package br.com.gestorfinanceiro;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Tasks;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

public final class DriveBackupWorker extends Worker {
    private static final String PREFS = "drive_backup";
    private static final String BACKUP_FILE_NAME = "gestor-financeiro-backup.json";
    private static final String DRIVE_FILES = "https://www.googleapis.com/drive/v3/files";
    private static final String DRIVE_UPLOAD = "https://www.googleapis.com/upload/drive/v3/files";
    private static final String[] TABLES = {"lancamentos", "categorias", "contas", "cartoes"};

    public DriveBackupWorker(@NonNull Context context, @NonNull WorkerParameters params) { super(context, params); }

    @NonNull @Override public Result doWork() {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean("oauth_connected", false)) return Result.success();
        prefs.edit().putString("status", "CHECKING").apply();
        try {
            String token = getAccessToken();
            if (token == null) {
                prefs.edit().putBoolean("oauth_connected", false).putString("status", "AUTH_REQUIRED").apply();
                return Result.failure();
            }
            try (MainActivity.LocalDb db = new MainActivity.LocalDb(getApplicationContext())) {
                String localRaw = db.read();
                String localHash = sha256(localRaw);
                String fileId = prefs.getString("file_id", "");
                if (fileId.isEmpty()) {
                    fileId = findBackupFile(token);
                    if (!fileId.isEmpty()) prefs.edit().putString("file_id", fileId).apply();
                }
                if (fileId.isEmpty()) return upload(token, "", localRaw, localHash, prefs);
                String remoteRaw;
                try {
                    remoteRaw = request(token, "GET", DRIVE_FILES + "/" + urlPath(fileId) + "?alt=media", null, null);
                } catch (DriveApiException error) {
                    if (error.statusCode == 401) {
                        prefs.edit().putBoolean("oauth_connected", false).putString("status", "AUTH_REQUIRED").apply();
                        return Result.failure();
                    }
                    if (error.statusCode != 404) throw error;
                    prefs.edit().remove("file_id").apply();
                    fileId = findBackupFile(token);
                    if (fileId.isEmpty()) return upload(token, "", localRaw, localHash, prefs);
                    prefs.edit().putString("file_id", fileId).apply();
                    remoteRaw = request(token, "GET", DRIVE_FILES + "/" + urlPath(fileId) + "?alt=media", null, null);
                }
                if (remoteRaw.trim().isEmpty()) return upload(token, fileId, localRaw, localHash, prefs);

                JSONObject remoteFile = new JSONObject(remoteRaw);
                String remotePayload;
                if (remoteFile.has("payload")) {
                    Object value = remoteFile.get("payload");
                    remotePayload = value instanceof String ? (String) value : ((JSONObject) value).toString();
                } else if ("gestor-financeiro-local-v1".equals(remoteFile.optString("format"))) {
                    JSONObject clean = new JSONObject();
                    for (String table : TABLES) clean.put(table, remoteFile.optJSONArray(table) == null ? new JSONArray() : remoteFile.getJSONArray(table));
                    remotePayload = clean.toString();
                } else throw new IllegalArgumentException("O arquivo do Drive não é um backup do Gestor Financeiro.");

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
                if ("phone".equals(resolution)) return upload(token, fileId, localRaw, localHash, prefs);
                if (localHash.equals(remoteHash)) return complete(prefs, localHash);
                if (baseHash.isEmpty()) {
                    if (isEmpty(localRaw)) {
                        if (!db.replaceIfCurrent(localRaw, remotePayload)) return retryChanged(prefs);
                        return complete(prefs, remoteHash);
                    }
                    if (isEmpty(remotePayload)) return upload(token, fileId, localRaw, localHash, prefs);
                    return conflict(prefs);
                }
                if (localHash.equals(baseHash)) {
                    if (!db.replaceIfCurrent(localRaw, remotePayload)) return retryChanged(prefs);
                    return complete(prefs, remoteHash);
                }
                if (remoteHash.equals(baseHash)) return upload(token, fileId, localRaw, localHash, prefs);
                return conflict(prefs);
            }
        } catch (DriveApiException error) {
            if (error.statusCode == 401) {
                prefs.edit().putBoolean("oauth_connected", false).putString("status", "AUTH_REQUIRED").apply();
                return Result.failure();
            }
            prefs.edit().putString("status", "ERROR").apply();
            return Result.retry();
        } catch (SecurityException error) {
            prefs.edit().putBoolean("oauth_connected", false).putString("status", "AUTH_REQUIRED").apply();
            return Result.failure();
        } catch (IllegalArgumentException error) {
            prefs.edit().putString("status", "ERROR_INVALID").apply();
            return Result.failure();
        } catch (Exception error) {
            prefs.edit().putString("status", "ERROR").apply();
            return Result.retry();
        }
    }

    private String getAccessToken() throws Exception {
        AuthorizationRequest request = AuthorizationRequest.builder()
            .setRequestedScopes(Collections.singletonList(new Scope(MainActivity.DRIVE_SCOPE))).build();
        AuthorizationResult result = Tasks.await(
            Identity.getAuthorizationClient(getApplicationContext()).authorize(request), 30, TimeUnit.SECONDS);
        if (result.hasResolution()) return null;
        return result.getAccessToken();
    }

    private String findBackupFile(String token) throws Exception {
        String query = "name='" + BACKUP_FILE_NAME + "' and trashed=false";
        String url = DRIVE_FILES + "?spaces=appDataFolder&pageSize=1&fields=files(id,name)&q=" + URLEncoder.encode(query, "UTF-8");
        JSONObject response = new JSONObject(request(token, "GET", url, null, null));
        JSONArray files = response.optJSONArray("files");
        return files == null || files.length() == 0 ? "" : files.getJSONObject(0).optString("id", "");
    }

    private Result upload(String token, String fileId, String payload, String hash, SharedPreferences prefs) throws Exception {
        JSONObject wrapper = new JSONObject();
        wrapper.put("format", "gestor-financeiro-drive-v1");
        wrapper.put("savedAt", System.currentTimeMillis());
        wrapper.put("sha256", hash);
        wrapper.put("payload", payload);
        String body = wrapper.toString();
        if (fileId.isEmpty()) {
            String boundary = "financeiro_" + Long.toHexString(System.currentTimeMillis());
            JSONObject metadata = new JSONObject();
            metadata.put("name", BACKUP_FILE_NAME);
            metadata.put("mimeType", "application/json");
            metadata.put("parents", new JSONArray().put("appDataFolder"));
            String multipart = "--" + boundary + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" + metadata +
                "\r\n--" + boundary + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" + body + "\r\n--" + boundary + "--";
            JSONObject created = new JSONObject(request(token, "POST", DRIVE_UPLOAD + "?uploadType=multipart&fields=id", "multipart/related; boundary=" + boundary, multipart));
            fileId = created.optString("id", "");
            if (fileId.isEmpty()) throw new IllegalStateException("O Drive não confirmou a criação do backup.");
            prefs.edit().putString("file_id", fileId).apply();
        } else {
            request(token, "PATCH", DRIVE_UPLOAD + "/" + urlPath(fileId) + "?uploadType=media", "application/json; charset=UTF-8", body);
        }
        return complete(prefs, hash);
    }

    private String request(String token, String method, String urlText, String contentType, String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        if (contentType != null) connection.setRequestProperty("Content-Type", contentType);
        if (body != null) {
            connection.setDoOutput(true);
            try (OutputStream out = connection.getOutputStream()) { out.write(body.getBytes(StandardCharsets.UTF_8)); }
        }
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
        String response = "";
        if (stream != null) try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            response = new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
        connection.disconnect();
        if (status < 200 || status >= 300) throw new DriveApiException(status, "Drive API HTTP " + status + ": " + response);
        return response;
    }

    private static final class DriveApiException extends IllegalStateException {
        final int statusCode;
        DriveApiException(int statusCode, String message) { super(message); this.statusCode = statusCode; }
    }

    private static String urlPath(String value) throws Exception { return java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20"); }
    private Result complete(SharedPreferences prefs, String hash) {
        prefs.edit().putString("base_hash", hash).remove("resolution").putString("status", "SYNCED").putLong("last_sync", System.currentTimeMillis()).apply();
        return Result.success();
    }
    private Result conflict(SharedPreferences prefs) { prefs.edit().remove("resolution").putString("status", "CONFLICT").apply(); return Result.success(); }
    private Result retryChanged(SharedPreferences prefs) { prefs.edit().putString("status", "PENDING").apply(); return Result.retry(); }
    private static boolean isEmpty(String raw) throws Exception {
        JSONObject data = new JSONObject(raw);
        for (String table : TABLES) { JSONArray rows = data.optJSONArray(table); if (rows != null && rows.length() > 0) return false; }
        return true;
    }
    private static String sha256(String text) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
        return hex.toString();
    }
}
