package br.com.gestorfinanceiro;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_AUDIO = 41;
    private static final int REQUEST_VOICE = 42;
    private static final String PREFS = "gestor_financeiro";
    private static final String SERVER_KEY = "server_url";
    private WebView webView;
    private String serverUrl;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(49, 94, 67));
        window.setNavigationBarColor(Color.rgb(31, 50, 39));
        serverUrl = getPreferences(MODE_PRIVATE).getString(SERVER_KEY, "");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(16), 0, dp(8), 0);
        toolbar.setBackgroundColor(Color.rgb(49, 94, 67));
        TextView title = new TextView(this);
        title.setText("Gestor Financeiro");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(null, 1);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, dp(56), 1));
        Button settings = new Button(this);
        settings.setText("Servidor");
        settings.setTextColor(Color.WHITE);
        settings.setBackgroundColor(Color.TRANSPARENT);
        settings.setOnClickListener(v -> showServerDialog());
        toolbar.addView(settings, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
        root.addView(toolbar);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.addJavascriptInterface(new VoiceBridge(), "AndroidVoice");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        root.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);

        if (serverUrl.isEmpty()) showServerDialog();
        else webView.loadUrl(serverUrl);
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
        else super.onBackPressed();
    }

    private final class VoiceBridge {
        @JavascriptInterface
        public void startSpeechRecognition() {
            runOnUiThread(MainActivity.this::startVoiceRecognition);
        }
    }
}
