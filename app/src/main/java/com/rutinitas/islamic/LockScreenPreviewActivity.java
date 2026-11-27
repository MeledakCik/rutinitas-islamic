package com.rutinitas.islamic;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** A small, read-only notification destination that stays above the Android keyguard. */
public final class LockScreenPreviewActivity extends Activity {
    static final String EXTRA_TITLE = "com.rutinitas.islamic.extra.LOCKSCREEN_TITLE";
    static final String EXTRA_TEXT = "com.rutinitas.islamic.extra.LOCKSCREEN_TEXT";

    private static final int BACKGROUND = Color.rgb(18, 19, 22);
    private static final int CARD = Color.rgb(32, 33, 38);
    private static final int BORDER = Color.rgb(61, 63, 70);
    private static final int PRIMARY = Color.rgb(242, 243, 247);
    private static final int SECONDARY = Color.rgb(177, 180, 190);
    private static final int ACCENT = Color.rgb(74, 111, 245);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureKeyguardDisplay();
        configureSystemBars();
        setContentView(createPreview());
    }

    private void configureKeyguardDisplay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
    }

    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(BACKGROUND);
        window.setNavigationBarColor(BACKGROUND);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;
        }
        window.getDecorView().setSystemUiVisibility(0);
    }

    private View createPreview() {
        FrameLayout backdrop = new FrameLayout(this);
        backdrop.setBackgroundColor(BACKGROUND);
        backdrop.setOnClickListener(view -> finish());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(dp(20), dp(24), dp(20), dp(24));
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        backdrop.setOnApplyWindowInsetsListener((view, insets) -> {
            scroll.setPadding(dp(20) + insets.getSystemWindowInsetLeft(),
                    dp(24) + insets.getSystemWindowInsetTop(),
                    dp(20) + insets.getSystemWindowInsetRight(),
                    dp(24) + insets.getSystemWindowInsetBottom());
            return insets;
        });

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(22), dp(22), dp(20));
        card.setBackground(roundedBackground(CARD, BORDER, dp(24), dp(1)));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(view -> { });

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);

        ImageView appIcon = new ImageView(this);
        appIcon.setImageResource(R.mipmap.ic_launcher);
        appIcon.setContentDescription(getString(R.string.app_name));
        header.addView(appIcon, new LinearLayout.LayoutParams(dp(36), dp(36)));

        TextView eyebrow = new TextView(this);
        eyebrow.setText(R.string.lockscreen_preview_eyebrow);
        eyebrow.setTextColor(SECONDARY);
        eyebrow.setTextSize(11);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        eyebrow.setLetterSpacing(0.06f);
        eyebrow.setMaxLines(2);
        eyebrow.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams eyebrowParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        eyebrowParams.leftMargin = dp(12);
        header.addView(eyebrow, eyebrowParams);
        card.addView(header);

        TextView title = new TextView(this);
        title.setText(safeExtra(EXTRA_TITLE, getString(R.string.app_name), 120));
        title.setTextColor(PRIMARY);
        title.setTextSize(21);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setLineSpacing(dp(2), 1f);
        title.setMaxLines(3);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(22);
        card.addView(title, titleParams);

        TextView body = new TextView(this);
        body.setText(safeExtra(EXTRA_TEXT, "Lihat target rutinitas hari ini.", 240));
        body.setTextColor(SECONDARY);
        body.setTextSize(16);
        body.setLineSpacing(dp(4), 1f);
        body.setMaxLines(5);
        body.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = dp(8);
        card.addView(body, bodyParams);

        TextView close = new TextView(this);
        close.setText(R.string.lockscreen_preview_close);
        close.setTextColor(Color.WHITE);
        close.setTextSize(14);
        close.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        close.setGravity(Gravity.CENTER);
        close.setMinHeight(dp(48));
        close.setPadding(dp(18), dp(10), dp(18), dp(10));
        close.setBackground(roundedBackground(ACCENT, ACCENT, dp(16), 0));
        close.setClickable(true);
        close.setFocusable(true);
        close.setOnClickListener(view -> finish());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        closeParams.topMargin = dp(22);
        card.addView(close, closeParams);

        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        scroll.addView(card, cardParams);
        backdrop.addView(scroll, scrollParams);
        return backdrop;
    }

    private String safeExtra(String key, String fallback, int maxLength) {
        String value = getIntent() == null ? null : getIntent().getStringExtra(key);
        if (TextUtils.isEmpty(value)) return fallback;
        value = value.trim();
        return value.length() <= maxLength ? value : value.substring(0, maxLength).trim() + "…";
    }

    private GradientDrawable roundedBackground(int fill, int stroke, int radius, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, stroke);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
