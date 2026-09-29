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
import android.widget.Toast;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.Scope;
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
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class MainActivity extends FragmentActivity {
    private static final int REQUEST_AUDIO = 41;
    private static final int REQUEST_VOICE = 42;
    private static final int REQUEST_GOOGLE_AUTH = 47;
    static final String DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.appdata";
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
        if (code == REQUEST_GOOGLE_AUTH) {
            if (result == RESULT_OK && intent != null) {
                try {
                    AuthorizationResult authorization = Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(intent);
                    finishDriveAuthorization(authorization);
                } catch (Exception e) {
                    driveSyncPrefs().edit().putString("status", "AUTH_REQUIRED").apply();
                    Toast.makeText(this, "Não foi possível concluir a autorização do Google. Toque no indicador para tentar novamente.", Toast.LENGTH_LONG).show();
                }
            } else {
                driveSyncPrefs().edit().putString("status", "AUTH_REQUIRED").apply();
            }
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
        @JavascriptInterface public boolean save(String json) { try { localDb.write(json); if (isDriveConnected()) enqueueDriveBackup(MainActivity.this, 2); return true; } catch (Exception e) { return false; } }
        @JavascriptInterface public String driveStatus() { return driveSyncPrefs().getString("status", isDriveConnected() ? "PENDING" : "DISCONNECTED"); }
        @JavascriptInterface public void checkDriveOnStart() {
            if (isDriveConnected()) {
                enqueueDriveBackup(MainActivity.this, 0);
                return;
            }
            android.content.SharedPreferences prefs = driveSyncPrefs();
            if (!prefs.getBoolean("first_oauth_prompt_shown", false)) {
                prefs.edit().putBoolean("first_oauth_prompt_shown", true).apply();
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Entre na sua conta Google para ativar o backup automático no Drive.", Toast.LENGTH_LONG).show();
                    if (webView != null) webView.postDelayed(MainActivity.this::requestDriveAuthorization, 500);
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
    private boolean isDriveConnected() { return driveSyncPrefs().getBoolean("oauth_connected", false); }
    private static void enqueueDriveBackup(android.content.Context context, long delaySeconds) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("drive_backup", MODE_PRIVATE);
        if (!prefs.getBoolean("oauth_connected", false)) return;
        prefs.edit().putString("status", "PENDING").apply();
        androidx.work.Constraints constraints = new androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DriveBackupWorker.class)
            .setConstraints(constraints).setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build();
        WorkManager.getInstance(context).enqueueUniqueWork("financeiro-drive-backup", ExistingWorkPolicy.APPEND_OR_REPLACE, request);
    }
    private void connectOrSyncDrive() {
        String status = driveSyncPrefs().getString("status", isDriveConnected() ? "PENDING" : "DISCONNECTED");
        if ("CONFLICT".equals(status)) {
            new AlertDialog.Builder(this).setTitle("Conflito entre as cópias")
                .setMessage("O telefone e o Google Drive foram alterados desde a última sincronização. Ambas as cópias foram preservadas. Qual deseja manter como principal?")
                .setPositiveButton("Restaurar do Drive", (d, w) -> resolveDriveConflict(true))
                .setNegativeButton("Manter telefone", (d, w) -> resolveDriveConflict(false)).show();
            return;
        }
        if (!isDriveConnected() || "AUTH_REQUIRED".equals(status)) {
            requestDriveAuthorization();
            return;
        }
        requestDriveAuthorization();
    }
    private void requestDriveAuthorization() {
        AuthorizationRequest request = AuthorizationRequest.builder()
            .setRequestedScopes(Collections.singletonList(new Scope(DRIVE_SCOPE))).build();
        Identity.getAuthorizationClient(this).authorize(request)
            .addOnSuccessListener(this::finishDriveAuthorization)
            .addOnFailureListener(error -> {
                driveSyncPrefs().edit().putString("status", "AUTH_REQUIRED").apply();
                Toast.makeText(this, "Não foi possível entrar no Google. Verifique a configuração de login do app e tente novamente.", Toast.LENGTH_LONG).show();
            });
    }
    private void finishDriveAuthorization(AuthorizationResult authorization) {
        if (authorization == null) return;
        if (authorization.hasResolution()) {
            try { startIntentSenderForResult(authorization.getPendingIntent().getIntentSender(), REQUEST_GOOGLE_AUTH, null, 0, 0, 0); }
            catch (Exception error) { driveSyncPrefs().edit().putString("status", "AUTH_REQUIRED").apply(); }
            return;
        }
        if (authorization.getAccessToken() == null || authorization.getAccessToken().isEmpty()) {
            driveSyncPrefs().edit().putString("status", "AUTH_REQUIRED").apply();
            Toast.makeText(this, "O Google não concedeu acesso ao Drive. Toque no indicador para tentar de novo.", Toast.LENGTH_LONG).show();
            return;
        }
        driveSyncPrefs().edit().putBoolean("oauth_connected", true).putString("status", "PENDING").apply();
        enqueueDriveBackup(this, 0);
        Toast.makeText(this, "Google Drive conectado. Criando/verificando o backup automático…", Toast.LENGTH_LONG).show();
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
