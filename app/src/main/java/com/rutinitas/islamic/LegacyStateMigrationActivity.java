package com.rutinitas.islamic;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import androidx.webkit.WebViewClientCompat;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.webkit.WebViewAssetLoader;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/** One-time, invisible bridge that reads existing WebView origin storage into native storage. */
public final class LegacyStateMigrationActivity extends Activity {
    private static final String LEGACY_URL = "https://appassets.androidplatform.net/assets/Rutinitas.html";
    private static final long MIGRATION_TIMEOUT_MS = 15000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private NativeStateStore stateStore;
    private WebView webView;
    private TextView status;
    private boolean completed;
    private final Runnable timeout = () -> finishMigration(false, true);

    @SuppressLint("SetJavaScriptEnabled")
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        stateStore = new NativeStateStore(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(241, 240, 236));
        android.widget.LinearLayout content = new android.widget.LinearLayout(this);
        content.setOrientation(android.widget.LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(28), dp(28), dp(28), dp(28));
        ProgressBar progress = new ProgressBar(this);
        content.addView(progress, new android.widget.LinearLayout.LayoutParams(dp(36), dp(36)));
        status = new TextView(this);
        status.setText(R.string.native_migration_status);
        status.setTextColor(Color.rgb(36, 35, 32));
        status.setTextSize(16);
        status.setGravity(Gravity.CENTER);
        android.widget.LinearLayout.LayoutParams statusParams = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(16);
        content.addView(status, statusParams);
        root.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        webView = new WebView(this);
        webView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        webView.setAlpha(0f);
        FrameLayout.LayoutParams hidden = new FrameLayout.LayoutParams(dp(1), dp(1), Gravity.TOP | Gravity.START);
        root.addView(webView, hidden);
        setContentView(root);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain("appassets.androidplatform.net")
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();
        webView.setWebViewClient(new WebViewClientCompat() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @SuppressWarnings("deprecation")
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                return loader.shouldInterceptRequest(android.net.Uri.parse(url));
            }

            @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                finishMigration(false, true);
                return true;
            }

            @Override public void onPageFinished(WebView view, String url) {
                if (LEGACY_URL.equals(url)) handler.postDelayed(LegacyStateMigrationActivity.this::readStoredState, 900L);
            }
        });
        handler.postDelayed(timeout, MIGRATION_TIMEOUT_MS);
        webView.loadUrl(LEGACY_URL);
    }

    private void readStoredState() {
        if (completed || webView == null) return;
        String script = "JSON.stringify({state:window.__ROUTINE_ENGINE__?window.__ROUTINE_ENGINE__.getState():null," +
                "raw:(function(){try{return localStorage.getItem('rutin_hifz_state_v10')}catch(e){return null}})()," +
                "quran:(function(){try{return localStorage.getItem('rutinitas_quran_reader_v1')}catch(e){return null}})()," +
                "notification:(function(){try{return localStorage.getItem('rutinitas_notification_surface')}catch(e){return null}})()," +
                "widget:(function(){try{return localStorage.getItem('rutinitas_widget_surface')}catch(e){return null}})()})";
        webView.evaluateJavascript(script, result -> acceptExtractedState(result));
    }

    private void acceptExtractedState(String result) {
        if (completed) return;
        try {
            Object decoded = new JSONTokener(result == null ? "null" : result).nextValue();
            if (!(decoded instanceof String)) throw new JSONException("Data lama tidak dapat dibaca.");
            JSONObject envelope = new JSONObject((String) decoded);
            JSONObject legacy = null;
            String raw = envelope.optString("raw", null);
            if (raw != null && !raw.isEmpty() && !"null".equals(raw)) legacy = new JSONObject(raw);
            if (legacy == null) legacy = envelope.optJSONObject("state");
            boolean hadStoredState = raw != null && !raw.isEmpty() && !"null".equals(raw);
            if (!hadStoredState && legacy != null) hadStoredState = legacy.optLong("updatedAt", 0) > 0;
            if (hadStoredState && legacy != null) {
                JSONObject imported = stateStore.importLegacyState(legacy);
                String quran = envelope.optString("quran", null);
                if (quran != null && !quran.isEmpty() && !"null".equals(quran)) {
                    try { imported.put("quranReader", new JSONObject(quran)); stateStore.save(imported); }
                    catch (JSONException ignored) { }
                }
                android.content.SharedPreferences.Editor surfaceEditor = getSharedPreferences("routine_surfaces", MODE_PRIVATE).edit();
                String notification = envelope.optString("notification", null);
                String widget = envelope.optString("widget", null);
                if (notification != null && !"null".equals(notification)) surfaceEditor.putBoolean("notification_enabled", Boolean.parseBoolean(notification));
                if (widget != null && !"null".equals(widget)) surfaceEditor.putBoolean("widget_enabled", Boolean.parseBoolean(widget));
                surfaceEditor.apply();
                finishMigration(true, false);
            } else {
                finishMigration(false, false);
            }
        } catch (Exception exception) {
            finishMigration(false, true);
        }
    }

    private void finishMigration(boolean migrated, boolean failed) {
        if (completed) return;
        completed = true;
        handler.removeCallbacks(timeout);
        android.content.Intent result = new android.content.Intent()
                .putExtra("migrated", migrated).putExtra("migrationFailed", failed);
        setResult(migrated || failed ? RESULT_OK : RESULT_CANCELED, result);
        finish();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
