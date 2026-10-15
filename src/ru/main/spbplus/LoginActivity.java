package ru.main.spbplus;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.concurrent.atomic.AtomicBoolean;

public class LoginActivity extends Activity {
    private static final String TAG = "DnevnikLogin";
    private final Api api = new Api(null);
    private SharedPreferences prefs;
    private final AtomicBoolean isFinished = new AtomicBoolean(false);

    private WebView web;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private Button btnRetry;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        prefs = getSharedPreferences("dnevnik", MODE_PRIVATE);

        // Проверяем: если открыто для повторного входа (токен протух), не авто-логинимся
        boolean forceRelogin = getIntent().getBooleanExtra("force_relogin", false);
        String savedToken = prefs.getString("token", null);

        if (!forceRelogin && savedToken != null && savedToken.startsWith("eyJ")) {
            openDiary();
            return;
        } else {
            prefs.edit().remove("token").apply();
        }

        FrameLayout root = new FrameLayout(this);

        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout loadingLayout = new LinearLayout(this);
        loadingLayout.setOrientation(LinearLayout.VERTICAL);
        loadingLayout.setGravity(android.view.Gravity.CENTER);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        loadingLayout.setPadding(pad, pad, pad, pad);

        progressBar = new ProgressBar(this);
        loadingLayout.addView(progressBar);

        tvStatus = new TextView(this);
        tvStatus.setText("Подключение к Госуслугам...");
        tvStatus.setTextSize(15);
        tvStatus.setGravity(android.view.Gravity.CENTER);
        tvStatus.setPadding(0, (int) (12 * getResources().getDisplayMetrics().density), 0, 0);
        loadingLayout.addView(tvStatus);

        btnRetry = new Button(this);
        btnRetry.setText("Повторить");
        btnRetry.setVisibility(View.GONE);
        btnRetry.setOnClickListener(v -> startLogin());
        loadingLayout.addView(btnRetry);

        root.addView(loadingLayout, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        setContentView(root);

        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        ws.setJavaScriptCanOpenWindowsAutomatically(true);

        String ua = ws.getUserAgentString().replace("; wv", "");
        ws.setUserAgentString(ua);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                // Прячем спиннер, как только пошёл ответ
                if (newProgress > 15) {
                    progressBar.setVisibility(View.GONE);
                    tvStatus.setVisibility(View.GONE);
                }
            }
        });

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleNavigation(view, request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleNavigation(view, url);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                Log.d(TAG, "Navigating: " + url);
                progressBar.setVisibility(View.GONE);
                tvStatus.setVisibility(View.GONE);
                checkTokenInUrl(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "Loaded: " + url);
                progressBar.setVisibility(View.GONE);
                tvStatus.setVisibility(View.GONE);

                checkCookies();

                if (url != null && url.contains("dnevnik2.petersburgedu.ru")) {
                    view.evaluateJavascript(
                        "(function() { return localStorage.getItem('token') || localStorage.getItem('jwt') || localStorage.getItem('X-JWT-Token'); })();",
                        val -> {
                            if (val != null && !val.equals("null") && val.length() > 20) {
                                String clean = val.replace("\"", "").trim();
                                if (clean.startsWith("eyJ")) {
                                    onTokenObtained(clean);
                                }
                            }
                        }
                    );
                }
            }

            @SuppressLint("WebViewClientOnReceivedSslError")
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                Log.e(TAG, "WebView error: " + error);
            }

            private boolean handleNavigation(WebView view, String url) {
                if (url == null) return false;
                checkTokenInUrl(url);
                // ВАЖНО: возвращаем false, чтобы WebView НЕ блокировал запрос к dnevnik2.petersburgedu.ru/api/journal/esa/login
                return false;
            }
        });

        startLogin();
    }

    private void checkTokenInUrl(String url) {
        if (url == null || isFinished.get()) return;

        try {
            Uri uri = Uri.parse(url);

            String jwt = uri.getQueryParameter("x-access-token");
            if (jwt != null && jwt.startsWith("eyJ")) {
                onTokenObtained(jwt);
                return;
            }

            String t = uri.getQueryParameter("token");
            if (t != null && t.startsWith("eyJ")) {
                onTokenObtained(t);
            }
        } catch (Exception ignored) {}
    }

    private void checkCookies() {
        if (isFinished.get()) return;
        String cookies = CookieManager.getInstance().getCookie("https://dnevnik2.petersburgedu.ru");
        if (cookies != null) {
            for (String cookie : cookies.split(";")) {
                String[] pair = cookie.trim().split("=");
                if (pair.length == 2) {
                    String name = pair[0].trim();
                    String val = pair[1].trim();
                    if ((name.equalsIgnoreCase("X-JWT-Token") || name.equalsIgnoreCase("token")) && val.startsWith("eyJ")) {
                        onTokenObtained(val);
                        return;
                    }
                }
            }
        }
    }

    private void startLogin() {
        isFinished.set(false);
        btnRetry.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText("Подключение к Госуслугам...");

        new Thread(() -> {
            try {
                String url = api.getEsiaLoginUrl();
                Log.d(TAG, "URL получен: " + url);
                runOnUiThread(() -> web.loadUrl(url));
            } catch (Exception e) {
                Log.e(TAG, "Ошибка получения ссылки", e);
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvStatus.setText("Ошибка сети: проверьте подключение.\n" + e.getMessage());
                    btnRetry.setVisibility(View.VISIBLE);
                });
            }
        }).start();
    }

    private void onTokenObtained(String token) {
        if (isFinished.compareAndSet(false, true)) {
            Log.d(TAG, "БОЕВОЙ JWT ПОЛУЧЕН: " + token.substring(0, Math.min(20, token.length())) + "...");
            prefs.edit().putString("token", token).apply();
            runOnUiThread(this::openDiary);
        }
    }

    private void openDiary() {
        Intent intent = new Intent(this, DiaryActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}