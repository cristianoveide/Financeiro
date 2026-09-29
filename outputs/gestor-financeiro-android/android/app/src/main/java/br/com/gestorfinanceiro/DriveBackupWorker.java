package br.com.gestorfinanceiro;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.json.JSONArray;
import org.json.JSONObject;

public final class DriveBackupWorker extends Worker {
    private static final String PREFS = "drive_backup";
    private static final String BACKUP_FILE_NAME = "gestor-financeiro-backup.json";
    private static final String[] TABLES = {"lancamentos", "categorias", "contas", "cartoes"};

    public DriveBackupWorker(@NonNull Context context, @NonNull WorkerParameters params) { super(context, params); }

    @NonNull @Override public Result doWork() {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String treeUriText = prefs.getString("tree_uri", "");
        String uriText = treeUriText.isEmpty() ? prefs.getString("uri", "") : treeUriText;
        if (uriText.isEmpty()) return Result.success();
        Uri uri = Uri.parse(uriText);
        prefs.edit().putString("status", "CHECKING").apply();
        try (MainActivity.LocalDb db = new MainActivity.LocalDb(getApplicationContext())) {
            if (!treeUriText.isEmpty()) {
                DocumentFile folder = DocumentFile.fromTreeUri(getApplicationContext(), uri);
                if (folder == null || !folder.canRead() || !folder.canWrite()) throw new SecurityException("A pasta do Drive não está mais acessível.");
                DocumentFile backup = folder.findFile(BACKUP_FILE_NAME);
                if (backup == null) backup = folder.createFile("application/json", BACKUP_FILE_NAME);
                if (backup == null) throw new IllegalStateException("Não foi possível criar o arquivo de backup na pasta do Drive.");
                uri = backup.getUri();
            }
            String localRaw = db.read();
            String localHash = sha256(localRaw);
            String remoteRaw = readRemote(uri);
            if (remoteRaw.trim().isEmpty()) return upload(uri, localRaw, localHash, prefs);

            JSONObject remoteFile = new JSONObject(remoteRaw);
            String remotePayload;
            if (remoteFile.has("payload")) {
                Object value = remoteFile.get("payload");
                remotePayload = value instanceof String ? (String) value : ((JSONObject) value).toString();
            } else if ("gestor-financeiro-local-v1".equals(remoteFile.optString("format"))) {
                JSONObject clean = new JSONObject();
                for (String table : TABLES) clean.put(table, remoteFile.optJSONArray(table) == null ? new JSONArray() : remoteFile.getJSONArray(table));
                remotePayload = clean.toString();
            } else throw new IllegalArgumentException("O arquivo selecionado não é um backup do Gestor Financeiro.");

            JSONObject remoteData = new JSONObject(remotePayload);
            for (String table : TABLES) if (!(remoteData.opt(table) instanceof JSONArray)) throw new IllegalArgumentException("O backup do Drive está incompleto.");
            String remoteHash = remoteFile.optString("sha256", sha256(remotePayload));
            if (!remoteHash.equals(sha256(remotePayload))) throw new IllegalArgumentException("O backup do Drive não passou na verificação de integridade.");
            String baseHash = prefs.getString("base_hash", "");
            String resolution = prefs.getString("resolution", "");

            if ("drive".equals(resolution)) {
                if (!db.replaceIfCurrent(localRaw, remotePayload)) return retryChanged(prefs);
                return complete(prefs, remoteHash);
            }
            if ("phone".equals(resolution)) return upload(uri, localRaw, localHash, prefs);

            if (localHash.equals(remoteHash)) return complete(prefs, localHash);
            if (baseHash.isEmpty()) {
                if (isEmpty(localRaw)) {
                    if (!db.replaceIfCurrent(localRaw, remotePayload)) return retryChanged(prefs);
                    return complete(prefs, remoteHash);
                }
                if (isEmpty(remotePayload)) return upload(uri, localRaw, localHash, prefs);
                return conflict(prefs);
            }
            if (localHash.equals(baseHash)) {
                if (!db.replaceIfCurrent(localRaw, remotePayload)) return retryChanged(prefs);
                return complete(prefs, remoteHash);
            }
            if (remoteHash.equals(baseHash)) return upload(uri, localRaw, localHash, prefs);
            return conflict(prefs);
        } catch (SecurityException error) {
            prefs.edit().putString("status", "ERROR_PERMISSION").apply();
            return Result.failure();
        } catch (IllegalArgumentException error) {
            prefs.edit().putString("status", "ERROR_INVALID").apply();
            return Result.failure();
        } catch (Exception error) {
            prefs.edit().putString("status", "ERROR").apply();
            return Result.retry();
        }
    }

    private String readRemote(Uri uri) throws Exception {
        try (InputStream in = getApplicationContext().getContentResolver().openInputStream(uri)) {
            if (in == null) return "";
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private Result upload(Uri uri, String payload, String hash, SharedPreferences prefs) throws Exception {
        JSONObject wrapper = new JSONObject();
        wrapper.put("format", "gestor-financeiro-drive-v1");
        wrapper.put("savedAt", System.currentTimeMillis());
        wrapper.put("sha256", hash);
        wrapper.put("payload", payload);
        try (OutputStream out = getApplicationContext().getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new IllegalStateException("O Drive não abriu o arquivo para gravação.");
            out.write(wrapper.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
        return complete(prefs, hash);
    }

    private Result complete(SharedPreferences prefs, String hash) {
        prefs.edit().putString("base_hash", hash).remove("resolution").putString("status", "SYNCED").putLong("last_sync", System.currentTimeMillis()).apply();
        return Result.success();
    }

    private Result conflict(SharedPreferences prefs) {
        prefs.edit().remove("resolution").putString("status", "CONFLICT").apply();
        return Result.success();
    }

    private Result retryChanged(SharedPreferences prefs) {
        prefs.edit().putString("status", "PENDING").apply();
        return Result.retry();
    }

    private static boolean isEmpty(String raw) throws Exception {
        JSONObject data = new JSONObject(raw);
        for (String table : TABLES) {
            JSONArray rows = data.optJSONArray(table);
            if (rows != null && rows.length() > 0) return false;
        }
        return true;
    }

    private static String sha256(String text) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
        return hex.toString();
    }
}
