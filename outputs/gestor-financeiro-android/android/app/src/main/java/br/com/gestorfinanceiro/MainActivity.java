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
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends FragmentActivity {
    private static final int REQUEST_AUDIO = 41;
    private static final int REQUEST_VOICE = 42;
    private static final int REQUEST_IMPORT = 43;
    private static final int REQUEST_EXPORT = 44;
    private WebView webView;
    private boolean authenticated, authenticationPromptVisible, textToSpeechReady;
    private TextToSpeech textToSpeech;
    private LocalDb localDb;
    private String exportContents;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getWindow().setStatusBarColor(Color.rgb(16, 42, 86));
        getWindow().setNavigationBarColor(Color.rgb(8, 19, 34));
        localDb = new LocalDb();
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
        if (result != RESULT_OK || intent == null) return;
        Uri uri = intent.getData();
        try {
            if (code == REQUEST_VOICE) {
                ArrayList<String> matches = intent.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (matches != null && !matches.isEmpty()) { sendJs("window.onNativeSpeechResult && window.onNativeSpeechResult(" + JSONObject.quote(matches.get(0)) + ")"); return; }
                sendJs("window.onNativeSpeechError && window.onNativeSpeechError()");
            } else if (code == REQUEST_IMPORT && uri != null) {
                StringBuilder contents = new StringBuilder();
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new IllegalStateException("Não foi possível abrir o arquivo.");
                    byte[] buffer = new byte[8192]; int n; while ((n = input.read(buffer)) != -1) contents.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
                }
                String name = uri.getLastPathSegment() == null ? "importacao.json" : uri.getLastPathSegment();
                sendJs("window.onNativeImportFile && window.onNativeImportFile(" + JSONObject.quote(name) + "," + JSONObject.quote(contents.toString()) + ")");
            } else if (code == REQUEST_EXPORT && uri != null && exportContents != null) {
                try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                    if (output == null) throw new IllegalStateException("Não foi possível criar o backup.");
                    output.write(exportContents.getBytes(StandardCharsets.UTF_8));
                }
                Toast.makeText(this, "Backup exportado.", Toast.LENGTH_LONG).show(); exportContents = null;
            }
        } catch (Exception e) { Toast.makeText(this, "Falha ao transferir arquivo: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void sendJs(String script) { runOnUiThread(() -> { if (webView != null) webView.evaluateJavascript(script, null); }); }
    @Override public void onBackPressed() { closeApp(); }
    private void closeApp() { finishAndRemoveTask(); android.os.Process.killProcess(android.os.Process.myPid()); }

    private final class LocalBridge {
        @JavascriptInterface public String load() { return localDb.read(); }
        @JavascriptInterface public boolean save(String json) { try { localDb.write(json); return true; } catch (Exception e) { return false; } }
        @JavascriptInterface public void openImport() {
            runOnUiThread(() -> { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("*/*"); i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/csv", "application/json", "text/plain"}); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i, REQUEST_IMPORT); });
        }
        @JavascriptInterface public void exportFile(String json) {
            runOnUiThread(() -> { exportContents = json; Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT); i.setType("application/json"); i.putExtra(Intent.EXTRA_TITLE, "gestor-financeiro-backup.json"); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i, REQUEST_EXPORT); });
        }
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
    private final class LocalDb extends SQLiteOpenHelper {
        LocalDb() { super(MainActivity.this, "gestor_financeiro.db", null, 1); }
        @Override public void onCreate(SQLiteDatabase db) { db.execSQL("CREATE TABLE state (id INTEGER PRIMARY KEY CHECK(id=1), payload TEXT NOT NULL)"); }
        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}
        synchronized String read() {
            try (Cursor c = getReadableDatabase().rawQuery("SELECT payload FROM state WHERE id=1", null)) {
                if (c.moveToFirst()) return c.getString(0);
            }
            return "{\"lancamentos\":[],\"categorias\":[],\"contas\":[],\"cartoes\":[]}";
        }
        synchronized void write(String json) {
            JSONObject data;
            try { data = new JSONObject(json); } catch (Exception e) { throw new IllegalArgumentException("Dados locais inválidos."); }
            for (String key : new String[]{"lancamentos", "categorias", "contas", "cartoes"}) if (!(data.opt(key) instanceof org.json.JSONArray)) throw new IllegalArgumentException("Estrutura local inválida.");
            ContentValues values = new ContentValues(); values.put("id", 1); values.put("payload", json);
            getWritableDatabase().insertWithOnConflict("state", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }
    @Override protected void onDestroy() {
        if (textToSpeech != null) { textToSpeech.stop(); textToSpeech.shutdown(); textToSpeech = null; }
        if (localDb != null) localDb.close(); super.onDestroy();
    }
}