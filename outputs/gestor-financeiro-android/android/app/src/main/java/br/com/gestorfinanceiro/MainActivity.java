package br.com.gestorfinanceiro;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;

import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import org.json.JSONObject;

import java.util.ArrayList;

public class MainActivity extends FragmentActivity {
    private static final int REQUEST_AUDIO = 41;
    private static final int REQUEST_VOICE = 42;
    private static final String SERVER_KEY = "server_url";
    private WebView webView;
    private String serverUrl;
    private boolean authenticated;
    private boolean authenticationPromptVisible;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(49, 94, 67));
        window.setNavigationBarColor(Color.rgb(31, 50, 39));
        serverUrl = getPreferences(MODE_PRIVATE).getString(SERVER_KEY, "");
        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.addJavascriptInterface(new VoiceBridge(), "AndroidVoice");
        webView.addJavascriptInterface(new ThemeBridge(), "AndroidTheme");
        webView.addJavascriptInterface(new SettingsBridge(), "AndroidSettings");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        setContentView(webView);

        authenticateToOpen();
    }

    private void authenticateToOpen() {
        if (authenticationPromptVisible || authenticated) return;
        authenticationPromptVisible = true;
        BiometricPrompt prompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        authenticationPromptVisible = false;
                        authenticated = true;
                        if (serverUrl.isEmpty()) showServerDialog();
                        else webView.loadUrl(serverUrl);
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        authenticationPromptVisible = false;
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Autenticação necessária")
                                .setMessage("Use a biometria ou o bloqueio de tela do aparelho para abrir o app.")
                                .setCancelable(false)
                                .setPositiveButton("Tentar novamente", (dialog, which) -> authenticateToOpen())
                                .setNegativeButton("Sair", (dialog, which) -> closeApp())
                                .show();
                    }
                });
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Desbloquear Gestor Financeiro")
                .setSubtitle("Confirme sua identidade para abrir o app")
                .setDeviceCredentialAllowed(true)
                .build();
        prompt.authenticate(promptInfo);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showServerDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("https://seu-servidor.com");
        input.setText(serverUrl);
        input.setPadding(dp(20), dp(12), dp(20), dp(12));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Conectar ao seu gestor")
                .setMessage("Informe o endereço HTTPS onde o Gestor Financeiro está publicado.")
                .setView(input)
                .setPositiveButton("Conectar", null)
                .setNegativeButton(serverUrl.isEmpty() ? "Cancelar" : "Fechar", null)
                .create();
        dialog.setOnShowListener(unused -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = input.getText().toString().trim().replaceAll("/+$", "");
            Uri uri = Uri.parse(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                input.setError("Use um endereço HTTPS válido");
                return;
            }
            serverUrl = value;
            getPreferences(MODE_PRIVATE).edit().putString(SERVER_KEY, serverUrl).apply();
            webView.loadUrl(serverUrl);
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void startVoiceRecognition() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO);
            return;
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Diga seu comando financeiro");
        try {
            startActivityForResult(intent, REQUEST_VOICE);
        } catch (Exception e) {
            sendJs("window.onNativeSpeechError && window.onNativeSpeechError() ");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_AUDIO && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            startVoiceRecognition();
        } else if (requestCode == REQUEST_AUDIO) {
            sendJs("window.onNativeSpeechError && window.onNativeSpeechError() ");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_VOICE) return;
        if (resultCode == RESULT_OK && data != null) {
            ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (matches != null && !matches.isEmpty()) {
                sendJs("window.onNativeSpeechResult && window.onNativeSpeechResult(" + JSONObject.quote(matches.get(0)) + ")");
                return;
            }
        }
        sendJs("window.onNativeSpeechError && window.onNativeSpeechError() ");
    }

    private void sendJs(String script) {
        runOnUiThread(() -> { if (webView != null) webView.evaluateJavascript(script, null); });
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else closeApp();
    }

    private void closeApp() {
        finishAndRemoveTask();
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    private final class VoiceBridge {
        @JavascriptInterface
        public void startSpeechRecognition() {
            runOnUiThread(MainActivity.this::startVoiceRecognition);
        }
    }

    private final class ThemeBridge {
        @JavascriptInterface
        public void setDark(boolean dark) {
            runOnUiThread(() -> {
                int top = dark ? Color.rgb(16, 42, 86) : Color.rgb(49, 94, 67);
                getWindow().setStatusBarColor(top);
                getWindow().setNavigationBarColor(dark ? Color.rgb(8, 19, 34) : Color.rgb(31, 50, 39));
                if (webView != null) webView.setBackgroundColor(dark ? Color.rgb(8, 19, 34) : Color.WHITE);
            });
        }
    }

    private final class SettingsBridge {
        @JavascriptInterface
        public void openServerSettings() {
            runOnUiThread(MainActivity.this::showServerDialog);
        }
    }
}
