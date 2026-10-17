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

        boolean forceRelogin = getIntent().getBooleanExtra("force_relogin", false);
        String savedToken = prefs.getString("token", null);

        // Если токен есть в памяти и не запрошен принудительный вход — сразу в оболочку
        if (!forceRelogin && savedToken != null && savedToken.startsWith("eyJ")) {
            openDiary();
            return;
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
                if (newProgress > 15) {
                    progressBar.setVisibility(View.GONE);
                    tvStatus.setVisibility(View.GONE);
                }
            }
        });

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (checkTokenAndOpen(url)) {
                    view.stopLoading();
                    return true;
                }
                return false;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (checkTokenAndOpen(url)) {
                    view.stopLoading();
                    return true;
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                Log.d(TAG, "Navigating: " + url);
                progressBar.setVisibility(View.GONE);
                tvStatus.setVisibility(View.GONE);

                if (checkTokenAndOpen(url)) {
                    view.stopLoading();
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "Loaded: " + url);
                progressBar.setVisibility(View.GONE);
                tvStatus.setVisibility(View.GONE);

                CookieManager.getInstance().flush();

                if (checkTokenAndOpen(url)) {
                    return;
                }

                // Дополнительная проверка на случай если токен лег в куки
                checkCookies();

                // Проверка LocalStorage
                if (url != null && url.contains("dnevnik2.petersburgedu.ru")) {
                    view.evaluateJavascript(
                        "(function() { return localStorage.getItem('token') || localStorage.getItem('jwt') || localStorage.getItem('X-JWT-Token'); })();",
                        val -> {
                            if (val != null && !val.equals("null") && val.length() > 30) {
                                String clean = val.replace("\"", "").trim();
                                if (clean.startsWith("eyJ")) {
                                    onTokenFound(clean);
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
        });

        startLogin();
    }

    // Мгновенный захват x-access-token из адреса редиректа
    private boolean checkTokenAndOpen(String url) {
        if (url == null || isFinished.get()) return false;

        try {
            Uri uri = Uri.parse(url);
            String jwt = uri.getQueryParameter("x-access-token");
            if (jwt != null && jwt.startsWith("eyJ")) {
                onTokenFound(jwt);
                return true;
            }
            String t = uri.getQueryParameter("token");
            if (t != null && t.startsWith("eyJ")) {
                onTokenFound(t);
                return true;
            }
        } catch (Exception ignored) {}

        // Резервный поиск по подстроке
        if (url.contains("x-access-token=eyJ")) {
            try {
                int start = url.indexOf("x-access-token=") + "x-access-token=".length();
                int end = url.indexOf("&", start);
                String jwt = (end == -1) ? url.substring(start) : url.substring(start, end);
                jwt = Uri.decode(jwt);
                if (jwt.startsWith("eyJ")) {
                    onTokenFound(jwt);
                    return true;
                }
            } catch (Exception ignored) {}
        }

        return false;
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
                        onTokenFound(val);
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
                    tvStatus.setText("Ошибка сети. Проверьте подключение.\n" + e.getMessage());
                    btnRetry.setVisibility(View.VISIBLE);
                });
            }
        }).start();
    }

    private void onTokenFound(String token) {
        if (isFinished.compareAndSet(false, true)) {
            Log.d(TAG, "✅ ТОКЕН ПЕРЕХВАЧЕН! ПЕРЕХОД В ОБОЛОЧКУ: " + token.substring(0, 20) + "...");
            CookieManager.getInstance().flush();
            prefs.edit()
                 .putString("token", token)
                 .putLong("token_time", System.currentTimeMillis())
                 .apply();

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