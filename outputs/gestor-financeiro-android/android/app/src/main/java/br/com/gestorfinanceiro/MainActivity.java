package br.com.gestorfinanceiro;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.Uri;
import android.widget.Toast;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MainActivity extends FragmentActivity {
    private static final int REQUEST_AUDIO = 41;
    private static final int REQUEST_VOICE = 42;
    private static final int REQUEST_DRIVE_TREE = 45;
    private static final int REQUEST_DRIVE_OPEN = 46;
    private WebView webView;
    private boolean authenticated, authenticationPromptVisible, textToSpeechReady;
    private TextToSpeech textToSpeech;
    private LocalDb localDb;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getWindow().setStatusBarColor(Color.rgb(16, 42, 86));
        getWindow().setNavigationBarColor(Color.rgb(8, 19, 34));
        localDb = new LocalDb(this);
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(8, 19, 34));
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowContentAccess(false);
        webView.addJavascriptInterface(new VoiceBridge(), "AndroidVoice");
        webView.addJavascriptInterface(new ThemeBridge(), "AndroidTheme");
        webView.addJavascriptInterface(new LocalBridge(), "LocalFinanceiro");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        setContentView(webView);
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS && textToSpeech != null) {
                int language = textToSpeech.setLanguage(new Locale("pt", "BR"));
                textToSpeechReady = language != TextToSpeech.LANG_MISSING_DATA && language != TextToSpeech.LANG_NOT_SUPPORTED;
                textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) {}
                    @Override public void onDone(String id) { if ("confirmation".equals(id)) sendJs("window.onNativeSpeechPromptFinished && window.onNativeSpeechPromptFinished()"); }
                    @Override public void onError(String id) { if ("confirmation".equals(id)) sendJs("window.onNativeSpeechPromptFinished && window.onNativeSpeechPromptFinished()"); }
                });
            }
        });
        authenticateToOpen();
    }

    private void authenticateToOpen() {
        if (authenticationPromptVisible || authenticated) return;
        authenticationPromptVisible = true;
        BiometricPrompt prompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(this), new BiometricPrompt.AuthenticationCallback() {
            @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                authenticationPromptVisible = false; authenticated = true;
                webView.loadUrl("file:///android_asset/www/index.html");
            }
            @Override public void onAuthenticationError(int code, CharSequence error) {
                authenticationPromptVisible = false;
                new AlertDialog.Builder(MainActivity.this).setTitle("Autenticação necessária")
                    .setMessage("Use a biometria ou o bloqueio de tela do aparelho para abrir o app.")
                    .setCancelable(false).setPositiveButton("Tentar novamente", (d, w) -> authenticateToOpen())
                    .setNegativeButton("Sair", (d, w) -> closeApp()).show();
            }
        });
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
            .setTitle("Desbloquear Gestor Financeiro").setSubtitle("Confirme sua identidade para abrir o app")
            .setDeviceCredentialAllowed(true).build();
        prompt.authenticate(info);
    }

    private void startVoiceRecognition() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO); return;
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR");
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Diga seu comando financeiro");
        try { startActivityForResult(intent, REQUEST_VOICE); }
        catch (Exception e) { sendJs("window.onNativeSpeechError && window.onNativeSpeechError()"); }
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == REQUEST_AUDIO && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startVoiceRecognition();
        else if (code == REQUEST_AUDIO) sendJs("window.onNativeSpeechError && window.onNativeSpeechError()");
    }

    @Override protected void onActivityResult(int code, int result, Intent intent) {
        super.onActivityResult(code, result, intent);
        if (code == REQUEST_DRIVE_TREE) {
            if (result == RESULT_OK && intent != null && intent.getData() != null) connectDriveFolder(intent.getData(), intent.getFlags());
            return;
        }
        if (code == REQUEST_DRIVE_OPEN) {
            if (result == RESULT_OK && intent != null && intent.getData() != null) connectDriveFile(intent.getData(), intent.getFlags());
            return;
        }
        if (result != RESULT_OK || intent == null) return;
        try {
            if (code == REQUEST_VOICE) {
                ArrayList<String> matches = intent.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (matches != null && !matches.isEmpty()) { sendJs("window.onNativeSpeechResult && window.onNativeSpeechResult(" + JSONObject.quote(matches.get(0)) + ")"); return; }
                sendJs("window.onNativeSpeechError && window.onNativeSpeechError()");
            }
        } catch (Exception e) { Toast.makeText(this, "Falha ao transferir arquivo: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void sendJs(String script) { runOnUiThread(() -> { if (webView != null) webView.evaluateJavascript(script, null); }); }
    @Override public void onBackPressed() { closeApp(); }
    private void closeApp() { finishAndRemoveTask(); android.os.Process.killProcess(android.os.Process.myPid()); }

    private final class LocalBridge {
        @JavascriptInterface public String load() { return localDb.read(); }
        @JavascriptInterface public boolean save(String json) { try { localDb.write(json); if (driveUri() != null) enqueueDriveBackup(MainActivity.this, 2); return true; } catch (Exception e) { return false; } }
        @JavascriptInterface public String driveStatus() { return driveSyncPrefs().getString("status", driveUri() == null ? "DISCONNECTED" : "PENDING"); }
        @JavascriptInterface public void checkDriveOnStart() {
            if (driveUri() != null) {
                enqueueDriveBackup(MainActivity.this, 0);
                return;
            }
            android.content.SharedPreferences prefs = driveSyncPrefs();
            if (!prefs.getBoolean("first_drive_prompt_shown", false)) {
                prefs.edit().putBoolean("first_drive_prompt_shown", true).apply();
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Entre na sua conta Google para conectar o Drive e ativar o backup automático.", Toast.LENGTH_LONG).show();
                    if (webView != null) webView.postDelayed(MainActivity.this::pickDriveFolder, 500);
                });
            }
        }
        @JavascriptInterface public void openDriveSettings() { runOnUiThread(MainActivity.this::connectOrSyncDrive); }
        @JavascriptInterface public void resolveDriveConflict(boolean useDriveCopy) {
            driveSyncPrefs().edit().putString("resolution", useDriveCopy ? "drive" : "phone").putString("status", "PENDING").apply();
            enqueueDriveBackup(MainActivity.this, 0);
        }
    }
    private android.content.SharedPreferences driveSyncPrefs() { return getSharedPreferences("drive_backup", MODE_PRIVATE); }
    private Uri driveUri() {
        String saved = driveSyncPrefs().getString("tree_uri", driveSyncPrefs().getString("uri", ""));
        return saved.isEmpty() ? null : Uri.parse(saved);
    }
    private static void enqueueDriveBackup(android.content.Context context, long delaySeconds) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("drive_backup", MODE_PRIVATE);
        if (prefs.getString("tree_uri", prefs.getString("uri", "")).isEmpty()) return;
        prefs.edit().putString("status", "PENDING").apply();
        androidx.work.Constraints constraints = new androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DriveBackupWorker.class)
            .setConstraints(constraints).setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build();
        WorkManager.getInstance(context).enqueueUniqueWork("financeiro-drive-backup", ExistingWorkPolicy.APPEND_OR_REPLACE, request);
    }
    private void connectOrSyncDrive() {
        String status = driveSyncPrefs().getString("status", driveUri() == null ? "DISCONNECTED" : "PENDING");
        if ("CONFLICT".equals(status)) {
            new AlertDialog.Builder(this).setTitle("Conflito entre as cópias")
                .setMessage("O telefone e o Google Drive foram alterados desde a última sincronização. Ambas as cópias foram preservadas. Qual deseja manter como principal?")
                .setPositiveButton("Restaurar do Drive", (d, w) -> resolveDriveConflict(true))
                .setNegativeButton("Manter telefone", (d, w) -> resolveDriveConflict(false)).show();
            return;
        }
        if (driveUri() == null) {
            Toast.makeText(this, "Entre na conta Google no seletor do Drive e escolha a pasta para o backup automático.", Toast.LENGTH_LONG).show();
            pickDriveFolder();
            return;
        }
        if ("ERROR_PERMISSION".equals(status)) {
            if (!driveSyncPrefs().getString("tree_uri", "").isEmpty()) pickDriveFolder();
            else pickDriveFile();
            return;
        }
        Toast.makeText(this, "Drive conectado. A sincronização acontece automaticamente.", Toast.LENGTH_SHORT).show();
    }
    private void pickDriveFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(intent, REQUEST_DRIVE_TREE); }
        catch (Exception e) { Toast.makeText(this, "Não encontrei o seletor de documentos. Instale/ative o Google Drive e tente novamente.", Toast.LENGTH_LONG).show(); }
    }
    private void pickDriveFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/json"); intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_DRIVE_OPEN);
    }
    private void connectDriveFolder(Uri uri, int resultFlags) {
        int flags = resultFlags & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if ((flags & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0 || (flags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == 0) {
            Toast.makeText(this, "Escolha uma pasta do Drive com acesso de leitura e gravação.", Toast.LENGTH_LONG).show(); return;
        }
        try {
            getContentResolver().takePersistableUriPermission(uri, flags);
            driveSyncPrefs().edit().putString("tree_uri", uri.toString()).remove("uri").remove("base_hash").remove("resolution").putString("status", "PENDING").apply();
            enqueueDriveBackup(this, 0);
            Toast.makeText(this, "Google Drive conectado. Criando/verificando o backup…", Toast.LENGTH_LONG).show();
        } catch (Exception e) { Toast.makeText(this, "Não consegui manter o acesso a essa pasta do Drive. Escolha outra pasta.", Toast.LENGTH_LONG).show(); }
    }
    private void connectDriveFile(Uri uri, int resultFlags) {
        int flags = resultFlags & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if ((flags & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0 || (flags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == 0) {
            Toast.makeText(this, "Escolha um arquivo JSON com permissão de leitura e gravação.", Toast.LENGTH_LONG).show(); return;
        }
        try {
            getContentResolver().takePersistableUriPermission(uri, flags);
            driveSyncPrefs().edit().putString("uri", uri.toString()).remove("tree_uri").remove("base_hash").remove("resolution").putString("status", "PENDING").apply();
            enqueueDriveBackup(this, 0);
            Toast.makeText(this, "Drive conectado. Verificando o backup…", Toast.LENGTH_LONG).show();
        } catch (Exception e) { Toast.makeText(this, "Não consegui manter o acesso a este arquivo. Escolha outro arquivo do Drive.", Toast.LENGTH_LONG).show(); }
    }
    private void resolveDriveConflict(boolean useDriveCopy) {
        driveSyncPrefs().edit().putString("resolution", useDriveCopy ? "drive" : "phone").putString("status", "PENDING").apply();
        enqueueDriveBackup(this, 0);
    }
    private final class VoiceBridge {
        @JavascriptInterface public void startSpeechRecognition() { runOnUiThread(MainActivity.this::startVoiceRecognition); }
        @JavascriptInterface public boolean speak(String text) {
            if (!textToSpeechReady || textToSpeech == null) return false;
            return textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "confirmation") != TextToSpeech.ERROR;
        }
    }
    private final class ThemeBridge {
        @JavascriptInterface public void setDark(boolean dark) { runOnUiThread(() -> {
            getWindow().setStatusBarColor(Color.rgb(16, 42, 86)); getWindow().setNavigationBarColor(Color.rgb(8, 19, 34));
            if (webView != null) webView.setBackgroundColor(Color.rgb(8, 19, 34));
        }); }
    }
    static final class LocalDb extends SQLiteOpenHelper {
        LocalDb(android.content.Context context) { super(context, "gestor_financeiro.db", null, 1); }
        @Override public void onCreate(SQLiteDatabase db) { db.execSQL("CREATE TABLE state (id INTEGER PRIMARY KEY CHECK(id=1), payload TEXT NOT NULL)"); }
        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}
        String read() {
            synchronized (LocalDb.class) {
                try (Cursor c = getReadableDatabase().rawQuery("SELECT payload FROM state WHERE id=1", null)) {
                    if (c.moveToFirst()) return c.getString(0);
                }
                return "{\"lancamentos\":[],\"categorias\":[],\"contas\":[],\"cartoes\":[]}";
            }
        }
        boolean replaceIfCurrent(String expected, String replacement) {
            synchronized (LocalDb.class) {
                if (!read().equals(expected)) return false;
                write(replacement);
                return true;
            }
        }
        void write(String json) {
            synchronized (LocalDb.class) {
                JSONObject data;
                try { data = new JSONObject(json); } catch (Exception e) { throw new IllegalArgumentException("Dados locais inválidos."); }
                for (String key : new String[]{"lancamentos", "categorias", "contas", "cartoes"}) if (!(data.opt(key) instanceof org.json.JSONArray)) throw new IllegalArgumentException("Estrutura local inválida.");
                ContentValues values = new ContentValues(); values.put("id", 1); values.put("payload", json);
                getWritableDatabase().insertWithOnConflict("state", null, values, SQLiteDatabase.CONFLICT_REPLACE);
            }
        }
    }
    @Override protected void onDestroy() {
        if (textToSpeech != null) { textToSpeech.stop(); textToSpeech.shutdown(); textToSpeech = null; }
        if (localDb != null) localDb.close(); super.onDestroy();
    }
}
