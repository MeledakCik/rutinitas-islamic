package com.rutinitas.islamic;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.Criteria;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.InputType;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.animation.LayoutTransition;
import android.app.Dialog;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** First fully native activity; all visible controls are Android Views. */
public final class NativeMainActivity extends Activity {
    private static final String SURFACES_PREFS = "routine_surfaces";
    private static final String SPACING_GAP_TAG = "rutinitas_spacing_gap";
    private static final String NOTIFICATION_CHANNEL = "active_routine_v2";
    private static final int REQUEST_IMPORT = 5201;
    private static final int REQUEST_EXPORT = 5202;
    private static final int REQUEST_LOCATION = 5203;
    private static final int REQUEST_LEGACY_MIGRATION = 5204;
    private static final int NOTIFICATION_ID = 2401;
    private static final int REQUEST_ADZAN_NOTIFICATION = 5206;
    private static final int REQUEST_ADZAN_FILE = 5207;
    private static final int REQUEST_ADZAN_FILE_SUBUH = 5208;
    private static final long HIFZ_STEP_SECONDS = 30 * 60L;
    private static final Pattern QURAN_SEARCH_SEPARATORS = Pattern.compile("[^\\p{L}\\p{N}]");
    private static final String[] NAV_KEYS = {"home", "schedule", "hifz", "prayer", "quran", "system"};
    private static final String[] NAV_LABELS = {"Beranda", "Jadwal", "Hafalan", "Shalat", "Qur’an", "Setelan"};
    private static final int[] NAV_ICON_RESOURCES = {
            R.drawable.ic_dock_home, R.drawable.ic_dock_schedule, R.drawable.ic_dock_hifz,
            R.drawable.ic_dock_prayer, R.drawable.ic_dock_quran, R.drawable.ic_dock_settings
    };
    private static final String[] PRAYER_KEYS = {"subuh", "dzuhur", "ashar", "maghrib", "isya"};
    private static final String[] PRAYER_LABELS = {"Subuh", "Dzuhur", "Ashar", "Maghrib", "Isya"};
    private static final String[] PRAYER_METHOD_KEYS = {"kemenag", "mwl", "isna", "makkah", "egypt"};
    private static final String[] PRAYER_METHOD_LABELS = {"Kemenag RI", "Muslim World League", "ISNA", "Makkah", "Mesir"};
    private static final String[] SABAQ_MODE_KEYS = {"quarter_page", "half_page", "one_page", "multi_page", "ayah"};
    private static final String[] SABAQ_MODE_LABELS = {"¼ halaman", "½ halaman", "1 halaman", "Beberapa halaman", "Ayat"};
    private static final int[] JUZ_START_PAGE = {1, 22, 42, 62, 82, 102, 121, 142, 162, 182, 201, 222, 242, 262, 282, 302, 322, 342, 362, 382, 402, 422, 442, 462, 482, 502, 522, 542, 562, 582};
    private static final int[] JUZ_END_PAGE = {21, 41, 61, 81, 101, 120, 141, 161, 181, 200, 221, 241, 261, 281, 301, 321, 341, 361, 381, 401, 421, 441, 461, 481, 501, 521, 541, 561, 581, 604};
    private static final int[] JUZ_START_SURAH = {1, 2, 2, 3, 4, 4, 5, 6, 7, 8, 9, 11, 12, 15, 17, 18, 21, 23, 25, 27, 29, 33, 36, 39, 41, 46, 51, 58, 67, 78};
    private static final int[] JUZ_START_AYAH = {1, 142, 253, 93, 24, 148, 82, 111, 88, 41, 93, 6, 53, 1, 1, 75, 1, 1, 21, 56, 46, 31, 28, 32, 47, 1, 31, 1, 1, 1};
    private static final int[] JUZ_END_SURAH = {2, 2, 3, 4, 4, 5, 6, 7, 8, 9, 11, 12, 14, 16, 18, 20, 22, 25, 27, 29, 33, 36, 39, 41, 45, 51, 57, 66, 77, 114};
    private static final int[] JUZ_END_AYAH = {141, 252, 92, 23, 147, 81, 110, 87, 40, 92, 5, 52, 52, 128, 74, 135, 78, 118, 55, 45, 30, 27, 31, 46, 37, 30, 29, 12, 50, 6};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Integer, String> surahNames = new HashMap<>();
    private final Map<Integer, String> surahArabicNames = new HashMap<>();
    private final Map<Integer, String> surahSearchAliases = new HashMap<>();
    private final Map<Integer, String> surahRevelationTypes = new HashMap<>();
    private final Map<Integer, List<String>> surahAyahs = new HashMap<>();
    private NativeStateStore stateStore;
    private JSONObject state;
    private LinearLayout root;
    private LinearLayout body;
    private LinearLayout nav;
    private ScrollView pageScroll;
    private FrameLayout stage;
    private View dockBackdrop;
    private android.window.OnBackInvokedCallback backCallback;
    private final int[] tabScroll = new int[NAV_KEYS.length];
    private final LinearLayout[] navIcons = new LinearLayout[NAV_KEYS.length];
    private final ImageView[] navGlyphs = new ImageView[NAV_KEYS.length];
    private final TextView[] navCaptions = new TextView[NAV_KEYS.length];
    private int prayerLineMinute = -1;
    private Typeface regularFont;
    private Typeface semiboldFont;
    private TextView clockView;
    private TextView prayerLineView;
    private TextView timerView;
    private TextView timerLabelView;
    private final java.util.Set<String> openSections = new java.util.HashSet<>();
    private String currentTab = "home";
    private boolean showManzilList;
    private int lastCheckedDay = -1;
    private String pendingExportName;
    private String pendingExportJson;
    private boolean darkTheme;
    private int quranSurah = 1;
    private int quranAyah = 1;
    private int quranFontSize = 30;
    private boolean quranReading;
    private int quranIndexLimit = 24;
    private int quranIndexScroll;
    private String quranSearch = "";
    private boolean showIndexAudio;
    private int audioSurah = 1;
    private NativeQuranAudio quranAudio;
    private NativeQuranTranslation quranTranslation;
    private NativeQuranTiming quranTiming;
    private Typeface quranFont;
    private final Map<Integer, View> ayahBlocks = new HashMap<>();
    private final Map<Integer, TextView> ayahBadges = new HashMap<>();
    private View basmalaBlock;
    private int followAyah = -1;
    private int pendingSeekMs = -1;
    private TextView nextPrayerCountdown;
    private long lastUserReaderInteraction;
    private final Runnable followTick = new Runnable() {
        @Override public void run() {
            followRecitation();
            handler.postDelayed(this, 250L);
        }
    };
    private TextView audioTitle;
    private TextView audioDetail;
    private Button audioPlay;
    private Button audioDownload;
    private SeekBar audioSeek;
    private TextView audioTime;
    private boolean seekingAudio;
    private boolean appInitialized;
    private boolean locationSearchInProgress;
    private LocationListener activeLocationListener;
    private CancellationSignal locationCancellationSignal;
    private ToneGenerator timerToneGenerator;
    private final Runnable locationSearchTimeout = () -> finishLocationSearch(null);

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            Calendar now = Calendar.getInstance();
            refreshDailyProgress(now);
            int minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
            if (minute != prayerLineMinute) {
                prayerLineMinute = minute;
                if (clockView != null) clockView.setText(String.format(Locale.ROOT, "%02d:%02d",
                        now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE)));
                if (prayerLineView != null) prayerLineView.setText(buildPrayerLine());
                updateNextPrayerCountdown();
            }
            String expired = expireTimerIfNeeded();
            if (currentTab.equals(expired)) renderTab(currentTab);
            refreshTimerText();
            updateAudioTimeline();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            showManzilList = savedInstanceState.getBoolean("showManzilList");
            quranReading = savedInstanceState.getBoolean("quranReading");
            audioSurah = savedInstanceState.getInt("audioSurah", 1);
            showIndexAudio = savedInstanceState.getBoolean("showIndexAudio");
            String restoredTab = savedInstanceState.getString("currentTab", "home");
            for (String key : NAV_KEYS) {
                if (key.equals(restoredTab)) {
                    currentTab = restoredTab;
                    break;
                }
            }
        }
        stateStore = new NativeStateStore(this);
        state = stateStore.load();
        if (!stateStore.hasStoredState() && !getSharedPreferences("routine_native_migration", MODE_PRIVATE)
                .getBoolean("checked", false)) {
            getSharedPreferences("routine_native_migration", MODE_PRIVATE).edit()
                    .putBoolean("checked", true).apply();
            startActivityForResult(new Intent(this, LegacyStateMigrationActivity.class), REQUEST_LEGACY_MIGRATION);
            return;
        }
        initializeNativeApp();
    }

    private void initializeNativeApp() {
        state = stateStore.load();
        appInitialized = true;
        applyPreferredTheme();
        restoreQuranReaderState();
        loadQuranAssets();
        repairSabqiHistory();
        if (quranAudio == null) quranAudio = new NativeQuranAudio(this, new NativeQuranAudio.Listener() {
            @Override public void audioChanged() {
                if (pendingSeekMs >= 0 && quranAudio.matches(quranReciter(), audioSurah) && quranAudio.duration() > 0) {
                    quranAudio.seek(pendingSeekMs);
                    pendingSeekMs = -1;
                }
                updateQuranAudioControls();
            }
            @Override public void audioMessage(String message) { toast(message); }
            @Override public void audioCompleted(int reciter, int surah) {
                if ("quran".equals(currentTab) && surfacePrefs().getBoolean("quran_auto_next", false) && surah < 114) {
                    audioSurah = surah + 1;
                    quranAudio.play(reciter, audioSurah);
                    if (quranReading && surah == quranSurah && surfacePrefs().getBoolean("quran_follow", true)) {
                        openQuranChapter(audioSurah, 1);
                    }
                }
            }
        });
        if (quranTranslation == null) quranTranslation = new NativeQuranTranslation(this);
        if (quranTiming == null) quranTiming = new NativeQuranTiming(this);
        ensureNotificationChannel();
        PrayerReminders.ensureChannel(this);
        PrayerReminders.schedule(this);
        if (Build.VERSION.SDK_INT >= 33 && PrayerReminders.enabled(this)
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && !surfacePrefs().getBoolean("adzan_permission_asked", false)) {
            surfacePrefs().edit().putBoolean("adzan_permission_asked", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_ADZAN_NOTIFICATION);
        }
        applyLaunchTab(getIntent());
        renderApp();
        handler.removeCallbacks(clockTick);
        handler.post(clockTick);
    }

    private void restoreQuranReaderState() {
        quranSurah = 1;
        quranAyah = 1;
        quranFontSize = 30;
        JSONObject reader = state.optJSONObject("quranReader");
        if (reader != null) {
            JSONObject lastRead = reader.optJSONObject("lastRead");
            if (lastRead != null) {
                quranSurah = Math.max(1, Math.min(114, lastRead.optInt("surah", 1)));
                quranAyah = Math.max(1, lastRead.optInt("ayah", 1));
            }
            quranFontSize = Math.max(24, Math.min(42, reader.optInt("fontSize", 30)));
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (!appInitialized) return;
        refreshDailyProgress(Calendar.getInstance());
        syncTimerScreenAwake();
        publishCurrentNotification();
        updateWidget();
        PrayerReminders.schedule(this);
        handler.removeCallbacks(followTick);
        handler.post(followTick);
        if ("prayer".equals(currentTab)) renderTab("prayer");
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (appInitialized) applyLaunchTab(intent);
    }

    private void applyLaunchTab(Intent intent) {
        String tab = intent == null ? null : intent.getStringExtra("open_tab");
        if (tab == null) return;
        intent.removeExtra("open_tab");
        for (String key : NAV_KEYS) if (key.equals(tab)) { renderTab(tab); return; }
    }

    @Override protected void onPause() {
        handler.removeCallbacks(followTick);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        persistState();
        super.onPause();
    }

    @Override protected void onStop() {
        if (quranAudio != null) quranAudio.pause();
        super.onStop();
    }

    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if (appInitialized && pageScroll != null) rebuildAppearance();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putString("currentTab", currentTab);
        outState.putBoolean("showManzilList", showManzilList);
        outState.putBoolean("quranReading", quranReading);
        outState.putInt("audioSurah", audioSurah);
        outState.putBoolean("showIndexAudio", showIndexAudio);
        super.onSaveInstanceState(outState);
    }

    private void refreshDailyProgress(Calendar now) {
        int day = now.get(Calendar.YEAR) * 400 + now.get(Calendar.DAY_OF_YEAR);
        if (day == lastCheckedDay) return;
        lastCheckedDay = day;
        if (!NativeStateStore.rolloverIfNewDay(state)) return;
        persistState();
        publishCurrentNotification();
        syncTimerScreenAwake();
        renderTab(currentTab);
    }

    @Override @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ADZAN_FILE || requestCode == REQUEST_ADZAN_FILE_SUBUH) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Uri chosen = data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(chosen, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    PrayerReminders.prefs(this).edit().putString(requestCode == REQUEST_ADZAN_FILE_SUBUH
                            ? "adzan_uri_subuh" : "adzan_uri", chosen.toString()).apply();
                    toast("Suara adzan disimpan: " + audioName(chosen.toString()));
                } catch (SecurityException denied) {
                    toast("File ini tidak bisa dipakai. Pilih file audio dari penyimpanan HP.");
                }
            }
            if ("prayer".equals(currentTab)) renderTab("prayer");
            return;
        }
        if (requestCode == REQUEST_LEGACY_MIGRATION) {
            initializeNativeApp();
            boolean migrated = data != null && data.getBooleanExtra("migrated", false);
            getSharedPreferences("routine_native_migration", MODE_PRIVATE).edit()
                    .putBoolean("show_data_import", !migrated).apply();
            if (migrated) {
                toast("Data versi sebelumnya berhasil dipindahkan.");
            } else if (data != null && data.getBooleanExtra("migrationFailed", false)) {
                toast("Pemindahan otomatis gagal. Kamu masih bisa impor cadangan JSON v10.");
            }
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQUEST_IMPORT) {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IllegalStateException("File tidak dapat dibaca.");
                StringBuilder json = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                    char[] buffer = new char[8192];
                    int count;
                    while ((count = reader.read(buffer)) > 0) {
                        json.append(buffer, 0, count);
                        if (json.length() > 5 * 1024 * 1024) throw new IllegalStateException("File cadangan terlalu besar (maks. 5 MB).");
                    }
                }
                state = stateStore.importBackup(new JSONObject(json.toString()));
                restoreQuranReaderState();
                repairSabqiHistory();
                getSharedPreferences("routine_native_migration", MODE_PRIVATE).edit()
                        .putBoolean("show_data_import", false).apply();
                if (applyPreferredTheme()) recreate();
                else renderTab(currentTab);
                toast("Cadangan v10 berhasil dipindahkan.");
            } catch (Exception exception) {
                toast(exception.getMessage() == null ? "Cadangan tidak valid." : exception.getMessage());
            }
        } else if (requestCode == REQUEST_EXPORT && pendingExportJson != null) {
            try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                if (output == null) throw new IllegalStateException("File tidak dapat ditulis.");
                output.write(pendingExportJson.getBytes(StandardCharsets.UTF_8));
                toast("Cadangan JSON tersimpan.");
            } catch (Exception exception) { toast("Gagal menyimpan cadangan."); }
            pendingExportJson = null;
        }
    }

    @Override @SuppressWarnings("deprecation")
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            requestCurrentLocation();
        } else if (requestCode == 5205 && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            publishCurrentNotification();
        } else if (requestCode == 5205 && grantResults.length > 0) {
            surfacePrefs().edit().putBoolean("notification_enabled", false).apply();
            toast("Izin notifikasi belum diberikan.");
        } else if (requestCode == REQUEST_ADZAN_NOTIFICATION) {
            PrayerReminders.schedule(this);
            if ("prayer".equals(currentTab)) renderTab("prayer");
        }
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (quranAudio != null) quranAudio.close();
        if (quranTranslation != null) quranTranslation.close();
        locationSearchInProgress = false;
        if (locationCancellationSignal != null) {
            locationCancellationSignal.cancel();
            locationCancellationSignal = null;
        }
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (locationManager != null && activeLocationListener != null) {
            try { locationManager.removeUpdates(activeLocationListener); } catch (SecurityException ignored) { }
            activeLocationListener = null;
        }
        if (timerToneGenerator != null) {
            timerToneGenerator.release();
            timerToneGenerator = null;
        }
        super.onDestroy();
    }

    private void renderApp() {
        applySystemBars();
        stage = new FrameLayout(this);
        stage.setBackground(stageBackground());
        if (Build.VERSION.SDK_INT >= 29) stage.setForceDarkAllowed(false);
        int width = getResources().getConfiguration().screenWidthDp;
        int railWidth = width >= 740 ? dp(680) : MATCH;
        FrameLayout frame = new FrameLayout(this);
        stage.addView(frame, new FrameLayout.LayoutParams(railWidth, MATCH, Gravity.CENTER));
        frame.setPadding(dp(16), dp(10), dp(16), dp(8));
        setContentView(stage);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        frame.addView(root, new FrameLayout.LayoutParams(MATCH, MATCH));
        root.addView(buildHeader(), new LinearLayout.LayoutParams(MATCH, WRAP));

        pageScroll = scrollView();
        pageScroll.setFillViewport(true);
        pageScroll.setClipToPadding(false);
        pageScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        pageScroll.setPadding(0, dp(16), 0, dp(96));
        pageScroll.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_MOVE) markReaderInteraction();
            return false;
        });
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        pageScroll.addView(body, new ScrollView.LayoutParams(MATCH, WRAP));
        root.addView(pageScroll, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        FrameLayout dock = new FrameLayout(this);
        dock.setBackground(rounded(Color.TRANSPARENT, colorBorder(), dp(NAV_RADIUS_DP), dp(1)));
        dock.setClipToOutline(true);
        dock.setElevation(dp(10));
        FrameLayout.LayoutParams dockParams = new FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM);
        dockParams.bottomMargin = dp(6);
        frame.addView(dock, dockParams);
        String surface = surfaceStyle();
        dockBackdrop = null;
        if (Build.VERSION.SDK_INT >= 31 && ("blur".equals(surface) || "glass".equals(surface))) {
            View backdrop = new NativeGlassBackdrop(pageScroll, dp(12));
            dockBackdrop = backdrop;
            dock.addView(backdrop, new FrameLayout.LayoutParams(MATCH, 0));
            pageScroll.setOnScrollChangeListener((view, x, y, oldX, oldY) -> backdrop.invalidate());
        }
        nav = buildBottomNavigation();
        dock.addView(nav, new FrameLayout.LayoutParams(MATCH, WRAP));
        final View backdrop = dockBackdrop;
        if (backdrop != null) nav.addOnLayoutChangeListener((bar, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int height = bottom - top;
            if (backdrop.getLayoutParams().height == height) return;
            backdrop.setLayoutParams(new FrameLayout.LayoutParams(MATCH, height));
        });
        renderTab(currentTab);
    }

    private void rebuildAppearance() {
        int scroll = pageScroll == null ? 0 : pageScroll.getScrollY();
        applyPreferredTheme();
        renderApp();
        pageScroll.post(() -> pageScroll.scrollTo(0, scroll));
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(12));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        icon.setContentDescription(getString(R.string.app_name));
        header.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(10), 0, 0, 0);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(0, WRAP, 1f);
        header.addView(labels, labelsParams);
        TextView name = text("RUTINITAS", 11, colorPrimary(), true);
        name.setLetterSpacing(0.13f);
        labels.addView(name);
        prayerLineView = text(buildPrayerLine(), 12, colorMuted(), false);
        labels.addView(prayerLineView);

        Calendar now = Calendar.getInstance();
        clockView = text(String.format(Locale.ROOT, "%02d:%02d", now.get(Calendar.HOUR_OF_DAY),
                now.get(Calendar.MINUTE)), 16, colorPrimary(), true);
        clockView.setTypeface(monoTypeface(), Typeface.BOLD);
        header.addView(clockView, new LinearLayout.LayoutParams(WRAP, WRAP));
        View divider = new View(this);
        divider.setBackgroundColor(colorBorder());
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(MATCH, dp(1));
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(header);
        container.addView(divider, dividerParams);
        return container;
    }

    private static final int NAV_RADIUS_DP = 30;

    private LinearLayout buildBottomNavigation() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(6), dp(6), dp(6), dp(6));
        bar.setBackground(navigationSurface());
        LayoutTransition motion = new LayoutTransition();
        motion.enableTransitionType(LayoutTransition.CHANGING);
        motion.setDuration(reducedMotion() ? 0 : 220);
        bar.setLayoutTransition(motion);
        for (int i = 0; i < NAV_KEYS.length; i++) {
            final String key = NAV_KEYS[i];
            final String label = NAV_LABELS[i];
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER);
            item.setMinimumHeight(dp(48));
            item.setPadding(dp(12), 0, dp(12), 0);
            item.setFocusable(true);
            ImageView icon = new ImageView(this);
            icon.setImageResource(NAV_ICON_RESOURCES[i]);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            item.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));
            navIcons[i] = item;
            navGlyphs[i] = icon;
            TextView caption = text(label, 13, colorOnAccent(), true);
            caption.setSingleLine(true);
            caption.setPadding(dp(8), 0, 0, 0);
            caption.setVisibility(View.GONE);
            caption.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            item.addView(caption, new LinearLayout.LayoutParams(WRAP, WRAP));
            item.setContentDescription(label);
            navCaptions[i] = caption;
            item.setOnClickListener(view -> { if (!key.equals(currentTab)) renderTab(key); });
            pressScale(item);
            bar.addView(item, new LinearLayout.LayoutParams(0, dp(48), 1f));
        }
        return bar;
    }

    private boolean reducedMotion() {
        try {
            return Settings.Global.getFloat(getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        } catch (Exception ignored) { return false; }
    }

    private void renderTab(String tab) {
        String previous = currentTab;
        currentTab = tab;
        if (body == null || pageScroll == null || nav == null) return;
        boolean switched = !tab.equals(previous);
        body.animate().cancel();
        body.setAlpha(1f);
        body.setTranslationY(0f);
        if (switched) {
            tabScroll[indexOf(NAV_KEYS, previous)] = pageScroll.getScrollY();
            if ("quran".equals(previous) && quranAudio != null) quranAudio.pause();
        }
        timerView = null;
        timerLabelView = null;
        nextPrayerCountdown = null;
        audioTitle = null;
        body.removeAllViews();
        updateNavigation();
        updateBackNavigation();
        switch (tab) {
            case "schedule": renderSchedule(); break;
            case "hifz": renderHifz(); break;
            case "prayer": renderPrayer(); break;
            case "system": renderSystem(); break;
            case "quran": renderQuran(); break;
            default: renderHome(); break;
        }
        if (switched) {
            int restoredScroll = tabScroll[indexOf(NAV_KEYS, tab)];
            pageScroll.scrollTo(0, 0);
            pageScroll.post(() -> { if (tab.equals(currentTab)) pageScroll.scrollTo(0, restoredScroll); });
            animateEntry(body);
        }
        prayerLineView.setText(buildPrayerLine());
        if (dockBackdrop != null) dockBackdrop.invalidate();
    }

    private void updateNavigation() {
        for (int i = 0; i < NAV_KEYS.length; i++) {
            boolean selected = NAV_KEYS[i].equals(currentTab);
            View item = nav.getChildAt(i);
            item.setSelected(selected);
            item.setBackground(selected
                    ? rippleBackground(colorAccent(), colorAccent(), dp(24), 0)
                    : rippleShape(dp(24)));
            item.setPadding(selected ? dp(14) : 0, 0, selected ? dp(16) : 0, 0);
            navGlyphs[i].setColorFilter(selected ? colorOnAccent() : colorMuted());
            navCaptions[i].setVisibility(selected ? View.VISIBLE : View.GONE);
            item.setLayoutParams(selected
                    ? new LinearLayout.LayoutParams(WRAP, dp(48), 0f)
                    : new LinearLayout.LayoutParams(0, dp(48), 1f));
        }
    }

    private void animateEntry(View view) {
        view.animate().cancel();
        view.setAlpha(1f);
        view.setTranslationY(0f);
        if (!surfacePrefs().getBoolean("motion_enabled", true)
                || (Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled())) return;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            int count = Math.min(group.getChildCount(), 8);
            for (int i = 0; i < count; i++) {
                View child = group.getChildAt(i);
                child.animate().cancel();
                child.setAlpha(0f);
                child.setTranslationY(dp(12));
                child.animate().alpha(1f).translationY(0f).setStartDelay(i * 28L).setDuration(260)
                        .setInterpolator(new DecelerateInterpolator(1.6f)).start();
            }
            return;
        }
        view.setAlpha(.9f);
        view.setTranslationY(dp(5));
        view.animate().alpha(1f).translationY(0f).setDuration(220)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private void renderHome() {
        boolean showMigrationCard = getSharedPreferences("routine_native_migration", MODE_PRIVATE)
                .getBoolean("show_data_import", !stateStore.hasStoredState());
        boolean hifzSessionComplete = todayString().equals(state.optString("completedDate", ""))
                && contains(state.optJSONArray("completedBlocksToday"), "primary_hifz");
        if (showMigrationCard) {
            LinearLayout importCard = card();
            importCard.addView(text("Punya data dari versi lama?", 15, colorPrimary(), true));
            importCard.addView(secondaryButton("Pindahkan data", R.drawable.ic_upload, this::showMigrationOptions));
            body.addView(importCard);
            addGap(body, 12);
        }

        JSONObject hifzPlan = getNativeHifzPlan();
        int stepCount = Math.max(1, hifzPlan == null ? 3 : hifzPlan.optInt("totalSteps", 3));
        int step = Math.max(0, Math.min(stepCount - 1, state.optInt("primarySubStep", 0)));

        LinearLayout focus = card();
        LinearLayout focusHeader = new LinearLayout(this);
        focusHeader.setGravity(Gravity.CENTER_VERTICAL);
        focusHeader.addView(text("Hafalan Subuh", 13, colorAccentText(), true), new LinearLayout.LayoutParams(0, WRAP, 1f));
        focusHeader.addView(text(hifzSessionComplete ? "Selesai"
                : "Langkah " + (step + 1) + "/" + stepCount, 12, colorMuted(), true));
        focus.addView(focusHeader);

        LinearLayout steps = new LinearLayout(this);
        for (int i = 0; i < stepCount; i++) {
            View segment = new View(this);
            int segmentColor = hifzSessionComplete || i <= step ? colorAccent() : colorBorder();
            segment.setBackground(rounded(segmentColor, segmentColor, dp(2), 0));
            LinearLayout.LayoutParams segmentParams = new LinearLayout.LayoutParams(0, dp(4), 1f);
            if (i > 0) segmentParams.leftMargin = dp(4);
            steps.addView(segment, segmentParams);
        }
        focus.addView(steps);

        if (hifzSessionComplete) {
            focus.addView(text("Hafalan hari ini tuntas", 20, colorPrimary(), true));
            focus.addView(text("Progres tersimpan. Sesi berikutnya dimulai besok.", 13, colorSecondary(), false));
            body.addView(focus);
            addGap(body, 12);
            renderDailyProgress();
            return;
        }

        focus.addView(text(hifzStepLabel(hifzPlan, step), 12, colorMuted(), true));
        TextView target = text(currentHifzTarget(), 18, colorPrimary(), true);
        target.setLineSpacing(dp(3), 1f);
        focus.addView(target);
        focus.addView(text(stepCue(step), 13, colorSecondary(), false));

        LinearLayout timerRow = new LinearLayout(this);
        timerRow.setGravity(Gravity.CENTER_VERTICAL);
        timerRow.setPadding(dp(14), dp(10), dp(14), dp(10));
        timerRow.setBackground(rounded(colorSubtle(), colorSubtle(), dp(12), 0));
        timerLabelView = text(timerIsRunning() ? "SESI BERJALAN" : "WAKTU LANGKAH", 11, colorMuted(), true);
        timerRow.addView(timerLabelView, new LinearLayout.LayoutParams(0, WRAP, 1f));
        timerView = text(formatSeconds(timerRemaining()), 30, colorPrimary(), true);
        timerView.setTypeface(monoTypeface(), Typeface.BOLD);
        timerRow.addView(timerView);
        focus.addView(timerRow);

        String timerAction = timerIsRunning() ? "Jeda"
                : (timerHasRecord() && timerRemaining() > 0 ? "Lanjutkan" : "Mulai");
        LinearLayout actions = new LinearLayout(this);
        actions.addView(actionButton(timerAction,
                timerIsRunning() ? R.drawable.ic_pause : R.drawable.ic_play, () -> {
            if (timerIsRunning()) pauseTimer(); else startOrResumeTimer();
            renderTab("home");
        }), new LinearLayout.LayoutParams(0, WRAP, 1f));
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(0, WRAP, 1f);
        doneParams.leftMargin = dp(8);
        actions.addView(secondaryButton(step < stepCount - 1 ? "Langkah selesai" : "Selesaikan sesi",
                R.drawable.ic_check, () -> {
            completeCurrentStep();
            renderTab("home");
        }), doneParams);
        focus.addView(actions);
        body.addView(focus);
        addGap(body, 12);
        renderDailyProgress();
    }

    private void renderDailyProgress() {
        JSONArray blocks;
        try {
            blocks = NativeScheduleEngine.build(state, getActivePrayerTimes(), new Date(), false).optJSONArray("blocks");
        } catch (JSONException ignored) { return; }
        if (blocks == null) return;
        int total = 0;
        int completed = 0;
        for (int i = 0; i < blocks.length(); i++) {
            JSONObject block = blocks.optJSONObject(i);
            if (block == null || "sleep_block".equals(block.optString("id")) || block.optBoolean("isDeferred")) continue;
            total++;
            if (block.optBoolean("isCompleted")) completed++;
        }
        if (total == 0) return;
        LinearLayout progress = card();
        progress.setClickable(true);
        progress.setFocusable(true);
        progress.setForeground(rippleShape(dp(16)));
        progress.setOnClickListener(view -> renderTab("schedule"));
        progress.setContentDescription("Rutinitas hari ini, " + completed + " dari " + total
                + " selesai. Ketuk untuk membuka jadwal.");
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text("Rutinitas hari ini", 15, colorPrimary(), true));
        TextView date = text(new java.text.SimpleDateFormat("EEEE, d MMMM", new Locale("id", "ID")).format(new Date()),
                12, colorMuted(), false);
        date.setPadding(0, dp(3), 0, 0);
        labels.addView(date);
        heading.addView(labels, new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView count = text(completed + "/" + total, 18, colorAccentText(), true);
        count.setTypeface(monoTypeface(), Typeface.BOLD);
        heading.addView(count);
        progress.addView(heading);
        android.widget.ProgressBar meter = new android.widget.ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        meter.setMax(total);
        meter.setProgress(completed);
        meter.setProgressTintList(ColorStateList.valueOf(colorAccent()));
        meter.setProgressBackgroundTintList(ColorStateList.valueOf(colorBorder()));
        meter.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        progress.addView(meter, new LinearLayout.LayoutParams(MATCH, dp(6)));
        body.addView(progress);
    }

    private void renderSchedule() {
        renderPageHeading("Jadwal", "Agenda hari ini");
        renderDailyAgenda();
        addGap(body, 12);
        renderGateActions();
        addGap(body, 12);
        JSONArray routines = state.optJSONArray("customRoutines");
        int routineCount = routines == null ? 0 : routines.length();
        String activeProfile = state.optString("activeRoutineProfileId", "profile_1").replace("profile_", "Profil ");
        body.addView(collapsible("schedule_routines", "Kelola aktivitas",
                activeProfile + " · " + routineCount + " aktivitas tambahan", panel -> {
            addProfilePicker(panel);
            if (routineCount == 0) {
                panel.addView(text("Belum ada aktivitas tambahan di profil ini.", 13, colorSecondary(), false));
            } else {
                for (int i = 0; i < routines.length(); i++) {
                    JSONObject routine = routines.optJSONObject(i);
                    if (routine != null) addRoutineRow(panel, routine);
                }
            }
            addGap(panel, 4);
            panel.addView(actionButton("Tambah aktivitas", R.drawable.ic_add, () -> showRoutineEditor(null)));
        }));
    }

    private void addRoutineRow(LinearLayout parent, JSONObject routine) {
        View divider = new View(this);
        divider.setBackgroundColor(colorBorder());
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(MATCH, dp(1));
        dividerParams.topMargin = dp(10);
        parent.addView(divider, dividerParams);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, 0);
        boolean enabled = routine.optBoolean("enabled", true);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setClickable(true);
        labels.setBackground(rippleShape(dp(10)));
        labels.setOnClickListener(view -> showRoutineEditor(routine));
        labels.setContentDescription("Ubah " + routine.optString("title", "Aktivitas"));
        labels.addView(text(routine.optString("title", "Aktivitas"), 15, enabled ? colorPrimary() : colorMuted(), true));
        TextView anchor = text(routineAnchorLabel(routine) + (enabled ? "" : " · dijeda"), 12, colorMuted(), false);
        anchor.setPadding(0, dp(3), 0, 0);
        labels.addView(anchor);
        row.addView(labels, new LinearLayout.LayoutParams(0, WRAP, 1f));
        Switch toggle = new Switch(this);
        toggle.setChecked(enabled);
        styleSwitch(toggle);
        toggle.setContentDescription(routine.optString("title", "Aktivitas") + " aktif");
        toggle.setOnCheckedChangeListener((button, checked) -> toggleRoutine(routine));
        row.addView(toggle, new LinearLayout.LayoutParams(WRAP, WRAP));
        Button remove = smallButton("Hapus");
        remove.setOnClickListener(view -> confirmDeleteRoutine(routine));
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(WRAP, WRAP);
        removeParams.leftMargin = dp(6);
        row.addView(remove, removeParams);
        parent.addView(row);
    }

    private void renderDailyAgenda() {
        LinearLayout agenda = card();
        addKicker(agenda, new java.text.SimpleDateFormat("EEEE, d MMM", new Locale("id", "ID"))
                .format(new Date()).toUpperCase(new Locale("id", "ID")));
        JSONObject schedule;
        try {
            schedule = NativeScheduleEngine.build(state, getActivePrayerTimes(), new Date(), false);
        } catch (JSONException exception) {
            agenda.addView(text("Jadwal belum dapat dihitung. Periksa data aktivitas dan waktu shalat.",
                    13, colorSecondary(), false));
            body.addView(agenda);
            return;
        }
        JSONArray blocks = schedule.optJSONArray("blocks");
        if (blocks == null || blocks.length() == 0) {
            agenda.addView(text("Belum ada blok jadwal untuk hari ini.", 13, colorSecondary(), false));
        } else {
            for (int i = 0; i < blocks.length(); i++) {
                JSONObject block = blocks.optJSONObject(i);
                if (block == null) continue;
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.TOP);
                TextView time = text(block.optString("start", "--:--"), 13, colorAccentText(), true);
                time.setTypeface(monoTypeface(), Typeface.BOLD);
                row.addView(time, new LinearLayout.LayoutParams(dp(54), WRAP));
                LinearLayout details = new LinearLayout(this);
                details.setOrientation(LinearLayout.VERTICAL);
                boolean done = block.optBoolean("isCompleted", false);
                String title = block.optString("title", "Aktivitas");
                if (done) title = "✓ " + title;
                details.addView(text(title, 14, done ? colorMuted() : colorPrimary(), true));
                addGap(details, 3);
                String secondary = "s.d. " + block.optString("end", "") + " · "
                        + Math.max(0, block.optInt("effectiveDurationMins", block.optInt("durationMins", 0))) + " mnt";
                if (block.optInt("shiftMins", 0) > 0) secondary += " · geser " + block.optInt("shiftMins") + " mnt";
                if (block.optInt("compressedFromMins", 0) > 0) secondary += " · dipadatkan";
                details.addView(text(secondary, 12, colorMuted(), false));
                if (!done && block.optString("protocol", "").trim().length() > 0) {
                    addGap(details, 3);
                    TextView protocol = text(block.optString("protocol"), 12, colorSecondary(), false);
                    protocol.setMaxLines(2);
                    protocol.setEllipsize(TextUtils.TruncateAt.END);
                    details.addView(protocol);
                }
                row.addView(details, new LinearLayout.LayoutParams(0, WRAP, 1f));
                agenda.addView(row);
                String blockId = block.optString("id", "");
                if ("primary_hifz".equals(blockId)) {
                    Button openHifz = smallButton("Buka di Beranda");
                    LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(WRAP, WRAP);
                    actionParams.leftMargin = dp(54);
                    openHifz.setOnClickListener(view -> renderTab("home"));
                    agenda.addView(openHifz, actionParams);
                } else if (!"presleep_gate".equals(blockId) && !"sleep_block".equals(blockId)) {
                    LinearLayout actions = new LinearLayout(this);
                    actions.setGravity(Gravity.CENTER_VERTICAL);
                    actions.setPadding(dp(54), dp(5), 0, 0);
                    if (block.optBoolean("isCompleted", false)) {
                        Button undo = smallButton("Batalkan");
                        undo.setOnClickListener(view -> undoAgendaBlock(blockId));
                        actions.addView(undo);
                    } else {
                        boolean hasTimer = hasAgendaTimer(blockId);
                        boolean running = agendaTimerRunning(blockId);
                        String policy = block.optString("conflictPolicy", "strict");
                        if ("shift".equals(policy) || "hifz".equals(block.optString("layer"))) {
                            Button timerAction = smallButton(running ? "Jeda" : (hasTimer ? "Lanjutkan" : "Mulai"));
                            timerAction.setOnClickListener(view -> {
                                if (running) pauseAgendaTimer(blockId); else startAgendaTimer(block);
                                renderTab("schedule");
                            });
                            actions.addView(timerAction);
                            if (hasTimer) {
                                TextView remaining = text(formatSeconds(agendaTimerRemaining(blockId)), 12, colorAccentText(), true);
                                remaining.setTypeface(monoTypeface(), Typeface.BOLD);
                                remaining.setTag("agenda_timer_" + blockId);
                                LinearLayout.LayoutParams timerParams = new LinearLayout.LayoutParams(WRAP, WRAP);
                                timerParams.leftMargin = dp(10);
                                actions.addView(remaining, timerParams);
                            }
                        }
                        Button complete = smallButton("Selesai");
                        LinearLayout.LayoutParams completeParams = new LinearLayout.LayoutParams(WRAP, WRAP);
                        completeParams.leftMargin = dp(7);
                        complete.setOnClickListener(view -> completeAgendaBlock(blockId));
                        actions.addView(complete, completeParams);
                    }
                    agenda.addView(actions);
                }
                if (i < blocks.length() - 1) {
                    View divider = new View(this);
                    divider.setBackgroundColor(colorBorder());
                    LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(MATCH, dp(1));
                    dividerParams.topMargin = dp(8);
                    dividerParams.bottomMargin = dp(8);
                    agenda.addView(divider, dividerParams);
                }
            }
        }
        JSONArray conflicts = schedule.optJSONArray("conflicts");
        if (conflicts != null && conflicts.length() > 0) {
            addGap(agenda, 8);
            agenda.addView(text("PERLU DICEK", 11, colorAccentText(), true));
            for (int i = 0; i < conflicts.length(); i++) {
                JSONObject conflict = conflicts.optJSONObject(i);
                if (conflict != null) agenda.addView(text("• " + conflict.optString("message"), 12, colorSecondary(), false));
            }
        }
        JSONArray skipped = schedule.optJSONArray("skippedBlocks");
        if (skipped != null && skipped.length() > 0) {
            for (int i = 0; i < skipped.length(); i++) {
                JSONObject item = skipped.optJSONObject(i);
                if (item != null) agenda.addView(text("Lewati " + item.optString("title") + " ("
                        + item.optString("reason") + ")", 12, colorMuted(), false));
            }
        }
        JSONArray deferred = schedule.optJSONArray("deferredBlocks");
        if (deferred != null && deferred.length() > 0) {
            for (int i = 0; i < deferred.length(); i++) {
                JSONObject item = deferred.optJSONObject(i);
                if (item != null) agenda.addView(text("Tertunda " + item.optString("title") + " · "
                        + item.optString("start") + "–" + item.optString("end"), 12, colorAccentText(), false));
            }
        }
        body.addView(agenda);
    }

    private void renderGateActions() {
        LinearLayout gate = card();
        addKicker(gate, "EVALUASI MALAM · 21:15–22:00");
        String result = state.optString("gateResultToday", "");
        if ("passed".equals(result)) {
            gate.addView(text("Evaluasi lulus · target hari berikutnya sudah disiapkan.", 14, colorPrimary(), true));
        } else if ("sabaq_frozen".equals(result)) {
            gate.addView(text("Sabaq dijeda untuk hari berikutnya. Manzil dan Sabqi tetap berjalan.", 14, colorPrimary(), true));
        } else if ("failed".equals(result)) {
            gate.addView(text("Evaluasi belum lulus. Target Sabaq tidak dimajukan.", 14, colorPrimary(), true));
        } else {
            gate.addView(text("Tentukan apakah target Sabaq maju besok.", 13, colorSecondary(), false));
        }
        JSONArray undo = state.optJSONArray("undoStack");
        JSONObject latestUndo = undo == null || undo.length() == 0 ? null : undo.optJSONObject(undo.length() - 1);
        if (isUndoableNativeAction(latestUndo)) {
            addGap(gate, 8);
            gate.addView(secondaryButton("Urungkan perubahan terakhir", this::undoGateDecision));
        }
        Calendar now = Calendar.getInstance();
        int minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        if (!"passed".equals(result) && !"sabaq_frozen".equals(result) && !"failed".equals(result)) {
            if (minutes >= 21 * 60 + 15 && minutes < 22 * 60) {
                addGap(gate, 8);
                gate.addView(text("Pilih hasil evaluasi:", 12, colorMuted(), true));
                addGap(gate, 4);
                LinearLayout choices = new LinearLayout(this);
                choices.setOrientation(LinearLayout.VERTICAL);
                choices.addView(actionButton("Lulus · lanjutkan target", R.drawable.ic_check, () -> applyGateDecision("passed")));
                addGap(choices, 6);
                choices.addView(secondaryButton("Jeda Sabaq · lanjutkan Manzil", () -> applyGateDecision("sabaq_frozen")));
                addGap(choices, 6);
                choices.addView(secondaryButton("Belum lulus", () -> applyGateDecision("failed")));
                gate.addView(choices);
            } else {
                addGap(gate, 4);
                gate.addView(text("Tersedia mulai pukul 21:15.", 12, colorMuted(), false));
            }
        }
        body.addView(gate);
    }

    private void applyGateDecision(String decision) {
        try {
            JSONObject action = new JSONObject()
                    .put("type", "native_gate")
                    .put("prevPhase", state.optInt("hifzPhase", 3))
                    .put("prevDay", state.optInt("phaseDay", 1))
                    .put("prevSubStep", state.optInt("primarySubStep", 0))
                    .put("prevSabaq", copyJson(state.optJSONObject("sabaq")))
                    .put("prevHistory", copyJson(state.optJSONArray("sabqiHistory")))
                    .put("prevConservedSpans", copyJson(state.optJSONObject("conservedPartialSpans")))
                    .put("prevFrozen", state.optBoolean("sabaqFrozen", false))
                    .put("prevFrozenForNextDay", state.optBoolean("sabaqFrozenForNextDay", false))
                    .put("prevGateResult", state.opt("gateResultToday") == null ? JSONObject.NULL : state.opt("gateResultToday"))
                    .put("prevGateAdvancedDate", state.opt("gateAdvancedDate") == null ? JSONObject.NULL : state.opt("gateAdvancedDate"))
                    .put("prevHvNextJuz", state.opt("hvNextJuz") == null ? JSONObject.NULL : state.opt("hvNextJuz"))
                    .put("prevLvManzilPage", state.opt("lvManzilPage") == null ? JSONObject.NULL : state.opt("lvManzilPage"))
                    .put("prevCompleted", copyJson(state.optJSONArray("completedBlocksToday")));
            JSONArray undo = state.optJSONArray("undoStack");
            if (undo == null) undo = new JSONArray();
            undo.put(action);
            while (undo.length() > 30) undo = removeFirst(undo);
            state.put("undoStack", undo);

            JSONArray completed = state.optJSONArray("completedBlocksToday");
            if (completed == null) completed = new JSONArray();
            if (!contains(completed, "presleep_gate")) completed.put("presleep_gate");
            state.put("completedBlocksToday", completed);
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
            state.put("gateResultToday", decision);
            if (!"failed".equals(decision)) state.put("gateAdvancedDate", today);

            if ("passed".equals(decision)) {
                state.put("phaseDay", Math.max(1, state.optInt("phaseDay", 1)) + 1);
                if (!state.optBoolean("sabaqFrozen", false)) advanceSabaqAfterGate();
                int oldPhase = state.optInt("hifzPhase", 3);
                JSONObject sabaq = state.optJSONObject("sabaq");
                int nextPhase = sabaq == null ? oldPhase : resolveNativeHifzPhase(sabaq);
                state.put("hifzPhase", nextPhase);
                state.put("sabaqFrozen", false).put("sabaqFrozenForNextDay", false);
                if (nextPhase == 4 && oldPhase != 4) {
                    JSONObject plan = NativeHifzPlan.build(state, Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY);
                    Object firstJuz = plan.opt("manzilCursorFirst");
                    state.put("hvNextJuz", firstJuz == null ? JSONObject.NULL : firstJuz);
                } else advanceNativeManzilCursor(nextPhase);
            } else if ("sabaq_frozen".equals(decision)) {
                state.put("sabaqFrozen", true).put("sabaqFrozenForNextDay", true)
                        .put("phaseDay", Math.max(1, state.optInt("phaseDay", 1)) + 1);
                advanceNativeManzilCursor(state.optInt("hifzPhase", 3));
            }
            persistState();
            renderTab("schedule");
            toast("Evaluasi tersimpan.");
        } catch (JSONException exception) { toast("Evaluasi tidak dapat disimpan."); }
    }

    private void advanceSabaqAfterGate() throws JSONException {
        JSONObject sabaq = state.optJSONObject("sabaq");
        if (sabaq == null) return;
        boolean fullMushaf = sabaq.optInt("rangeStartPage", 1) == 1 && sabaq.optInt("rangeEndPage", 604) == 604;
        if (sabaq.optBoolean("sabaqCompleted", false) || sabaq.optBoolean("targetCompleted", false)) {
            JSONArray history = state.optJSONArray("sabqiHistory");
            if (history != null && history.length() > 0) state.put("sabqiHistory", removeFirst(history));
            return;
        }
        boolean sundayAudit = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
                && !sabaq.optBoolean("sundayNewSabaq", false);
        if (sundayAudit) return;
        int page = sabaq.optInt("currentPage", sabaq.optInt("rangeStartPage", 1));
        int end = sabaq.optInt("rangeEndPage", 604);
        String mode = sabaq.optString("mode", "half_page");
        String completed = currentSabaqUnitLabel(sabaq);
        int nextPage = page;
        int nextSlot = sabaq.optInt("subSlot", 0);
        boolean done = false;
        switch (mode) {
            case "quarter_page":
                if (nextSlot < 3) nextSlot++;
                else if (page < end) { nextPage++; nextSlot = 0; }
                else done = true;
                break;
            case "half_page":
                if (nextSlot == 0) nextSlot = 1;
                else if (page < end) { nextPage++; nextSlot = 0; }
                else done = true;
                break;
            case "multi_page":
                nextPage = Math.min(end + 1, page + Math.max(1, sabaq.optInt("pagesPerDay", 2)));
                nextSlot = 0;
                done = nextPage > end;
                break;
            case "ayah":
                advanceAyahUnit(sabaq, end);
                nextPage = sabaq.optInt("currentPage", page);
                nextSlot = 0;
                done = sabaq.optBoolean("targetCompleted", false) || sabaq.optBoolean("sabaqCompleted", false);
                break;
            default:
                if (page < end) nextPage++;
                else done = true;
                nextSlot = 0;
                break;
        }
        recordSabqiUnit(completed, page, mode, sabaq.optInt("subSlot", 0),
                sabaq.optInt("pagesPerDay", 2), done);
        sabaq.put("currentPage", Math.min(end, nextPage)).put("subSlot", nextSlot);
        if (done) sabaq.put("sabaqCompleted", fullMushaf).put("targetCompleted", !fullMushaf);
        state.put("sabaq", sabaq);
    }

    private void recordSabqiUnit(String unit, int page, String mode, int slot, int pagesPerDay,
                                  boolean targetDone) throws JSONException {
        JSONObject conserved = state.optJSONObject("conservedPartialSpans");
        if (conserved == null) conserved = new JSONObject();
        boolean closesPage = targetDone || "one_page".equals(mode) || "multi_page".equals(mode)
                || ("half_page".equals(mode) && slot == 1)
                || ("quarter_page".equals(mode) && slot == 3);
        int clearThrough = "multi_page".equals(mode) ? page + Math.max(1, pagesPerDay) - 1 : page;
        if (closesPage) for (int cleared = page; cleared <= clearThrough; cleared++) conserved.remove(String.valueOf(cleared));

        JSONArray history = state.optJSONArray("sabqiHistory");
        if (history == null) history = new JSONArray();
        history.put(new JSONObject().put("unitShort", unit).put("approxPage", page).put("mode", mode));
        while (history.length() > NativeSabqiHistory.MAX_UNITS) {
            JSONObject evicted = history.optJSONObject(0);
            history = removeFirst(history);
            if (evicted == null) continue;
            int evictedPage = evicted.optInt("approxPage", 0);
            String evictedText = evicted.optString("unitShort", "");
            boolean stillInSabqi = false;
            for (int i = 0; i < history.length(); i++) {
                JSONObject item = history.optJSONObject(i);
                if (item != null && item.optInt("approxPage", -1) == evictedPage) { stillInSabqi = true; break; }
            }
            if (evictedPage < 1 || evictedPage > 604 || stillInSabqi) continue;
            String label = null;
            if ("half_page".equals(evicted.optString("mode")) && evictedText.contains("Atas")) label = "Baris 1–8";
            else if ("quarter_page".equals(evicted.optString("mode"))) {
                if (evictedText.contains("Baris 1–4")) label = "Baris 1–4";
                else if (evictedText.contains("Baris 5–8")) label = "Baris 1–8";
                else if (evictedText.contains("Baris 9–12")) label = "Baris 1–12";
            }
            if (label != null && !(closesPage && evictedPage >= page && evictedPage <= clearThrough)) {
                conserved.put(String.valueOf(evictedPage), new JSONObject()
                        .put("page", evictedPage).put("from", JSONObject.NULL).put("to", JSONObject.NULL).put("label", label));
            }
        }
        syncConservedPartialSpans(history, conserved);
        state.put("conservedPartialSpans", conserved);
        state.put("sabqiHistory", history);
    }

    private void syncConservedPartialSpans(JSONArray history, JSONObject conserved) throws JSONException {
        Map<Integer, List<JSONObject>> byPage = new HashMap<>();
        for (int i = 0; i < history.length(); i++) {
            JSONObject item = history.optJSONObject(i);
            if (item == null) continue;
            int page = item.optInt("approxPage", 0);
            if (page < 1 || page > 604) continue;
            byPage.computeIfAbsent(page, ignored -> new ArrayList<>()).add(item);
        }
        Pattern ayahPattern = Pattern.compile("(\\d+):(\\d+)(?:[–-](\\d+))?");
        Map<Integer, Integer> counts = nativeAyahCounts();
        for (Map.Entry<Integer, List<JSONObject>> entry : byPage.entrySet()) {
            int page = entry.getKey();
            List<JSONObject> onPage = entry.getValue();
            JSONObject first = onPage.get(0), last = onPage.get(onPage.size() - 1);
            String firstText = first.optString("unitShort", "");
            String lastText = last.optString("unitShort", "");
            String firstMode = first.optString("mode", "");
            if (onPage.stream().anyMatch(item -> "one_page".equals(item.optString("mode"))
                    || "multi_page".equals(item.optString("mode")))) {
                conserved.remove(String.valueOf(page));
                continue;
            }
            if ("half_page".equals(firstMode)) {
                if (lastText.contains("Bawah")) {
                    if (firstText.contains("Atas") || conserved.has(String.valueOf(page))) conserved.remove(String.valueOf(page));
                } else if (firstText.contains("Atas") && lastText.contains("Atas")) {
                    conserved.put(String.valueOf(page), partialSpanJson(page, "Baris 1–8", null, null));
                }
                continue;
            }
            if ("quarter_page".equals(firstMode)) {
                if (lastText.contains("Baris 13–15")) {
                    if (firstText.contains("Baris 1–4") || conserved.has(String.valueOf(page))) conserved.remove(String.valueOf(page));
                } else if (firstText.contains("Baris 1–4")) {
                    String lines = lastText.contains("Baris 1–4") ? "1–4"
                            : (lastText.contains("Baris 5–8") ? "1–8"
                            : (lastText.contains("Baris 9–12") ? "1–12" : null));
                    if (lines != null) conserved.put(String.valueOf(page), partialSpanJson(page, "Baris " + lines, null, null));
                }
                continue;
            }
            if (!"ayah".equals(firstMode)) continue;
            Matcher matcher = ayahPattern.matcher(lastText);
            int toSurah = 0, toAyah = 0;
            while (matcher.find()) {
                toSurah = parseInt(matcher.group(1), 0);
                toAyah = parseInt(matcher.group(3) == null ? matcher.group(2) : matcher.group(3), 0);
            }
            if (toSurah < 1 || toSurah > 114 || toAyah < 1) continue;
            NativeQuranPageMap.AyahRef next = nextAyah(toSurah, toAyah, counts);
            if (next == null || NativeQuranPageMap.estimatePage(next.surah, next.ayah, counts) > page) {
                conserved.remove(String.valueOf(page));
                continue;
            }
            NativeQuranPageMap.AyahRef from = new NativeQuranPageMap.AyahRef(toSurah, toAyah);
            NativeQuranPageMap.AyahRef probe = previousAyah(toSurah, toAyah, counts);
            while (probe != null && NativeQuranPageMap.estimatePage(probe.surah, probe.ayah, counts) >= page) {
                from = probe;
                probe = previousAyah(probe.surah, probe.ayah, counts);
            }
            String label = ayahSpanLabel(from, new NativeQuranPageMap.AyahRef(toSurah, toAyah));
            conserved.put(String.valueOf(page), partialSpanJson(page, label,
                    new JSONObject().put("s", from.surah).put("a", from.ayah),
                    new JSONObject().put("s", toSurah).put("a", toAyah)));
        }
    }

    private static JSONObject partialSpanJson(int page, String label, JSONObject from, JSONObject to) throws JSONException {
        JSONObject span = new JSONObject().put("page", page).put("label", label);
        span.put("from", from == null ? JSONObject.NULL : from);
        span.put("to", to == null ? JSONObject.NULL : to);
        return span;
    }

    private NativeQuranPageMap.AyahRef previousAyah(int surah, int ayah, Map<Integer, Integer> counts) {
        if (ayah > 1) return new NativeQuranPageMap.AyahRef(surah, ayah - 1);
        if (surah <= 1) return null;
        return new NativeQuranPageMap.AyahRef(surah - 1, counts.getOrDefault(surah - 1, 1));
    }

    private NativeQuranPageMap.AyahRef nextAyah(int surah, int ayah, Map<Integer, Integer> counts) {
        if (ayah < counts.getOrDefault(surah, 1)) return new NativeQuranPageMap.AyahRef(surah, ayah + 1);
        if (surah >= 114) return null;
        return new NativeQuranPageMap.AyahRef(surah + 1, 1);
    }

    private String ayahSpanLabel(NativeQuranPageMap.AyahRef from, NativeQuranPageMap.AyahRef to) {
        String start = sabaqAyahReference(from.surah, from.ayah);
        if (from.surah == to.surah) return from.ayah == to.ayah ? start : start + "–" + to.ayah;
        return start + " → " + sabaqAyahReference(to.surah, to.ayah);
    }

    private void advanceNativeManzilCursor(int phase) throws JSONException {
        JSONObject plan = NativeHifzPlan.build(state,
                Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY);
        Object next = plan.opt("manzilCursorNext");
        String key = phase == 4 ? "hvNextJuz" : "lvManzilPage";
        if (next == null || next == JSONObject.NULL) state.put(key, JSONObject.NULL);
        else state.put(key, next);
    }

    private boolean isUndoableNativeAction(JSONObject action) {
        if (action == null) return false;
        String type = action.optString("type", "");
        return "native_gate".equals(type) || "gate".equals(type)
                || "native_sabqi_freeze".equals(type) || "sabqi_freeze".equals(type);
    }

    private void undoGateDecision() {
        JSONArray undo = state.optJSONArray("undoStack");
        if (undo == null || undo.length() == 0) return;
        JSONObject action = undo.optJSONObject(undo.length() - 1);
        if (!isUndoableNativeAction(action)) return;
        try {
            undo = removeLast(undo);
            restoreNativeAction(action);
            state.put("undoStack", undo);
            persistState();
            publishCurrentNotification();
            syncTimerScreenAwake();
            renderTab(currentTab);
            toast("Perubahan terakhir diurungkan.");
        } catch (JSONException exception) { toast("Perubahan terakhir tidak dapat diurungkan."); }
    }

    private void restoreNativeAction(JSONObject action) throws JSONException {
        String type = action.optString("type", "");
        if ("native_gate".equals(type) || "gate".equals(type)) {
            state.put("hifzPhase", action.optInt("prevPhase", state.optInt("hifzPhase", 3)))
                    .put("phaseDay", action.optInt("prevDay", state.optInt("phaseDay", 1)))
                    .put("sabaq", copyJson(action.optJSONObject("prevSabaq")))
                    .put("sabqiHistory", copyJson(action.optJSONArray("prevHistory")))
                    .put("sabaqFrozen", action.optBoolean("prevFrozen", false))
                    .put("sabaqFrozenForNextDay", action.optBoolean("prevFrozenForNextDay", false));
            if (action.has("prevSubStep")) state.put("primarySubStep", action.optInt("prevSubStep", 0));
            if (action.has("prevConservedSpans")) {
                state.put("conservedPartialSpans", copyJson(action.optJSONObject("prevConservedSpans")));
            }
            if (action.has("prevCompleted")) {
                state.put("completedBlocksToday", copyJson(action.optJSONArray("prevCompleted")));
            } else if (action.optBoolean("addedBlock", false)) {
                JSONArray completed = state.optJSONArray("completedBlocksToday");
                JSONArray restored = new JSONArray();
                if (completed != null) for (int i = 0; i < completed.length(); i++) {
                    if (!"presleep_gate".equals(completed.optString(i))) restored.put(completed.get(i));
                }
                state.put("completedBlocksToday", restored);
            }
            for (String[] pair : new String[][]{
                    {"prevGateResult", "gateResultToday"}, {"prevGateAdvancedDate", "gateAdvancedDate"},
                    {"prevHvNextJuz", "hvNextJuz"}, {"prevLvManzilPage", "lvManzilPage"}}) {
                if (action.has(pair[0])) restoreNullable(action, pair[0], pair[1]);
            }
            return;
        }

        state.put("sabaqFrozen", action.optBoolean("prevFrozen", false))
                .put("sabaqFrozenForNextDay", action.optBoolean("prevFrozenForNextDay", false));
        if (action.has("prevSubStep")) state.put("primarySubStep", action.optInt("prevSubStep", 0));
        if (action.has("prevBlockTimers")) {
            state.put("blockTimers", copyJson(action.optJSONObject("prevBlockTimers")));
        } else {
            JSONObject timers = state.optJSONObject("blockTimers");
            if (timers == null) timers = new JSONObject();
            restoreTimerRecord(timers, "primary_hifz_step_1", action.opt("prevStep1Timer"),
                    action.optBoolean("hadStep1Timer", false));
            Object previousStep2 = action.opt("prevStep2Timer");
            restoreTimerRecord(timers, "primary_hifz_step_2", previousStep2,
                    previousStep2 instanceof JSONObject);
            state.put("blockTimers", timers);
        }
    }

    private void restoreTimerRecord(JSONObject timers, String key, Object prior, boolean existed) throws JSONException {
        if (existed && prior instanceof JSONObject) timers.put(key, new JSONObject(prior.toString()));
        else timers.remove(key);
    }

    private void restoreNullable(JSONObject source, String from, String to) throws JSONException {
        Object value = source.opt(from);
        if (value == null || value == JSONObject.NULL) state.put(to, JSONObject.NULL);
        else state.put(to, value);
    }

    private static JSONArray removeFirst(JSONArray source) throws JSONException {
        JSONArray result = new JSONArray();
        for (int i = 1; i < source.length(); i++) result.put(source.get(i));
        return result;
    }

    private static JSONArray removeLast(JSONArray source) throws JSONException {
        JSONArray result = new JSONArray();
        for (int i = 0; i < source.length() - 1; i++) result.put(source.get(i));
        return result;
    }

    private static JSONObject copyJson(JSONObject source) throws JSONException {
        return source == null ? new JSONObject() : new JSONObject(source.toString());
    }

    private static JSONArray copyJson(JSONArray source) throws JSONException {
        return source == null ? new JSONArray() : new JSONArray(source.toString());
    }

    private void renderHifz() {
        JSONObject sabaq = state.optJSONObject("sabaq");
        if (sabaq == null) sabaq = new JSONObject();
        renderPageHeading("Hafalan", "Target dan murajaah");

        LinearLayout phaseCard = card();
        JSONObject nativePlan = getNativeHifzPlan();
        int phase = nativePlan == null ? state.optInt("hifzPhase", 3) : nativePlan.optInt("phase", state.optInt("hifzPhase", 3));
        LinearLayout phaseHeader = new LinearLayout(this);
        phaseHeader.setGravity(Gravity.CENTER_VERTICAL);
        phaseHeader.addView(text(phase == 4 ? "Fase 4 · Manzil Juz" : "Fase 3 · Pemulihan", 13, colorAccentText(), true),
                new LinearLayout.LayoutParams(0, WRAP, 1f));
        phaseHeader.addView(text("Hari " + Math.max(1, state.optInt("phaseDay", 1)), 13, colorMuted(), true));
        phaseCard.addView(phaseHeader);
        phaseCard.addView(text(currentSabaqUnitFullLabel(sabaq), 17, colorPrimary(), true));
        android.widget.ProgressBar mushaf = new android.widget.ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        mushaf.setMax(604);
        mushaf.setProgress(Math.max(0, Math.min(604, sabaq.optInt("currentPage", 1))));
        mushaf.setProgressTintList(ColorStateList.valueOf(colorAccent()));
        mushaf.setProgressBackgroundTintList(ColorStateList.valueOf(colorBorder()));
        mushaf.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        phaseCard.addView(mushaf, new LinearLayout.LayoutParams(MATCH, dp(6)));
        phaseCard.addView(text("Halaman " + sabaq.optInt("currentPage", 1) + " dari 604"
                + (state.optBoolean("sabaqFrozen", false) ? " · Sabaq dijeda" : ""), 12, colorMuted(), false));
        body.addView(phaseCard);
        addGap(body, 12);

        LinearLayout review = card();
        renderHifzReview(review);
        body.addView(review);
        addGap(body, 12);

        final JSONObject sabaqTarget = sabaq;
        body.addView(collapsible("hifz_target", "Atur target Sabaq",
                SABAQ_MODE_LABELS[Math.max(0, indexOf(SABAQ_MODE_KEYS, sabaq.optString("mode", "half_page")))],
                target -> renderSabaqTargetSettings(target, sabaqTarget)));
        addGap(body, 12);

        String paceSummary = phase == 4
                ? Math.max(1, Math.min(2, state.optInt("HighVolumeJuzPerDay", 1))) + " Juz per hari"
                : (state.optInt("lvPace", 10) == 20 ? 20 : 10) + " halaman per hari";
        body.addView(collapsible("hifz_pace", "Atur porsi Manzil", paceSummary, paceCard -> {
            if (phase == 4) {
                Spinner pace = spinner(new String[]{"1 Juz per hari", "2 Juz per hari"});
                pace.setSelection(Math.max(0, Math.min(1, state.optInt("HighVolumeJuzPerDay", 1) - 1)));
                pace.setOnItemSelectedListener(simpleItemListener(position -> {
                    stateSafePut("HighVolumeJuzPerDay", position + 1);
                    persistState();
                    if (showManzilList) renderHifzReview(review);
                }));
                paceCard.addView(labeled("Porsi harian", pace));
                Switch maghrib = editorSwitch("Tambahkan sesi Maghrib", state.optBoolean("HighVolumeMaghribOn", false));
                maghrib.setOnCheckedChangeListener((button, checked) -> {
                    stateSafePut("HighVolumeMaghribOn", checked);
                    persistState();
                    if (showManzilList) renderHifzReview(review);
                });
                paceCard.addView(maghrib);
            } else {
                Spinner pace = spinner(new String[]{"10 halaman per hari", "20 halaman per hari"});
                pace.setSelection(state.optInt("lvPace", 10) == 20 ? 1 : 0);
                pace.setOnItemSelectedListener(simpleItemListener(position -> {
                    stateSafePut("lvPace", position == 1 ? 20 : 10);
                    persistState();
                    if (showManzilList) renderHifzReview(review);
                }));
                paceCard.addView(labeled("Porsi harian", pace));
                Switch maghrib = editorSwitch("Tambahkan sesi Maghrib", state.optBoolean("lvMaghribEnabled", false));
                maghrib.setOnCheckedChangeListener((button, checked) -> {
                    stateSafePut("lvMaghribEnabled", checked);
                    if (!checked) stateSafePut("lvPace", 10);
                    persistState();
                    if (showManzilList) renderHifzReview(review);
                });
                paceCard.addView(maghrib);
            }
            paceCard.addView(secondaryButton("Atur Juz hafalan sebelumnya", this::showPriorJuzDialog));
        }));
        addGap(body, 12);
        body.addView(secondaryButton(state.optBoolean("sabaqFrozen", false)
                ? "Lanjutkan Sabaq" : "Hentikan Sabaq sementara", this::toggleSabaqFreeze));
        JSONArray undo = state.optJSONArray("undoStack");
        JSONObject latestUndo = undo == null || undo.length() == 0 ? null : undo.optJSONObject(undo.length() - 1);
        if (isUndoableNativeAction(latestUndo)) {
            addGap(body, 8);
            body.addView(secondaryButton("Urungkan perubahan terakhir", this::undoGateDecision));
        }
    }

    private void renderSabaqTargetSettings(LinearLayout target, JSONObject sabaq) {
        Spinner juzPreset = spinner(getSabaqPresetLabels());
        juzPreset.setSelection(Math.max(0, Math.min(30, sabaq.optInt("targetJuz", 0))));
        juzPreset.setOnItemSelectedListener(simpleItemListener(this::applySabaqPreset));
        target.addView(labeled("Cakupan mushaf", juzPreset));
        addGap(target, 6);
        Spinner modePicker = spinner(SABAQ_MODE_LABELS);
        modePicker.setSelection(indexOf(SABAQ_MODE_KEYS, sabaq.optString("mode", "half_page")));
        modePicker.setOnItemSelectedListener(simpleItemListener(position -> setSabaqMode(SABAQ_MODE_KEYS[position])));
        target.addView(labeled("Porsi per unit", modePicker));
        if ("multi_page".equals(sabaq.optString("mode", "half_page"))) {
            addGap(target, 6);
            target.addView(text("Halaman per hari: " + Math.max(1, sabaq.optInt("pagesPerDay", 2)), 12, colorMuted(), false));
            target.addView(secondaryButton("Atur jumlah halaman", this::showPagesPerDayDialog));
        }
        if ("ayah".equals(sabaq.optString("mode", "half_page"))) {
            addGap(target, 6);
            target.addView(secondaryButton("Atur rentang ayat dan porsi harian", this::showAyahTargetDialog));
        }
        if (!"ayah".equals(sabaq.optString("mode", "half_page"))) {
            LinearLayout pager = new LinearLayout(this);
            pager.addView(secondaryButton("− 1 halaman", () -> moveSabaqPage(-1)), new LinearLayout.LayoutParams(0, WRAP, 1f));
            LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(0, WRAP, 1f);
            nextParams.leftMargin = dp(8);
            pager.addView(secondaryButton("+ 1 halaman", () -> moveSabaqPage(1)), nextParams);
            target.addView(pager);
            addGap(target, 6);
        }
        if (!"ayah".equals(sabaq.optString("mode", "half_page"))) {
            target.addView(secondaryButton("Atur rentang halaman", this::showSabaqRangeDialog));
        }
        addGap(target, 6);
        final JSONObject sabaqForSundaySetting = sabaq;
        Switch sundayNew = editorSwitch("Izinkan Sabaq baru pada hari Minggu",
                sabaq.optBoolean("sundayNewSabaq", false));
        sundayNew.setOnCheckedChangeListener((button, checked) -> {
            try {
                sabaqForSundaySetting.put("sundayNewSabaq", checked);
                persistState();
            } catch (JSONException ignored) { toast("Setelan Minggu tidak dapat disimpan."); }
        });
        target.addView(sundayNew);
        addGap(target, 6);
        target.addView(text("Progres unit maju setelah evaluasi malam dinyatakan lulus.",
                12, colorMuted(), false));
    }

    private void renderHifzReview(LinearLayout review) {
        review.removeAllViews();
        TextView title = text("Daftar murajaah", 16, colorPrimary(), true);
        if (Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
        review.addView(title);
        addGap(review, 12);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabs.setBackground(rounded(colorSubtle(), colorBorder(), dp(13), dp(1)));
        review.addView(tabs);
        TextView sabqiTab = addHifzListTab(tabs, "Daftar Sabqi", false);
        TextView manzilTab = addHifzListTab(tabs, "Daftar Manzil", true);
        addGap(review, 16);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        review.addView(list, new LinearLayout.LayoutParams(MATCH, WRAP));
        Runnable refresh = () -> {
            styleHifzListTab(sabqiTab, !showManzilList);
            styleHifzListTab(manzilTab, showManzilList);
            list.removeAllViews();
            if (showManzilList) renderManzilList(list);
            else renderSabqiList(list);
        };
        sabqiTab.setOnClickListener(view -> {
            if (!showManzilList) return;
            showManzilList = false;
            refresh.run();
        });
        manzilTab.setOnClickListener(view -> {
            if (showManzilList) return;
            showManzilList = true;
            refresh.run();
        });
        refresh.run();
    }

    private TextView addHifzListTab(LinearLayout tabs, String label, boolean second) {
        TextView tab = text(label, 14, colorPrimary(), true);
        tab.setFocusable(true);
        tab.setGravity(Gravity.CENTER);
        tab.setMinimumHeight(dp(48));
        tab.setPadding(dp(8), dp(6), dp(8), dp(6));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, MATCH, 1f);
        if (second) params.setMarginStart(dp(4));
        tabs.addView(tab, params);
        return tab;
    }

    private void styleHifzListTab(TextView tab, boolean selected) {
        tab.setSelected(selected);
        tab.setTextColor(selected ? colorAccentText() : colorPrimary());
        tab.setBackground(rippleBackground(selected ? colorAccentSoft() : colorSubtle(),
                colorAccentBorder(), dp(10), selected ? dp(1) : 0));
    }

    private void renderSabqiList(LinearLayout review) {
        JSONArray history = state.optJSONArray("sabqiHistory");
        if (history == null || history.length() == 0) {
            review.addView(text("Sabqi masih kosong. Unit Sabaq masuk ke daftar setelah evaluasi malam dinyatakan lulus.",
                    14, colorSecondary(), false));
            return;
        }
        review.addView(text("Hingga 7 unit Sabaq terbaru. Unit baru masuk setelah evaluasi malam lulus.",
                13, colorSecondary(), false));
        addGap(review, 8);
        int start = Math.max(0, history.length() - NativeSabqiHistory.MAX_UNITS);
        for (int i = start; i < history.length(); i++) {
            JSONObject entry = history.optJSONObject(i);
            if (entry == null) continue;
            int page = entry.optInt("approxPage");
            String label = entry.optString("unitShort", "Bacaan Sabqi");
            String detail = page < 1 || page > 604 ? "" : "Juz " + NativeHifzPlan.juzForPage(page);
            if (!detail.isEmpty() && !label.startsWith("Halaman ")) detail += " · mulai di halaman " + page;
            addReviewRow(review, label, detail, i == history.length() - 1 ? "Terbaru" : "", i > start);
        }
    }

    private void renderManzilList(LinearLayout review) {
        JSONObject plan;
        try {
            plan = NativeHifzPlan.buildWithManzilList(state,
                    Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY);
        } catch (JSONException exception) {
            review.addView(text("Daftar Manzil tidak dapat ditampilkan.", 14, colorSecondary(), false));
            return;
        }

        JSONArray list = plan.optJSONArray("manzilList");
        if (list == null || list.length() == 0) {
            review.addView(text("Manzil masih kosong. Daftar terisi dari bacaan yang lulus Sabqi dan Juz hafalan sebelumnya.",
                    14, colorSecondary(), false));
        } else {
            String subuh = plan.optString("dawnTarget");
            String maghrib = plan.optString("maghribTarget");
            JSONObject first = list.optJSONObject(0);
            boolean showSessions = !maghrib.isEmpty() || first == null || !subuh.equals(first.optString("label"));
            if (showSessions) {
                addReviewRow(review, subuh, "Subuh", "", false);
                if (!maghrib.isEmpty()) addReviewRow(review, maghrib, "Maghrib", "", true);
                addGap(review, 12);
            }
            boolean juzMode = plan.optInt("phase") == 4;
            int count = plan.optInt("manzilPoolSize");
            review.addView(text("Rotasi Manzil · " + count + (juzMode ? " Juz" : " halaman"), 14, colorSecondary(), true));
            addGap(review, 8);
            String activeLabel = todayString().equals(state.optString("gateAdvancedDate", "")) ? "Berikutnya" : "Hari ini";
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.optJSONObject(i);
                if (item == null) continue;
                String detail = i == 0 && !showSessions ? "Subuh" : "";
                addReviewRow(review, item.optString("label"), detail, item.optBoolean("today") ? activeLabel : "", i > 0);
            }
        }
        int partialPage = plan.optInt("partialPage");
        if (partialPage > 0) {
            addGap(review, 12);
            review.addView(text("Hafalan sebagian · Halaman " + partialPage + " · " + plan.optString("partialLabel"),
                    13, colorSecondary(), false));
        }
    }

    private void addReviewRow(LinearLayout parent, String title, String detail, String status, boolean separate) {
        boolean highlighted = !status.isEmpty();
        if (separate && highlighted) {
            addGap(parent, 4);
        } else if (separate) {
            View divider = new View(this);
            divider.setBackgroundColor(colorBorder());
            parent.addView(divider, new LinearLayout.LayoutParams(MATCH, dp(1)));
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        if (highlighted) {
            row.setBackground(rounded(colorAccentSoft(), colorAccentBorder(), dp(11), dp(1)));
            row.addView(text(status, 12, colorAccentText(), true));
            addGap(row, 4);
        }
        row.addView(text(title, 15, colorPrimary(), true));
        if (!detail.isEmpty()) {
            addGap(row, 4);
            row.addView(text(detail, 13, colorSecondary(), false));
        }
        parent.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    private void renderPrayer() {
        boolean ready = PrayerReminders.locationReady(state);
        String selected = state.optString("prayerSource", "fixed");
        renderPageHeading("Waktu shalat", PrayerReminders.sourceLabel(state));

        if (!ready) {
            LinearLayout warning = card();
            warning.setBackground(rounded(withAlpha(0xFFF59E0B, darkTheme ? 40 : 28), withAlpha(0xFFF59E0B, 150), dp(20), dp(1)));
            TextView title = text("Jadwal belum sesuai lokasimu", 17, colorPrimary(), true);
            warning.addView(title);
            addGap(warning, 6);
            warning.addView(text("Waktu di bawah hanya contoh dan pengingat adzan belum aktif. Pilih kota atau gunakan GPS "
                    + "agar jadwal dihitung dengan kriteria Kemenag RI untuk tempatmu.", 14, colorSecondary(), false));
            addGap(warning, 10);
            warning.addView(actionButton("Pilih kota", R.drawable.ic_location, this::showCityPicker));
            addGap(warning, 8);
            warning.addView(secondaryButton("Gunakan lokasi GPS", () -> {
                stateSafePut("prayerSource", "gps");
                persistState();
                requestCurrentLocation();
                renderTab("prayer");
            }));
            body.addView(warning);
            addGap(body, 12);
        }

        NativePrayerCalculator.PrayerTimes computed = PrayerReminders.prayerTimesFor(state, new Date());
        Map<String, String> times = getActivePrayerTimes();
        long now = System.currentTimeMillis();
        PrayerReminders.Next upcoming = PrayerReminders.nextPrayer(state, now);
        if (ready && upcoming != null) {
            LinearLayout hero = card();
            GradientDrawable heroFill = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{colorAccent(), darkTheme ? 0xFF2A3FB0 : 0xFF3B6CF8});
            heroFill.setCornerRadius(dp(16));
            hero.setBackground(heroFill);
            hero.setPadding(dp(20), dp(18), dp(20), dp(18));
            TextView kicker = text("SHALAT BERIKUTNYA", 11, withAlpha(Color.WHITE, 200), true);
            kicker.setLetterSpacing(0.1f);
            hero.addView(kicker);
            addGap(hero, 8);
            LinearLayout line = new LinearLayout(this);
            line.setGravity(Gravity.BOTTOM);
            line.addView(text(PrayerReminders.label(upcoming.key), 30, Color.WHITE, true), new LinearLayout.LayoutParams(0, WRAP, 1f));
            TextView clock = text(upcoming.time, 30, Color.WHITE, true);
            clock.setTypeface(monoTypeface(), Typeface.BOLD);
            line.addView(clock);
            hero.addView(line);
            addGap(hero, 6);
            nextPrayerCountdown = text("", 14, withAlpha(Color.WHITE, 225), false);
            hero.addView(nextPrayerCountdown);
            updateNextPrayerCountdown();
            body.addView(hero);
            addGap(body, 12);
        }

        LinearLayout timesCard = card();
        timesCard.setPadding(dp(10), dp(10), dp(10), dp(10));
        Calendar today = Calendar.getInstance();
        TextView dateLabel = text(new java.text.SimpleDateFormat("EEEE, d MMMM yyyy", new Locale("id", "ID")).format(today.getTime()),
                12, colorMuted(), true);
        dateLabel.setPadding(dp(10), dp(4), dp(10), dp(8));
        timesCard.addView(dateLabel);
        int nowMinutes = today.get(Calendar.HOUR_OF_DAY) * 60 + today.get(Calendar.MINUTE);
        String nextKey = upcoming != null && upcoming.at - now < 24 * 3600_000L
                && PrayerReminders.label(upcoming.key) != null ? upcoming.key : null;
        for (int i = 0; i < PRAYER_KEYS.length; i++) {
            final String key = PRAYER_KEYS[i];
            if ("dzuhur".equals(key) && computed != null && !computed.terbit.isEmpty()) {
                addPrayerRow(timesCard, null, "Terbit", computed.terbit, false, NativePrayerCalculator.toMinutes(computed.terbit) <= nowMinutes, false);
            }
            boolean isNext = ready && key.equals(nextKey) && NativePrayerCalculator.toMinutes(times.get(key)) > nowMinutes;
            boolean past = NativePrayerCalculator.toMinutes(times.get(key)) <= nowMinutes;
            if ("manual".equals(selected)) {
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(10), dp(4), dp(4), dp(4));
                row.addView(text(PRAYER_LABELS[i], 15, colorPrimary(), true), new LinearLayout.LayoutParams(0, WRAP, 1f));
                EditText field = input(times.get(key), "HH:MM", InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
                field.setEms(5);
                field.setOnFocusChangeListener((view, focused) -> {
                    if (!focused) saveManualPrayerTime(key, field.getText().toString());
                });
                row.addView(field);
                row.addView(reminderBell(key));
                timesCard.addView(row);
            } else {
                addPrayerRow(timesCard, key, PRAYER_LABELS[i], times.get(key), isNext, past, ready);
            }
        }
        body.addView(timesCard);
        addGap(body, 12);

        String[] sourceNames = {"Kota", "GPS", "Manual"};
        String[] sourceKeyOrder = {"city", "gps", "manual"};
        IndonesianCities.City sourceCity = PrayerReminders.city(state);
        String sourceSummary = "city".equals(selected) && sourceCity != null ? sourceCity.name
                : sourceNames[Math.max(0, indexOf(sourceKeyOrder, selected))];
        body.addView(collapsible("prayer_source", "Lokasi & metode", sourceSummary, sourceCard -> {
            String[] sourceKeys = {"city", "gps", "manual"};
            Spinner source = spinner(new String[]{"Pilih kota", "Lokasi GPS", "Isi waktu manual"});
            source.setSelection(Math.max(0, indexOf(sourceKeys, selected)));
            source.setOnItemSelectedListener(simpleItemListener(position -> {
                if (sourceKeys[position].equals(state.optString("prayerSource"))) return;
                if (position == 0) { showCityPicker(); return; }
                stateSafePut("prayerSource", sourceKeys[position]);
                persistState();
                if (position == 1) requestCurrentLocation();
                renderTab("prayer");
            }));
            sourceCard.addView(labeled("Sumber jadwal", source));
            if ("city".equals(selected)) {
                addGap(sourceCard, 10);
                IndonesianCities.City city = PrayerReminders.city(state);
                sourceCard.addView(labeled("Kota", secondaryButton(city == null ? "Pilih kota…" : city.name + "  ·  ubah",
                        R.drawable.ic_location, this::showCityPicker)));
            }
            if ("city".equals(selected) || "gps".equals(selected)) {
                addGap(sourceCard, 10);
                Spinner method = spinner(PRAYER_METHOD_LABELS);
                method.setSelection(indexOf(PRAYER_METHOD_KEYS, state.optString("gpsMethod", "kemenag")));
                method.setOnItemSelectedListener(simpleItemListener(position -> {
                    stateSafePut("gpsMethod", PRAYER_METHOD_KEYS[position]);
                    persistState();
                    renderTab("prayer");
                }));
                sourceCard.addView(labeled("Metode perhitungan", method));
            }
            if ("gps".equals(selected)) {
                addGap(sourceCard, 10);
                JSONObject location = state.optJSONObject("gpsLocation");
                sourceCard.addView(text((location == null ? "Lokasi belum ditentukan" : location.optString("label", "Lokasi perangkat"))
                        + (location != null && location.has("lat") && location.has("lon")
                        ? " · " + String.format(Locale.ROOT, "%.4f, %.4f", location.optDouble("lat"), location.optDouble("lon"))
                        : " · koordinat belum tersedia"), 12, colorMuted(), false));
                addGap(sourceCard, 8);
                sourceCard.addView(secondaryButton("Perbarui lokasi", R.drawable.ic_location, this::requestCurrentLocation));
                addGap(sourceCard, 8);
                EditText latitude = input(location == null || !location.has("lat") ? "" : String.valueOf(location.optDouble("lat")),
                        "Contoh: -6.9175", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
                EditText longitude = input(location == null || !location.has("lon") ? "" : String.valueOf(location.optDouble("lon")),
                        "Contoh: 107.6191", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
                sourceCard.addView(labeled("Lintang", latitude));
                addGap(sourceCard, 6);
                sourceCard.addView(labeled("Bujur", longitude));
                addGap(sourceCard, 8);
                sourceCard.addView(secondaryButton("Gunakan koordinat ini", () -> saveManualCoordinates(latitude, longitude)));
            }
        }));
        addGap(body, 12);

        renderAdzanCard(ready);
        addGap(body, 12);

        body.addView(collapsible("prayer_more", "Lainnya", "Jeda hafalan setelah Subuh · metode hitung", offsetCard -> {
            EditText offsetField = input(String.valueOf(state.optInt("subuhOffsetMins", 15)), "15", InputType.TYPE_CLASS_NUMBER);
            offsetField.setOnFocusChangeListener((view, focused) -> {
                if (!focused) {
                    int minutes = parseInt(offsetField.getText().toString(), -1);
                    if (minutes < 0 || minutes > 240) { toast("Jeda Subuh harus 0–240 menit."); return; }
                    stateSafePut("subuhOffsetMins", minutes);
                    persistState();
                }
            });
            offsetCard.addView(labeled("Mulai hafalan setelah Subuh (menit)", offsetField));
            offsetCard.addView(text("Dihitung di perangkat dengan kriteria Kemenag RI (Subuh 20°, Isya 18°, Ashar Syafi'i) ditambah "
                    + "ihtiyath dan koreksi ketinggian; hasil dibulatkan ke atas sehingga tidak lebih awal dari jadwal resmi. "
                    + "Bisa berbeda 1–2 menit dari jadwal Kemenag daerahmu.", 12, colorMuted(), false));
        }));
    }

    private void addPrayerRow(LinearLayout parent, String key, String label, String time, boolean next, boolean past, boolean ready) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(52));
        row.setPadding(dp(12), dp(4), dp(4), dp(4));
        if (next) row.setBackground(rounded(colorAccentSoft(), colorAccentBorder(), dp(14), dp(1)));
        boolean info = key == null;
        int color = next ? colorAccentText() : (info || past) ? colorMuted() : colorPrimary();
        TextView name = text(label, info ? 13 : 16, color, !info);
        row.addView(name, new LinearLayout.LayoutParams(0, WRAP, 1f));
        if (next) {
            TextView badge = pill("berikutnya", colorAccent(), colorOnAccent());
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(WRAP, WRAP);
            badgeParams.setMarginEnd(dp(10));
            row.addView(badge, badgeParams);
        }
        TextView clock = text(ready || info ? time : time + "*", info ? 13 : 17, color, !info);
        clock.setTypeface(monoTypeface(), info ? Typeface.NORMAL : Typeface.BOLD);
        row.addView(clock);
        if (info) {
            View spacer = new View(this);
            row.addView(spacer, new LinearLayout.LayoutParams(dp(44), dp(1)));
        } else {
            row.addView(reminderBell(key));
        }
        row.setContentDescription(label + " " + time + (next ? ", shalat berikutnya" : ""));
        parent.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    private ImageView reminderBell(String key) {
        ImageView bell = new ImageView(this);
        bell.setImageResource(R.drawable.ic_notification);
        bell.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        bell.setPadding(dp(12), dp(12), dp(12), dp(12));
        bell.setBackground(rippleShape(dp(14)));
        boolean on = PrayerReminders.enabled(this, key);
        bell.setColorFilter(on ? colorAccentText() : colorMuted());
        bell.setAlpha(on ? 1f : .35f);
        bell.setContentDescription("Pengingat " + PrayerReminders.label(key) + (on ? " aktif" : " mati"));
        bell.setOnClickListener(view -> {
            if (!PrayerReminders.enabled(this)) { toast("Nyalakan pengingat adzan dulu."); return; }
            SharedPreferences prefs = PrayerReminders.prefs(this);
            boolean next = !prefs.getBoolean("adzan_" + key, true);
            prefs.edit().putBoolean("adzan_" + key, next).apply();
            PrayerReminders.schedule(this);
            toast("Pengingat " + PrayerReminders.label(key) + (next ? " dinyalakan." : " dimatikan."));
            renderTab("prayer");
        });
        return bell;
    }

    private void updateNextPrayerCountdown() {
        if (nextPrayerCountdown == null) return;
        PrayerReminders.Next upcoming = PrayerReminders.nextPrayer(state, System.currentTimeMillis());
        if (upcoming == null) { nextPrayerCountdown.setText(""); return; }
        nextPrayerCountdown.setText(PrayerReminders.countdown(upcoming.at - System.currentTimeMillis())
                + " · " + PrayerReminders.sourceLabel(state));
    }

    private void showCityPicker() {
        String[] names = new String[IndonesianCities.ALL.length];
        IndonesianCities.City current = PrayerReminders.city(state);
        int currentIndex = -1;
        for (int i = 0; i < names.length; i++) {
            IndonesianCities.City city = IndonesianCities.ALL[i];
            names[i] = city.name + "  ·  " + (city.tz == 7 ? "WIB" : city.tz == 8 ? "WITA" : "WIT");
            if (current != null && city.name.equals(current.name)) currentIndex = i;
        }
        showOptionSheet("Pilih kota terdekat", names, null, currentIndex, position -> applyCity(IndonesianCities.ALL[position]));
    }

    private void applyCity(IndonesianCities.City city) {
        try {
            stateSafePut("cityLocation", new JSONObject().put("name", city.name));
            stateSafePut("prayerSource", "city");
            persistState();
            toast("Jadwal shalat memakai " + city.name + ".");
            renderTab("prayer");
        } catch (JSONException ignored) { toast("Kota tidak dapat disimpan."); }
    }

    private void renderAdzanCard(boolean ready) {
        boolean enabled = PrayerReminders.enabled(this);
        LinearLayout reminder = card();
        Switch master = editorSwitch("Pengingat adzan", enabled);
        master.setTextSize(15);
        master.setTypeface(semiboldTypeface());
        reminder.addView(master);
        if (enabled) {
            String[] modes = {PrayerReminders.MODE_ADZAN, PrayerReminders.MODE_ALARM, PrayerReminders.MODE_NOTIFICATION};
            Spinner mode = spinner(new String[]{"Suara adzan", "Nada alarm (berulang sampai dimatikan)", "Notifikasi biasa (sekali bunyi)"});
            mode.setSelection(Math.max(0, indexOf(modes, PrayerReminders.mode(this))));
            mode.setOnItemSelectedListener(simpleItemListener(position -> {
                PrayerReminders.prefs(this).edit().putString("adzan_mode", modes[position]).apply();
                renderTab("prayer");
            }));
            reminder.addView(labeled("Bunyi saat masuk waktu", mode));
            if (!ready) {
                reminder.addView(text("Aktif setelah kota atau lokasi GPS dipilih.", 13, colorSecondary(), false));
            } else {
                reminder.addView(text(PrayerReminders.describe(PrayerReminders.schedule(this))
                        + "\nKetuk lonceng di daftar waktu untuk mengatur per waktu shalat.", 12, colorMuted(), false));
            }
            boolean notificationsAllowed = Build.VERSION.SDK_INT < 33
                    || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            if (!notificationsAllowed) {
                reminder.addView(text("Izin notifikasi belum diberikan, jadi pengingat tidak bisa tampil.", 13, colorSecondary(), false));
                reminder.addView(actionButton("Izinkan notifikasi", () -> requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_ADZAN_NOTIFICATION)));
            } else if (!PrayerReminders.canScheduleExact(this)) {
                reminder.addView(text("Izinkan \"Alarm & pengingat\" agar tepat waktu; tanpa izin ini bisa terlambat beberapa menit.",
                        13, colorSecondary(), false));
                reminder.addView(actionButton("Izinkan alarm tepat waktu", this::openExactAlarmSettings));
            }
        }
        master.setOnCheckedChangeListener((button, checked) -> {
            PrayerReminders.prefs(this).edit().putBoolean("adzan_enabled", checked).apply();
            if (checked && Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_ADZAN_NOTIFICATION);
            }
            PrayerReminders.schedule(this);
            renderTab("prayer");
        });
        body.addView(reminder);
        if (!enabled) return;

        boolean adzanMode = PrayerReminders.MODE_ADZAN.equals(PrayerReminders.mode(this));
        String general = PrayerReminders.prefs(this).getString("adzan_uri", null);
        String subuh = PrayerReminders.prefs(this).getString("adzan_uri_subuh", null);
        String soundSummary = adzanMode ? (general == null ? "Adzan Mekkah (bawaan)" : audioName(general)) : "Uji coba bunyi";
        addGap(body, 12);
        body.addView(collapsible("prayer_sound", adzanMode ? "Suara adzan & uji coba" : "Uji coba bunyi", soundSummary, panel -> {
            if (adzanMode) {
                panel.addView(text("Adzan: " + (general == null ? "Adzan Mekkah (bawaan)" : audioName(general))
                        + "\nAdzan Subuh: " + (subuh != null ? audioName(subuh) : general == null ? "Adzan Mekkah (bawaan)" : audioName(general)),
                        13, colorSecondary(), false));
                panel.addView(secondaryButton(general == null ? "Ganti dengan file adzan lain" : "Ganti suara adzan",
                        R.drawable.ic_headphones, () -> pickAdzanFile(REQUEST_ADZAN_FILE)));
                panel.addView(secondaryButton(subuh == null ? "Suara khusus Subuh (opsional)" : "Ganti suara Subuh",
                        () -> pickAdzanFile(REQUEST_ADZAN_FILE_SUBUH)));
                if (general != null || subuh != null) {
                    panel.addView(secondaryButton("Kembali ke Adzan Mekkah bawaan", () -> {
                        PrayerReminders.prefs(this).edit().remove("adzan_uri").remove("adzan_uri_subuh").apply();
                        renderTab("prayer");
                    }));
                }
            } else if (PrayerReminders.MODE_NOTIFICATION.equals(PrayerReminders.mode(this))) {
                panel.addView(text("Notifikasi biasa mengikuti nada dan mode senyap HP.", 12, colorMuted(), false));
            }
            if (!PrayerReminders.MODE_NOTIFICATION.equals(PrayerReminders.mode(this))) {
                panel.addView(text("Adzan dan nada alarm memakai volume alarm, jadi tetap berbunyi saat HP disenyapkan. "
                        + "Hentikan dengan tombol Matikan di notifikasi.", 12, colorMuted(), false));
            }
            boolean notificationsAllowed = Build.VERSION.SDK_INT < 33
                    || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            if (notificationsAllowed && ready) {
                LinearLayout tests = new LinearLayout(this);
                tests.addView(secondaryButton("Coba bunyi", R.drawable.ic_play, () -> {
                    if (!PrayerReminders.notifyTest(this)) toast("Notifikasi belum diizinkan.");
                }), new LinearLayout.LayoutParams(0, WRAP, 1f));
                LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(0, WRAP, 1f);
                stopParams.leftMargin = dp(8);
                tests.addView(secondaryButton("Hentikan", R.drawable.ic_pause, () -> AdzanPlayerService.stop(this)), stopParams);
                panel.addView(tests);
            } else {
                panel.addView(text("Uji coba tersedia setelah lokasi dipilih dan notifikasi diizinkan.", 12, colorMuted(), false));
            }
        }));
    }

    private void pickAdzanFile(int requestCode) {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(pick, requestCode); }
        catch (Exception missing) { toast("Tidak ada aplikasi pemilih file di HP ini."); }
    }

    private String audioName(String uri) {
        try (android.database.Cursor cursor = getContentResolver().query(Uri.parse(uri),
                new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (Exception ignored) { }
        return "file audio";
    }

    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < 31) return;
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())));
        } catch (Exception ignored) {
            toast("Buka Setelan › Aplikasi › Rutinitas › Alarm & pengingat.");
        }
    }

    private void renderSystem() {
        renderPageHeading("Setelan", "Tampilan, notifikasi, dan data");

        LinearLayout appearance = card();
        addKicker(appearance, "TAMPILAN");
        Spinner theme = spinner(new String[]{"Ikuti perangkat", "Terang", "Gelap"});
        String themeValue = state.optString("themePreference", "system");
        theme.setSelection("light".equals(themeValue) ? 1 : ("dark".equals(themeValue) ? 2 : 0));
        theme.setOnItemSelectedListener(simpleItemListener(position -> {
            String nextPreference = position == 1 ? "light" : (position == 2 ? "dark" : "system");
            if (nextPreference.equals(state.optString("themePreference", "system"))) return;
            stateSafePut("themePreference", nextPreference);
            persistState();
            rebuildAppearance();
        }));
        appearance.addView(labeled("Tema aplikasi", theme));
        String[] styles = {"solid", "transparent", "blur", "glass"};
        Spinner surface = spinner(new String[]{"Solid", "Transparan", "Blur", "Glass"});
        surface.setSelection(indexOf(styles, surfaceStyle()));
        surface.setOnItemSelectedListener(simpleItemListener(position -> {
            if (styles[position].equals(surfaceStyle())) return;
            surfacePrefs().edit().putString("surface_style", styles[position]).apply();
            rebuildAppearance();
        }));
        appearance.addView(labeled("Gaya panel dan navigasi", surface));
        if (Build.VERSION.SDK_INT < 31) appearance.addView(text(
                "Blur tersedia mulai Android 12; di perangkat ini memakai efek transparan.", 12, colorMuted(), false));
        Switch motion = editorSwitch("Animasi antarlayar", surfacePrefs().getBoolean("motion_enabled", true));
        motion.setOnCheckedChangeListener((button, checked) -> surfacePrefs().edit().putBoolean("motion_enabled", checked).apply());
        appearance.addView(motion);
        body.addView(appearance);
        addGap(body, 12);

        LinearLayout surfaces = card();
        addKicker(surfaces, "NOTIFIKASI & WIDGET");
        Switch notification = new Switch(this);
        notification.setText(R.string.native_notification_setting);
        notification.setTextColor(colorPrimary());
        notification.setTextSize(14);
        notification.setTypeface(appTypeface());
        notification.setMinHeight(dp(48));
        styleSwitch(notification);
        boolean notificationPermissionGranted = Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        notification.setChecked(surfacePrefs().getBoolean("notification_enabled", true)
                && notificationPermissionGranted);
        notification.setOnCheckedChangeListener((button, checked) -> setNotificationEnabled(checked));
        surfaces.addView(notification);
        Switch widget = new Switch(this);
        widget.setText(R.string.native_widget_setting);
        widget.setTextColor(colorPrimary());
        widget.setTextSize(14);
        widget.setTypeface(appTypeface());
        widget.setMinHeight(dp(48));
        styleSwitch(widget);
        widget.setChecked(surfacePrefs().getBoolean("widget_enabled", false));
        widget.setOnCheckedChangeListener((button, checked) -> setWidgetEnabled(checked));
        surfaces.addView(widget);
        LinearLayout timerAlarm = surfaces;
        Switch alarmEnabled = editorSwitch("Bunyikan alarm saat timer selesai",
                state.optBoolean("timerAlarmEnabled", true));
        alarmEnabled.setOnCheckedChangeListener((button, checked) -> {
            stateSafePut("timerAlarmEnabled", checked);
            persistState();
        });
        timerAlarm.addView(alarmEnabled);
        LinearLayout surfaceButtons = new LinearLayout(this);
        surfaceButtons.addView(secondaryButton("Tambah widget", this::requestWidgetPin), new LinearLayout.LayoutParams(0, WRAP, 1f));
        LinearLayout.LayoutParams chimeParams = new LinearLayout.LayoutParams(0, WRAP, 1f);
        chimeParams.leftMargin = dp(8);
        surfaceButtons.addView(secondaryButton("Tes suara timer", () -> {
            playTimerClockChime(true);
            toast("Lonceng jam diputar.");
        }), chimeParams);
        surfaces.addView(surfaceButtons);
        surfaces.addView(secondaryButton("Setelan notifikasi Android", this::openNotificationSettings));
        body.addView(surfaces);
        addGap(body, 12);

        LinearLayout backup = card();
        addKicker(backup, "DATA");
        backup.addView(text("Semua data tersimpan di perangkat. Gunakan cadangan JSON untuk memindahkan data.",
                13, colorSecondary(), false));
        LinearLayout backupButtons = new LinearLayout(this);
        backupButtons.addView(secondaryButton("Ekspor", R.drawable.ic_download, this::beginExport), new LinearLayout.LayoutParams(0, WRAP, 1f));
        LinearLayout.LayoutParams importParams = new LinearLayout.LayoutParams(0, WRAP, 1f);
        importParams.leftMargin = dp(8);
        backupButtons.addView(secondaryButton("Impor", R.drawable.ic_upload, this::beginImport), importParams);
        backup.addView(backupButtons);
        backup.addView(secondaryButton("Reset aktivitas hari ini", this::confirmResetToday));
        body.addView(backup);
        addGap(body, 12);

        body.addView(collapsible("system_more", "Lainnya", "Tanggal & waktu · sumber & lisensi", credits -> {
            credits.addView(secondaryButton("Setelan tanggal dan waktu", () -> {
                try { startActivity(new Intent(Settings.ACTION_DATE_SETTINGS)); }
                catch (Exception exception) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            }));
            addKicker(credits, "SUMBER & LISENSI");
            credits.addView(text("Teks Al-Qur'an: Tanzil Project (Uthmani v1.1, CC BY 3.0), disalin tanpa perubahan.", 13, colorSecondary(), false));
            credits.addView(sourceLabel("tanzil.net", "https://tanzil.net/"));
            credits.addView(text("Nama surah: transliterasi Kementerian Agama RI. Terjemahan Indonesia: EQuran.id (Kemenag RI).", 13, colorSecondary(), false));
            credits.addView(sourceLabel("equran.id", "https://equran.id/"));
            credits.addView(text("Murattal dan waktu ayat: MP3Quran.net (Yasser Al-Dosari, Mishary Alafasy).", 13, colorSecondary(), false));
            credits.addView(sourceLabel("mp3quran.net", "https://mp3quran.net/"));
            credits.addView(text("Suara adzan bawaan: rekaman adzan Masjidil Haram, Makkah, dari islamiques.net. Hak cipta tetap milik "
                    + "pemegang hak; sumber tidak mencantumkan lisensi. Pemegang hak yang keberatan dapat menghubungi "
                    + "depupdev@gmail.com agar rekaman dihapus atau diganti.", 13, colorSecondary(), false));
            credits.addView(sourceLabel("islamiques.net", "https://islamiques.net/"));
            credits.addView(text("Jadwal shalat dihitung di perangkat dengan kriteria Kemenag RI. Huruf: Amiri Quran dan Inter "
                    + "(SIL Open Font License 1.1).", 13, colorSecondary(), false));
        }));
    }

    private void confirmResetToday() {
        new AlertDialog.Builder(this)
                .setTitle("Reset aktivitas hari ini?")
                .setMessage("Timer, langkah hafalan, dan status agenda hari ini akan dibersihkan. Perubahan evaluasi dan jeda Sabaq hari ini ikut diurungkan.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Reset hari ini", (dialog, which) -> resetTodayProgress())
                .show();
    }

    private void resetTodayProgress() {
        try {
            JSONArray undo = state.optJSONArray("undoStack");
            if (undo != null) {
                for (int i = undo.length() - 1; i >= 0; i--) {
                    JSONObject action = undo.optJSONObject(i);
                    if (isUndoableNativeAction(action)) restoreNativeAction(action);
                }
            }
            state.put("completedBlocksToday", new JSONArray())
                    .put("blockTimers", new JSONObject())
                    .put("primarySubStep", 0)
                    .put("gateResultToday", JSONObject.NULL)
                    .put("gateAdvancedDate", JSONObject.NULL)
                    .put("undoStack", new JSONArray());
            if (timerToneGenerator != null) {
                timerToneGenerator.stopTone();
                timerToneGenerator.release();
                timerToneGenerator = null;
            }
            persistState();
            publishCurrentNotification();
            syncTimerScreenAwake();
            renderTab(currentTab);
            toast("Progres dan timer hari ini direset.");
        } catch (JSONException exception) { toast("Progres hari ini tidak dapat direset."); }
    }

    private void renderQuran() {
        if (quranReading) renderQuranReader();
        else renderQuranIndex();
    }

    private int quranReciter() {
        int reciter = surfacePrefs().getInt("quran_reciter", 0);
        return Math.max(0, Math.min(NativeQuranAudio.NAMES.length - 1, reciter));
    }

    private void renderQuranAudio() {
        LinearLayout player = card();
        audioTitle = text("", 16, colorPrimary(), true);
        player.addView(audioTitle);
        audioDetail = text("", 13, colorSecondary(), false);
        addGap(player, 5);
        player.addView(audioDetail);
        addGap(player, 8);
        LinearLayout controls = new LinearLayout(this);
        audioPlay = smallButton("Putar");
        audioPlay.setOnClickListener(view -> quranAudio.toggle(quranReciter(), audioSurah));
        controls.addView(audioPlay, new LinearLayout.LayoutParams(0, WRAP, 1f));
        audioDownload = smallButton("Unduh");
        audioDownload.setOnClickListener(view -> toggleAudioDownload());
        LinearLayout.LayoutParams downloadParams = new LinearLayout.LayoutParams(0, WRAP, 1f);
        downloadParams.setMarginStart(dp(8));
        controls.addView(audioDownload, downloadParams);
        ImageView options = new ImageView(this);
        options.setImageResource(R.drawable.ic_dock_settings);
        options.setColorFilter(colorAccentText());
        options.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        options.setPadding(dp(12), dp(12), dp(12), dp(12));
        options.setBackground(rippleBackground(colorSubtle(), colorBorder(), dp(11), dp(1)));
        options.setContentDescription("Pengaturan audio");
        options.setFocusable(true);
        options.setOnClickListener(view -> showQuranAudioSettings());
        LinearLayout.LayoutParams optionsParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        optionsParams.setMarginStart(dp(8));
        controls.addView(options, optionsParams);
        player.addView(controls);
        audioSeek = new SeekBar(this);
        audioSeek.setContentDescription("Posisi audio");
        audioSeek.setProgressTintList(ColorStateList.valueOf(colorAccent()));
        audioSeek.setThumbTintList(ColorStateList.valueOf(colorAccent()));
        audioSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser && !seekingAudio) quranAudio.seek(progress);
                if (fromUser) audioTime.setText(formatSeconds(progress / 1000) + " / " + formatSeconds(bar.getMax() / 1000));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { seekingAudio = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) { seekingAudio = false; quranAudio.seek(bar.getProgress()); }
        });
        audioSeek.setVisibility(View.GONE);
        player.addView(audioSeek);
        audioTime = text("", 12, colorMuted(), false);
        audioTime.setVisibility(View.GONE);
        player.addView(audioTime);
        body.addView(player);
        addGap(body, 16);
        seekingAudio = false;
        updateQuranAudioControls();
    }

    private void updateQuranAudioControls() {
        if (audioTitle == null || quranAudio == null) return;
        int voice = quranReciter();
        boolean active = quranAudio.matches(voice, audioSurah);
        boolean stored = quranAudio.hasDownload(voice, audioSurah);
        boolean downloading = quranAudio.isDownloading(voice, audioSurah);
        audioTitle.setText("Murattal · " + quranName(audioSurah));
        audioDetail.setText(NativeQuranAudio.NAMES[voice] + " · " + (stored ? "Tersedia offline" : "Audio per surah"));
        audioPlay.setText(active && quranAudio.loading() ? "Batalkan"
                : active && quranAudio.isPlaying() ? "Jeda" : active ? "Lanjutkan" : "Putar");
        audioDownload.setText(downloading ? (quranAudio.downloadPercent() < 0 ? "Mengunduh…" : quranAudio.downloadPercent() + "%")
                : stored ? "Hapus offline" : "Unduh");
        boolean canDownload = stored || !quranAudio.isDownloading();
        audioDownload.setEnabled(canDownload);
        audioDownload.setAlpha(canDownload ? 1f : .65f);
        updateAudioTimeline();
    }

    private void updateAudioTimeline() {
        if (audioTitle == null || seekingAudio || quranAudio == null) return;
        int duration = quranAudio.matches(quranReciter(), audioSurah) ? quranAudio.duration() : 0;
        int visibility = duration > 0 ? View.VISIBLE : View.GONE;
        audioSeek.setVisibility(visibility);
        audioTime.setVisibility(visibility);
        if (duration > 0) {
            int position = quranAudio.position();
            audioSeek.setMax(duration);
            audioSeek.setProgress(position);
            audioTime.setText(formatSeconds(position / 1000) + " / " + formatSeconds(duration / 1000));
        }
    }

    private void toggleAudioDownload() {
        int reciter = quranReciter();
        int chapter = audioSurah;
        if (!quranAudio.hasDownload(reciter, chapter)) {
            quranAudio.download(reciter, chapter);
            return;
        }
        new AlertDialog.Builder(this).setTitle("Hapus unduhan?")
                .setMessage(quranName(chapter) + " masih bisa diputar lewat internet.")
                .setNegativeButton("Batal", null).setPositiveButton("Hapus", (dialog, which) -> {
                    if (!quranAudio.removeDownload(reciter, chapter)) toast("Unduhan tidak dapat dihapus.");
                    updateQuranAudioControls();
                }).show();
    }

    private void showQuranAudioSettings() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), 0);
        Spinner reciters = spinner(NativeQuranAudio.NAMES);
        reciters.setSelection(quranReciter());
        form.addView(labeled("Qari", reciters));
        addGap(form, 8);
        Switch automatic = editorSwitch("Putar surah berikutnya", surfacePrefs().getBoolean("quran_auto_next", false));
        form.addView(automatic);
        Switch follow = editorSwitch("Ikuti ayat yang dibacakan (gulir otomatis)", surfacePrefs().getBoolean("quran_follow", true));
        form.addView(follow);
        addGap(form, 10);
        form.addView(text("Sumber audio dan waktu ayat: MP3Quran.net. Unduhan disimpan di perangkat untuk diputar offline.",
                13, colorSecondary(), false));
        new AlertDialog.Builder(this).setTitle("Pengaturan audio").setView(form)
                .setNegativeButton("Batal", null).setPositiveButton("Simpan", (dialog, which) -> {
                    int reciter = reciters.getSelectedItemPosition();
                    if (reciter != quranReciter()) quranAudio.stop();
                    surfacePrefs().edit().putInt("quran_reciter", reciter)
                            .putBoolean("quran_auto_next", automatic.isChecked())
                            .putBoolean("quran_follow", follow.isChecked()).apply();
                    updateQuranAudioControls();
                }).show();
    }

    private void renderPageHeading(String title, String subtitle) {
        TextView heading = text(title, 24, colorPrimary(), true);
        if (Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
        body.addView(heading);
        addGap(body, 4);
        body.addView(text(subtitle, 13, colorMuted(), false));
        addGap(body, 14);
    }

    private void renderQuranIndex() {
        renderPageHeading("Al-Qur’an", "Pilih surah untuk membaca atau mendengarkan murattal.");
        LinearLayout shortcuts = card();
        JSONObject reader = state.optJSONObject("quranReader");
        JSONObject last = reader == null ? null : reader.optJSONObject("lastRead");
        if (last != null) {
            shortcuts.addView(recentQuranButton("Lanjutkan", last));
        }
        JSONObject bookmark = reader == null ? null : reader.optJSONObject("bookmark");
        if (bookmark != null) {
            shortcuts.addView(recentQuranButton("Penanda", bookmark));
        }
        if (shortcuts.getChildCount() > 0) {
            body.addView(shortcuts);
            addGap(body, 16);
        }
        if (showIndexAudio) renderQuranAudio();

        EditText search = input(quranSearch, "Cari nama atau nomor surah", InputType.TYPE_CLASS_TEXT);
        search.setContentDescription("Cari surah");
        body.addView(search, new LinearLayout.LayoutParams(MATCH, dp(50)));
        addGap(body, 12);
        LinearLayout chapters = card();
        chapters.setPadding(dp(12), dp(6), dp(12), dp(6));
        body.addView(chapters);
        refreshSurahIndex(chapters);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                quranSearch = s.toString();
                quranIndexLimit = 24;
                refreshSurahIndex(chapters);
            }
            @Override public void afterTextChanged(Editable editable) { }
        });
    }

    private View recentQuranButton(String action, JSONObject reference) {
        int chapter = Math.max(1, Math.min(114, reference.optInt("surah", 1)));
        int ayah = reference.optInt("ayah", 1);
        return secondaryButton(action + ": " + quranName(chapter) + " · Ayat " + ayah,
                () -> openQuranChapter(chapter, ayah));
    }

    private void refreshSurahIndex(LinearLayout chapters) {
        chapters.removeAllViews();
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        chapters.addView(list, new LinearLayout.LayoutParams(MATCH, WRAP));
        String query = searchKey(quranSearch);
        int shown = 0;
        boolean more = false;
        for (int chapter = 1; chapter <= 114; chapter++) {
            if (!query.isEmpty() && !searchKey(quranName(chapter)).contains(query)
                    && !searchKey(surahSearchAliases.getOrDefault(chapter, "")).contains(query)
                    && !searchKey(surahArabicNames.getOrDefault(chapter, "")).contains(query)
                    && !String.valueOf(chapter).equals(query)) continue;
            if (shown >= quranIndexLimit) { more = true; break; }
            if (shown > 0) addDivider(list);
            list.addView(surahIndexRow(chapter));
            shown++;
        }
        if (shown == 0) {
            list.addView(text("Surah tidak ditemukan.", 14, colorSecondary(), false));
        } else if (more) {
            addGap(list, 8);
            list.addView(secondaryButton("Tampilkan surah berikutnya", () -> {
                quranIndexLimit += 24;
                refreshSurahIndex(chapters);
            }));
            addGap(list, 6);
        }
    }

    private View surahIndexRow(int chapter) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout open = new LinearLayout(this);
        open.setGravity(Gravity.CENTER_VERTICAL);
        open.setMinimumHeight(dp(64));
        open.setPadding(dp(2), dp(8), dp(6), dp(8));
        open.setBackground(rippleBackground(Color.TRANSPARENT, colorBorder(), dp(10), 0));
        open.setFocusable(true);
        open.setContentDescription(quranName(chapter) + ", " + quranSurahMeta(chapter));
        open.setOnClickListener(view -> openQuranChapter(chapter, 1));
        TextView number = text(String.valueOf(chapter), 12, colorAccentText(), true);
        number.setGravity(Gravity.CENTER);
        number.setBackground(rounded(colorAccentSoft(), colorAccentBorder(), dp(9), dp(1)));
        open.addView(number, new LinearLayout.LayoutParams(dp(32), dp(32)));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(9), 0, 0, 0);
        labels.addView(text(quranName(chapter), 14, colorPrimary(), true));
        addGap(labels, 3);
        labels.addView(text(quranSurahMeta(chapter), 11, colorSecondary(), false));
        open.addView(labels, new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView arabicName = arabicText(surahArabicNames.getOrDefault(chapter, ""), 18);
        arabicName.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        arabicName.setPadding(dp(4), 0, 0, 0);
        arabicName.setMaxWidth(dp(84));
        open.addView(arabicName, new LinearLayout.LayoutParams(WRAP, WRAP));
        open.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        for (int i = 0; i < open.getChildCount(); i++) {
            open.getChildAt(i).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
        row.addView(open, new LinearLayout.LayoutParams(0, WRAP, 1f));
        ImageView play = new ImageView(this);
        play.setImageResource(R.drawable.ic_headphones);
        play.setColorFilter(colorAccentText());
        play.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        play.setPadding(dp(12), dp(12), dp(12), dp(12));
        play.setContentDescription("Dengarkan " + quranName(chapter));
        play.setFocusable(true);
        play.setBackground(rippleBackground(Color.TRANSPARENT, colorBorder(), dp(10), 0));
        play.setOnClickListener(view -> {
            audioSurah = chapter;
            quranAudio.toggle(quranReciter(), chapter);
            if (!showIndexAudio) {
                showIndexAudio = true;
                renderTab("quran");
            }
            scrollQuranToTop();
        });
        row.addView(play, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return row;
    }

    private static final java.util.regex.Pattern BASMALA_PREFIX =
            java.util.regex.Pattern.compile("^(\\S+(?:\\s+\\S+){3})([\\s\\S]*)$");

    /** Memisahkan basmalah pembuka dari ayat 1 hanya untuk tampilan; gabungannya sama persis dengan teks sumber. */
    static String[] splitBasmala(String source) {
        java.util.regex.Matcher match = BASMALA_PREFIX.matcher(source);
        if (!match.matches()) return null;
        String[] words = match.group(1).split("\\s+");
        if (!words[0].startsWith("ب") || !words[1].contains("للَّه")) return null;
        return new String[]{match.group(1), match.group(2)};
    }

    private void renderQuranReader() {
        int chapter = quranSurah;
        List<String> verses = surahAyahs.get(chapter);
        ayahBlocks.clear();
        ayahBadges.clear();
        basmalaBlock = null;
        followAyah = -1;
        body.addView(secondaryButton("Daftar surah", R.drawable.ic_nav_quran, this::showQuranIndex));
        addGap(body, 14);

        LinearLayout hero = card();
        GradientDrawable heroBackground = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{
                withAlpha(colorAccent(), darkTheme ? 70 : 34), withAlpha(colorSurface(), darkTheme ? 225 : 240)});
        heroBackground.setCornerRadius(dp(22));
        heroBackground.setStroke(dp(1), colorAccentBorder());
        hero.setBackground(heroBackground);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(dp(18), dp(20), dp(18), dp(18));
        TextView heroArabic = text(surahArabicNames.getOrDefault(chapter, ""), 34, colorPrimary(), false);
        heroArabic.setTypeface(quranTypeface());
        heroArabic.setTextDirection(View.TEXT_DIRECTION_RTL);
        heroArabic.setIncludeFontPadding(true);
        heroArabic.setGravity(Gravity.CENTER);
        hero.addView(heroArabic, new LinearLayout.LayoutParams(MATCH, WRAP));
        addGap(hero, 6);
        TextView heroLatin = text(quranName(chapter), 20, colorPrimary(), true);
        heroLatin.setGravity(Gravity.CENTER);
        if (Build.VERSION.SDK_INT >= 28) heroLatin.setAccessibilityHeading(true);
        hero.addView(heroLatin, new LinearLayout.LayoutParams(MATCH, WRAP));
        addGap(hero, 5);
        TextView heroMeta = text("Surah ke-" + chapter + " · " + quranSurahMeta(chapter), 13, colorSecondary(), false);
        heroMeta.setGravity(Gravity.CENTER);
        hero.addView(heroMeta, new LinearLayout.LayoutParams(MATCH, WRAP));
        body.addView(hero);
        addGap(body, 12);

        if (verses == null || verses.isEmpty()) {
            body.addView(text(getString(R.string.native_quran_unavailable), 15, colorSecondary(), false));
            return;
        }
        quranAyah = Math.max(1, Math.min(verses.size(), quranAyah));
        List<String> translations = quranTranslation.read(chapter, verses.size());
        boolean showTranslation = translations != null && surfacePrefs().getBoolean("quran_translation", true);
        LinearLayout tools = new LinearLayout(this);
        addQuranTool(tools, "Ke ayat", this::jumpToQuranAyah);
        addQuranTool(tools, "Bacaan", () -> showQuranReadingSettings(translations != null, verses.size()));
        addQuranTool(tools, quranBookmarkMatchesCurrent() ? "Ditandai" : "Tandai", this::toggleQuranBookmark);
        body.addView(tools);
        addGap(body, 12);
        renderQuranAudio();

        LinearLayout paper = card();
        paper.setPadding(dp(12), dp(14), dp(12), dp(10));
        paper.setBackground(rounded(darkTheme ? withAlpha(0xFF161A22, 240) : withAlpha(0xFFFFFDF8, 245),
                colorBorder(), dp(22), dp(1)));
        int first = quranAyah;
        int last = Math.min(verses.size(), first + 17);
        TextView range = text("Ayat " + (first == last ? first : first + "–" + last) + " dari " + verses.size(),
                12, colorMuted(), true);
        range.setPadding(dp(6), 0, dp(6), dp(8));
        paper.addView(range);

        String[] basmala = first == 1 && chapter != 1 && chapter != 9 ? splitBasmala(verses.get(0)) : null;
        if (basmala != null) {
            TextView opening = arabicText(basmala[0], Math.max(24, quranFontSize - 2));
            opening.setGravity(Gravity.CENTER);
            opening.setTextColor(colorAccentText());
            opening.setPadding(dp(8), dp(10), dp(8), dp(12));
            opening.setContentDescription("Basmalah");
            paper.addView(opening, new LinearLayout.LayoutParams(MATCH, WRAP));
            basmalaBlock = opening;
        }

        for (int number = first; number <= last; number++) {
            final int verse = number;
            String verseText = verses.get(number - 1);
            if (number == 1 && basmala != null) verseText = basmala[1].trim();
            LinearLayout block = new LinearLayout(this);
            block.setOrientation(LinearLayout.VERTICAL);
            block.setPadding(dp(12), dp(12), dp(12), dp(14));
            block.setBackground(ayahBackground(false));

            LinearLayout header = new LinearLayout(this);
            header.setGravity(Gravity.CENTER_VERTICAL);
            TextView badge = text(String.valueOf(number), 12, colorAccentText(), true);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(ayahBadgeBackground(false));
            badge.setContentDescription("Putar dari ayat " + number);
            badge.setOnClickListener(view -> playFromAyah(verse));
            pressScale(badge);
            header.addView(badge, new LinearLayout.LayoutParams(dp(34), dp(34)));
            TextView reference = text(chapter + ":" + number, 11, colorMuted(), false);
            reference.setPadding(dp(10), 0, 0, 0);
            header.addView(reference, new LinearLayout.LayoutParams(0, WRAP, 1f));
            ImageView listen = new ImageView(this);
            listen.setImageResource(R.drawable.ic_play);
            listen.setColorFilter(colorMuted());
            listen.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            listen.setPadding(dp(12), dp(12), dp(12), dp(12));
            listen.setBackground(rippleShape(dp(12)));
            listen.setContentDescription("Dengarkan ayat " + number);
            listen.setOnClickListener(view -> playFromAyah(verse));
            header.addView(listen, new LinearLayout.LayoutParams(dp(44), dp(44)));
            block.addView(header);

            TextView arabic = arabicText(verseText + " ﴿" + toArabicIndicDigits(number) + "﴾", quranFontSize);
            arabic.setLineSpacing(dp(12), 1.18f);
            arabic.setPadding(dp(2), dp(10), dp(2), dp(4));
            block.addView(arabic, new LinearLayout.LayoutParams(MATCH, WRAP));
            if (showTranslation) {
                addGap(block, 8);
                TextView meaning = text(translations.get(number - 1), 15, colorSecondary(), false);
                meaning.setLineSpacing(dp(4), 1.15f);
                block.addView(meaning);
            }
            block.setOnLongClickListener(view -> {
                if (storeQuranBookmark(chapter, verse)) toast("Penanda disimpan di " + QuranSurahNames.reference(chapter, verse) + ".");
                return true;
            });
            LinearLayout.LayoutParams blockParams = new LinearLayout.LayoutParams(MATCH, WRAP);
            blockParams.topMargin = dp(4);
            paper.addView(block, blockParams);
            ayahBlocks.put(number, block);
            ayahBadges.put(number, badge);
        }
        addGap(paper, 6);
        paper.addView(sourceLabel("Teks Arab · Tanzil Project", "https://tanzil.net/"));
        if (showTranslation) paper.addView(sourceLabel("Terjemahan Indonesia · EQuran.id", "https://equran.id/"));
        body.addView(paper);
        addGap(body, 12);

        LinearLayout pages = new LinearLayout(this);
        if (first > 1) addQuranTool(pages, "Ayat sebelumnya", () -> { markReaderInteraction(); openQuranChapter(chapter, Math.max(1, first - 18)); });
        else if (chapter > 1) addQuranTool(pages, "Surah sebelumnya", () -> { markReaderInteraction(); openQuranChapter(chapter - 1, 1); });
        if (last < verses.size()) addQuranTool(pages, "Ayat berikutnya", () -> { markReaderInteraction(); openQuranChapter(chapter, last + 1); });
        else if (chapter < 114) addQuranTool(pages, "Surah berikutnya", () -> { markReaderInteraction(); openQuranChapter(chapter + 1, 1); });
        body.addView(pages);
        pageScroll.post(this::followRecitation);
    }

    private Drawable ayahBackground(boolean active) {
        return active ? rounded(withAlpha(colorAccent(), darkTheme ? 46 : 22), colorAccentBorder(), dp(18), dp(1))
                : rounded(Color.TRANSPARENT, Color.TRANSPARENT, dp(18), 0);
    }

    private Drawable ayahBadgeBackground(boolean active) {
        GradientDrawable badge = new GradientDrawable();
        badge.setShape(GradientDrawable.OVAL);
        badge.setColor(active ? colorAccent() : colorAccentSoft());
        badge.setStroke(dp(1), active ? colorAccent() : colorAccentBorder());
        return badge;
    }

    private void markReaderInteraction() { lastUserReaderInteraction = android.os.SystemClock.uptimeMillis(); }

    /** Memutar murattal surah yang sedang dibaca mulai dari ayat tertentu (memakai data waktu ayat MP3Quran). */
    private void playFromAyah(int ayah) {
        int voice = quranReciter();
        int chapter = quranSurah;
        List<String> verses = surahAyahs.get(chapter);
        if (verses == null) return;
        int[] starts = quranTiming.get(voice, chapter);
        if (starts == null) {
            toast("Menyiapkan posisi ayat…");
            quranTiming.load(voice, chapter, verses.size(), (reciter, surah) -> {
                if (reciter == quranReciter() && surah == quranSurah) playFromAyah(ayah);
            });
            return;
        }
        int ms = ayah >= 1 && ayah < starts.length ? Math.max(0, starts[ayah]) : 0;
        audioSurah = chapter;
        lastUserReaderInteraction = 0L;
        if (quranAudio.matches(voice, chapter) && quranAudio.duration() > 0) {
            quranAudio.seek(ms);
            if (!quranAudio.isPlaying()) quranAudio.toggle(voice, chapter);
        } else {
            pendingSeekMs = ms;
            quranAudio.play(voice, chapter);
        }
        updateQuranAudioControls();
    }

    /** Menyorot ayat yang sedang dibacakan dan menggulir halaman agar ayat itu terlihat. */
    private void followRecitation() {
        if (!"quran".equals(currentTab) || !quranReading || quranAudio == null || quranTiming == null || pageScroll == null) return;
        int voice = quranReciter();
        if (!quranAudio.matches(voice, quranSurah) || quranAudio.duration() <= 0) return;
        int[] starts = quranTiming.get(voice, quranSurah);
        if (starts == null) {
            List<String> verses = surahAyahs.get(quranSurah);
            if (verses != null) quranTiming.load(voice, quranSurah, verses.size(), (reciter, surah) -> followRecitation());
            return;
        }
        int ayah = NativeQuranTiming.ayahAt(starts, quranAudio.position());
        if (ayah == followAyah) return;
        boolean follow = surfacePrefs().getBoolean("quran_follow", true);
        boolean userBusy = android.os.SystemClock.uptimeMillis() - lastUserReaderInteraction < 5000L;
        if (ayah >= 1 && !ayahBlocks.containsKey(ayah)) {
            if (!follow || userBusy || !quranAudio.isPlaying()) return;
            quranAyah = ayah;
            renderTab("quran");
            return;
        }
        int previous = followAyah;
        followAyah = ayah;
        if (previous >= 1 && ayahBlocks.containsKey(previous)) {
            ayahBlocks.get(previous).setBackground(ayahBackground(false));
            TextView oldBadge = ayahBadges.get(previous);
            oldBadge.setBackground(ayahBadgeBackground(false));
            oldBadge.setTextColor(colorAccentText());
        } else if (previous == 0 && basmalaBlock != null) {
            basmalaBlock.setBackground(null);
        }
        View target = ayah >= 1 ? ayahBlocks.get(ayah) : basmalaBlock;
        if (ayah >= 1) {
            target.setBackground(ayahBackground(true));
            TextView badge = ayahBadges.get(ayah);
            badge.setBackground(ayahBadgeBackground(true));
            badge.setTextColor(colorOnAccent());
        } else if (target != null) {
            target.setBackground(rounded(withAlpha(colorAccent(), darkTheme ? 40 : 18), Color.TRANSPARENT, dp(18), 0));
        }
        if (target == null || !follow || userBusy || !quranAudio.isPlaying()) return;
        int top = 0;
        View cursor = target;
        while (cursor != null && cursor != body) {
            top += cursor.getTop();
            cursor = cursor.getParent() instanceof View ? (View) cursor.getParent() : null;
        }
        int destination = Math.max(0, top + body.getTop() - pageScroll.getHeight() / 4);
        if (reducedMotion()) pageScroll.scrollTo(0, destination);
        else pageScroll.smoothScrollTo(0, destination);
    }

    private Typeface quranTypeface() {
        if (quranFont == null) {
            try { quranFont = Typeface.createFromAsset(getAssets(), "fonts/AmiriQuran-Regular.ttf"); }
            catch (Exception ignored) { quranFont = Typeface.create("serif", Typeface.NORMAL); }
        }
        return quranFont;
    }

    private TextView arabicText(String value, int size) {
        TextView view = text(value, size, colorPrimary(), false);
        view.setTextDirection(View.TEXT_DIRECTION_RTL);
        view.setGravity(Gravity.RIGHT);
        view.setTypeface(quranTypeface());
        view.setIncludeFontPadding(true);
        view.setLineSpacing(dp(6), 1.16f);
        return view;
    }

    private TextView sourceLabel(String value, String url) {
        TextView link = text(value, 12, colorAccentText(), true);
        link.setMinimumHeight(dp(44));
        link.setGravity(Gravity.CENTER_VERTICAL);
        link.setOnClickListener(view -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
            catch (Exception ignored) { toast("Tidak ada aplikasi untuk membuka tautan."); }
        });
        return link;
    }

    private void addQuranTool(LinearLayout row, String name, Runnable action) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, WRAP, 1f);
        if (row.getChildCount() > 0) params.setMarginStart(dp(6));
        row.addView(secondaryButton(name, action), params);
    }

    private void jumpToQuranAyah() {
        List<String> available = surahAyahs.get(quranSurah);
        int total = available == null ? QuranSurahNames.AYAH_COUNTS[Math.max(0, Math.min(113, quranSurah - 1))] : available.size();
        EditText value = input(String.valueOf(quranAyah), "1–" + total, InputType.TYPE_CLASS_NUMBER);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), 0);
        form.addView(labeled("Nomor ayat (1–" + total + ")", value), new LinearLayout.LayoutParams(MATCH, WRAP));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Buka ayat · " + quranName(quranSurah))
                .setView(form).setNegativeButton("Batal", null).setPositiveButton("Buka", null).create();
        dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            List<String> verses = surahAyahs.get(quranSurah);
            int verse = parseInt(value.getText().toString(), -1);
            if (verses == null || verse < 1 || verse > verses.size()) {
                value.setError(quranName(quranSurah) + " hanya memiliki ayat 1–" + total + ".");
                return;
            }
            dialog.dismiss();
            openQuranChapter(quranSurah, verse);
        }));
        dialog.show();
    }

    private void showQuranReadingSettings(boolean hasTranslation, int verseCount) {
        String translation = hasTranslation
                ? (surfacePrefs().getBoolean("quran_translation", true) ? "Sembunyikan terjemahan" : "Tampilkan terjemahan")
                : "Unduh terjemahan Indonesia";
        new AlertDialog.Builder(this).setTitle("Pengaturan bacaan")
                .setItems(new String[]{"Huruf Arab lebih kecil", "Huruf Arab lebih besar", translation}, (dialog, which) -> {
                    if (which < 2) {
                        changeQuranFont(which == 0 ? -2 : 2);
                    } else if (hasTranslation) {
                        SharedPreferences prefs = surfacePrefs();
                        prefs.edit().putBoolean("quran_translation", !prefs.getBoolean("quran_translation", true)).apply();
                        renderTab("quran");
                    } else if (!quranTranslation.loading()) {
                        final int chapter = quranSurah;
                        toast("Mengunduh terjemahan dari EQuran.id…");
                        quranTranslation.download(chapter, verseCount, success -> {
                            if (success && "quran".equals(currentTab) && quranReading && chapter == quranSurah) {
                                surfacePrefs().edit().putBoolean("quran_translation", true).apply();
                                renderTab("quran");
                            }
                            toast(success ? "Terjemahan siap dibaca offline." : "Terjemahan belum dapat diunduh.");
                        });
                    } else {
                        toast("Unduhan terjemahan masih berjalan.");
                    }
                }).setNegativeButton("Tutup", null).show();
    }

    private void openQuranChapter(int chapter, int verse) {
        if (!quranReading) quranIndexScroll = pageScroll.getScrollY();
        quranReading = true;
        quranSurah = chapter;
        List<String> verses = surahAyahs.get(chapter);
        quranAyah = verses == null ? 1 : Math.max(1, Math.min(verses.size(), verse));
        if (audioSurah != chapter && !quranAudio.isPlaying() && !quranAudio.loading()) audioSurah = chapter;
        hideKeyboard();
        saveQuranReaderState();
        renderTab("quran");
        scrollQuranToTop();
    }

    private void showQuranIndex() {
        quranReading = false;
        renderTab("quran");
        pageScroll.post(() -> { if ("quran".equals(currentTab) && !quranReading) pageScroll.scrollTo(0, quranIndexScroll); });
    }

    private void updateBackNavigation() {
        if (Build.VERSION.SDK_INT < 33) return;
        if ("home".equals(currentTab)) {
            if (backCallback != null) {
                getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
                backCallback = null;
            }
        } else if (backCallback == null) {
            backCallback = this::navigateBackInApp;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        }
    }

    private void navigateBackInApp() {
        if ("quran".equals(currentTab) && quranReading) {
            showQuranIndex();
        } else {
            renderTab("home");
        }
    }

    // On Android 13 and later, the platform dispatcher handles back gestures.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() {
        if (Build.VERSION.SDK_INT < 33 && appInitialized && !"home".equals(currentTab)) navigateBackInApp();
        else super.onBackPressed();
    }

    private void hideKeyboard() {
        View focused = getCurrentFocus();
        if (focused == null) return;
        android.view.inputmethod.InputMethodManager keyboard = getSystemService(android.view.inputmethod.InputMethodManager.class);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        focused.clearFocus();
    }

    private void scrollQuranToTop() {
        pageScroll.scrollTo(0, 0);
        pageScroll.post(() -> { if ("quran".equals(currentTab)) pageScroll.scrollTo(0, 0); });
    }

    private String quranName(int surah) { return QuranSurahNames.latin(surah); }

    private String quranSurahMeta(int surah) {
        List<String> ayahs = surahAyahs.get(surah);
        String type = surahRevelationTypes.getOrDefault(surah, "");
        String count = (ayahs == null ? 0 : ayahs.size()) + " ayat";
        if ("Meccan".equalsIgnoreCase(type)) return "Makkiyah · " + count;
        if ("Medinan".equalsIgnoreCase(type)) return "Madaniyah · " + count;
        return count;
    }

    private static String searchKey(String text) {
        String normalized = java.text.Normalizer.normalize(text.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFKD);
        String key = QURAN_SEARCH_SEPARATORS.matcher(normalized).replaceAll("");
        String[][] variants = {{"aa", "a"}, {"ii", "i"}, {"oo", "u"}, {"uu", "u"}, {"ee", "i"},
                {"ts", "s"}, {"th", "s"}, {"dz", "z"}, {"dh", "z"}, {"zh", "z"}, {"gh", "g"}, {"kh", "k"},
                {"sy", "s"}, {"sh", "s"}, {"aw", "au"}, {"ay", "ai"}, {"q", "k"}, {"w", "u"}, {"y", "i"}};
        for (String[] pair : variants) key = key.replace(pair[0], pair[1]);
        return key;
    }

    private void addDivider(LinearLayout parent) {
        View divider = new View(this);
        divider.setBackgroundColor(colorBorder());
        parent.addView(divider, new LinearLayout.LayoutParams(MATCH, dp(1)));
    }

    private String toArabicIndicDigits(int value) {
        String western = Integer.toString(value);
        StringBuilder localized = new StringBuilder(western.length());
        for (int i = 0; i < western.length(); i++) {
            char digit = western.charAt(i);
            localized.append(digit >= '0' && digit <= '9' ? (char) ('٠' + digit - '0') : digit);
        }
        return localized.toString();
    }

    private void loadQuranAssets() {
        surahNames.clear();
        for (int i = 1; i <= 114; i++) surahNames.put(i, QuranSurahNames.latin(i));
        surahSearchAliases.clear();
        surahArabicNames.clear();
        surahRevelationTypes.clear();
        surahAyahs.clear();
        try (InputStream input = getAssets().open("quran-data.xml")) {
            XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
            parser.setInput(input, "UTF-8");
            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && "sura".equals(parser.getName())) {
                    int index = parseInt(parser.getAttributeValue(null, "index"), 0);
                    String tanzilName = parser.getAttributeValue(null, "tname");
                    if (index >= 1 && index <= 114) {
                        surahNames.put(index, QuranSurahNames.latin(index));
                        if (tanzilName != null) surahSearchAliases.put(index, tanzilName);
                        String arabicName = parser.getAttributeValue(null, "name");
                        String revelationType = parser.getAttributeValue(null, "type");
                        if (arabicName != null) surahArabicNames.put(index, arabicName);
                        if (revelationType != null) surahRevelationTypes.put(index, revelationType);
                    }
                }
                event = parser.next();
            }
        } catch (Exception ignored) { }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getAssets().open("quran-uthmani.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int first = line.indexOf('|');
                int second = first < 0 ? -1 : line.indexOf('|', first + 1);
                if (second < 0) continue;
                int surah = parseInt(line.substring(0, first), 0);
                String ayahText = line.substring(second + 1);
                surahAyahs.computeIfAbsent(surah, key -> new ArrayList<>()).add(ayahText);
            }
        } catch (Exception ignored) { }
    }

    private void changeQuranFont(int delta) {
        quranFontSize = Math.max(24, Math.min(42, quranFontSize + delta));
        saveQuranReaderState();
        renderTab("quran");
    }

    private boolean quranBookmarkMatchesCurrent() {
        JSONObject reader = state.optJSONObject("quranReader");
        JSONObject bookmark = reader == null ? null : reader.optJSONObject("bookmark");
        return bookmark != null && bookmark.optInt("surah", -1) == quranSurah
                && bookmark.optInt("ayah", -1) == quranAyah;
    }

    private void toggleQuranBookmark() {
        if (storeQuranBookmark(quranSurah, quranBookmarkMatchesCurrent() ? 0 : quranAyah)) renderTab("quran");
    }

    private boolean storeQuranBookmark(int chapter, int ayah) {
        try {
            JSONObject reader = state.optJSONObject("quranReader");
            if (reader == null) reader = new JSONObject();
            if (ayah == 0) reader.put("bookmark", JSONObject.NULL);
            else reader.put("bookmark", new JSONObject().put("surah", chapter).put("ayah", ayah));
            state.put("quranReader", reader);
            persistState();
            return true;
        } catch (JSONException ignored) {
            toast("Penanda ayat tidak dapat disimpan.");
            return false;
        }
    }

    private void saveQuranReaderState() {
        try {
            JSONObject reader = state.optJSONObject("quranReader");
            if (reader == null) reader = new JSONObject();
            reader.put("lastRead", new JSONObject().put("surah", quranSurah).put("ayah", quranAyah));
            reader.put("fontSize", quranFontSize);
            if (!reader.has("bookmark")) reader.put("bookmark", JSONObject.NULL);
            state.put("quranReader", reader);
            persistState();
        } catch (JSONException ignored) { }
    }

    private void retryLegacyMigration() {
        try { startActivityForResult(new Intent(this, LegacyStateMigrationActivity.class), REQUEST_LEGACY_MIGRATION); }
        catch (Exception exception) { toast("Pemindahan data lama tidak dapat dibuka."); }
    }

    private void showMigrationOptions() {
        new AlertDialog.Builder(this).setTitle("Pindah data versi lama")
                .setItems(new String[]{"Cari data versi lama lagi", "Pilih cadangan JSON v10"},
                        (dialog, choice) -> { if (choice == 0) retryLegacyMigration(); else beginImport(); })
                .setNegativeButton("Tutup", null).show();
    }

    private void addProfilePicker(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        for (int i = 1; i <= 3; i++) {
            final int profileIndex = i;
            String id = "profile_" + i;
            Button button = smallButton("Profil " + i);
            if (id.equals(state.optString("activeRoutineProfileId", "profile_1"))) {
                button.setTextColor(colorOnAccent());
                button.setBackground(rounded(colorAccent(), colorAccent(), dp(12), 0));
            }
            button.setOnClickListener(view -> selectProfile(profileIndex));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, WRAP, 1f);
            if (i > 1) params.leftMargin = dp(6);
            row.addView(button, params);
        }
        parent.addView(row);
    }

    private void selectProfile(int index) {
        try {
            String oldId = state.optString("activeRoutineProfileId", "profile_1");
            JSONObject profiles = state.optJSONObject("routineProfiles");
            if (profiles == null) profiles = new JSONObject();
            JSONObject oldProfile = profiles.optJSONObject(oldId);
            if (oldProfile == null) oldProfile = new JSONObject();
            oldProfile.put("customRoutines", state.optJSONArray("customRoutines") == null
                    ? new JSONArray() : state.optJSONArray("customRoutines"));
            profiles.put(oldId, oldProfile);
            String nextId = "profile_" + index;
            JSONObject nextProfile = profiles.optJSONObject(nextId);
            if (nextProfile == null) nextProfile = new JSONObject().put("name", "Profil " + index).put("customRoutines", new JSONArray());
            state.put("routineProfiles", profiles);
            state.put("activeRoutineProfileId", nextId);
            state.put("customRoutines", nextProfile.optJSONArray("customRoutines") == null
                    ? new JSONArray() : nextProfile.optJSONArray("customRoutines"));
            persistState();
            renderTab("schedule");
        } catch (JSONException ignored) { }
    }

    private void showRoutineEditor(JSONObject existing) {
        JSONObject current = existing == null ? new JSONObject() : existing;
        JSONObject oldAnchor = current.optJSONObject("anchor");
        if (oldAnchor == null) oldAnchor = new JSONObject();
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(4), dp(20), dp(8));
        ScrollView scroll = scrollView();
        scroll.setFillViewport(false);
        scroll.addView(form);

        EditText title = input(current.optString("title", ""), "Nama aktivitas",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText protocol = input(current.optString("protocol", ""), "Catatan atau protokol",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        form.addView(labeled("Nama", title));
        addGap(form, 8);
        form.addView(labeled("Catatan", protocol));
        addGap(form, 8);

        Spinner anchorType = spinner(new String[]{"Waktu tetap", "Setelah shalat", "Setelah aktivitas"});
        String oldType = oldAnchor.optString("type", "fixed");
        anchorType.setSelection("prayer".equals(oldType) ? 1 : ("after_block".equals(oldType) ? 2 : 0));
        form.addView(labeled("Jangkar waktu", anchorType));
        addGap(form, 7);

        LinearLayout fixedGroup = new LinearLayout(this);
        fixedGroup.setOrientation(LinearLayout.VERTICAL);
        EditText fixedTime = input(oldAnchor.optString("time", "09:00"), "HH:MM",
                InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
        fixedGroup.addView(labeled("Waktu", fixedTime));
        form.addView(fixedGroup);

        LinearLayout prayerGroup = new LinearLayout(this);
        prayerGroup.setOrientation(LinearLayout.VERTICAL);
        Spinner prayer = spinner(PRAYER_LABELS);
        String prayerKey = oldAnchor.optString("prayer", "dzuhur");
        prayer.setSelection(Math.max(0, indexOf(PRAYER_KEYS, prayerKey.toLowerCase(Locale.ROOT))));
        prayerGroup.addView(labeled("Shalat", prayer));
        addGap(prayerGroup, 6);
        EditText offset = input(String.valueOf(oldAnchor.optInt("offsetMins", 0)), "0",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        prayerGroup.addView(labeled("Jeda setelah shalat (menit)", offset));
        form.addView(prayerGroup);

        LinearLayout afterGroup = new LinearLayout(this);
        afterGroup.setOrientation(LinearLayout.VERTICAL);
        EditText afterBlock = input(oldAnchor.optString("blockId", "primary_hifz"), "ID aktivitas",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        afterGroup.addView(labeled("ID aktivitas sebelumnya", afterBlock));
        EditText afterOffset = input(String.valueOf(oldAnchor.optInt("offsetMins", 10)), "10",
                InputType.TYPE_CLASS_NUMBER);
        afterGroup.addView(labeled("Jeda setelah aktivitas (menit)", afterOffset));
        form.addView(afterGroup);
        Runnable syncAnchorForm = () -> {
            int selected = anchorType.getSelectedItemPosition();
            fixedGroup.setVisibility(selected == 0 ? View.VISIBLE : View.GONE);
            prayerGroup.setVisibility(selected == 1 ? View.VISIBLE : View.GONE);
            afterGroup.setVisibility(selected == 2 ? View.VISIBLE : View.GONE);
        };
        anchorType.setOnItemSelectedListener(simpleItemListener(position -> syncAnchorForm.run()));
        syncAnchorForm.run();

        addGap(form, 8);
        LinearLayout lengths = new LinearLayout(this);
        EditText duration = input(String.valueOf(current.optInt("durationMins", 30)), "30", InputType.TYPE_CLASS_NUMBER);
        EditText window = input(String.valueOf(current.optInt("windowMins", 30)), "30", InputType.TYPE_CLASS_NUMBER);
        lengths.addView(labeled("Durasi (menit)", duration), new LinearLayout.LayoutParams(0, WRAP, 1f));
        LinearLayout.LayoutParams windowParams = new LinearLayout.LayoutParams(0, WRAP, 1f);
        windowParams.leftMargin = dp(8);
        lengths.addView(labeled("Jendela (menit)", window), windowParams);
        form.addView(lengths);

        addGap(form, 8);
        addKicker(form, "HARI AKTIF");
        LinearLayout week = new LinearLayout(this);
        week.setGravity(Gravity.CENTER_VERTICAL);
        String[] dayLabels = {"Min", "Sen", "Sel", "Rab", "Kam", "Jum", "Sab"};
        CheckBox[] dayChecks = new CheckBox[7];
        JSONArray activeDays = current.optJSONArray("daysOfWeek");
        for (int day = 0; day < 7; day++) {
            CheckBox check = new CheckBox(this);
            check.setText(dayLabels[day]);
            check.setTextColor(colorPrimary());
            check.setTextSize(11);
            check.setPadding(0, 0, 0, 0);
            check.setChecked(activeDays == null || contains(activeDays, day));
            dayChecks[day] = check;
            week.addView(check, new LinearLayout.LayoutParams(0, WRAP, 1f));
        }
        form.addView(week);

        Switch flexible = editorSwitch("Bisa bergeser saat jadwal padat", current.optBoolean("isFlexible", false));
        Switch hifz = editorSwitch("Sertakan target hafalan", current.optBoolean("includeHifzTarget", false));
        Switch commute = editorSwitch("Aktivitas perjalanan", current.optBoolean("isCommute", false));
        form.addView(flexible);
        form.addView(hifz);
        form.addView(commute);
        commute.setOnCheckedChangeListener((button, checked) -> {
            if (checked) { flexible.setChecked(false); hifz.setChecked(false); }
        });
        Spinner conflict = spinner(new String[]{"Geser ke waktu kosong", "Lewati jika bentrok", "Prioritas ketat"});
        String conflictValue = current.optString("conflictPolicy", "shift");
        conflict.setSelection("skip_if_busy".equals(conflictValue) ? 1 : ("strict".equals(conflictValue) ? 2 : 0));
        form.addView(labeled("Saat terjadi bentrok", conflict));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Aktivitas baru" : "Ubah aktivitas")
                .setView(scroll).setNegativeButton("Batal", null).setPositiveButton("Simpan", null).create();
        dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String name = title.getText().toString().trim();
            String fixedValue = fixedTime.getText().toString().trim();
            int minutes = parseInt(duration.getText().toString(), 30);
            int windowMinutes = parseInt(window.getText().toString(), 30);
            JSONArray selectedDays = new JSONArray();
            for (int day = 0; day < dayChecks.length; day++) if (dayChecks[day].isChecked()) selectedDays.put(day);
            if (name.isEmpty()) { toast("Nama aktivitas wajib diisi."); return; }
            if (anchorType.getSelectedItemPosition() == 0 && !validTime(fixedValue)) {
                toast("Gunakan format waktu 24 jam HH:MM."); return;
            }
            if (minutes < 1 || minutes > 480 || windowMinutes < 5 || windowMinutes > 480) {
                toast("Durasi harus 1–480 menit, jendela 5–480 menit."); return;
            }
            if (selectedDays.length() == 0) { toast("Pilih setidaknya satu hari."); return; }
            try {
                JSONObject anchor;
                if (anchorType.getSelectedItemPosition() == 1) {
                    anchor = new JSONObject().put("type", "prayer")
                            .put("prayer", PRAYER_KEYS[Math.max(0, prayer.getSelectedItemPosition())])
                            .put("offsetMins", Math.max(-120, Math.min(240, parseInt(offset.getText().toString(), 0))));
                } else if (anchorType.getSelectedItemPosition() == 2) {
                    String blockId = afterBlock.getText().toString().trim();
                    if (blockId.isEmpty()) { toast("ID aktivitas sebelumnya wajib diisi."); return; }
                    int offsetMins = parseInt(afterOffset.getText().toString(), 10);
                    if (offsetMins < 0 || offsetMins > 240) { toast("Jeda setelah aktivitas harus 0–240 menit."); return; }
                    anchor = new JSONObject().put("type", "after_block").put("blockId", blockId)
                            .put("offsetMins", offsetMins);
                } else {
                    anchor = new JSONObject().put("type", "fixed").put("time", fixedValue);
                }
                JSONObject saved = existing == null ? new JSONObject() : new JSONObject(existing.toString());
                if (!saved.has("id")) saved.put("id", "native_" + UUID.randomUUID());
                saved.put("title", name).put("protocol", protocol.getText().toString().trim())
                        .put("anchor", anchor).put("windowMins", windowMinutes).put("durationMins", minutes)
                        .put("isFlexible", commute.isChecked() ? false : flexible.isChecked())
                        .put("includeHifzTarget", commute.isChecked() ? false : hifz.isChecked())
                        .put("isCommute", commute.isChecked())
                        .put("conflictPolicy", new String[]{"shift", "skip_if_busy", "strict"}[conflict.getSelectedItemPosition()])
                        .put("daysOfWeek", selectedDays).put("enabled", existing == null || existing.optBoolean("enabled", true));
                upsertRoutine(saved);
                dialog.dismiss();
            } catch (JSONException ignored) { toast("Aktivitas tidak dapat disimpan."); }
        }));
        dialog.show();
    }

    private Switch editorSwitch(String label, boolean checked) {
        Switch control = new Switch(this);
        control.setText(label);
        control.setTextColor(colorPrimary());
        control.setTextSize(14);
        control.setTypeface(appTypeface());
        control.setMinHeight(dp(48));
        styleSwitch(control);
        control.setChecked(checked);
        return control;
    }

    private void styleSwitch(Switch control) {
        int[][] states = {{android.R.attr.state_checked}, {}};
        control.setThumbTintList(new ColorStateList(states, new int[]{colorAccent(), darkTheme ? 0xFFB6C1D4 : 0xFFF4F6FA}));
        control.setTrackTintList(new ColorStateList(states, new int[]{colorAccent(), darkTheme ? 0xFF3A4458 : 0xFF9AA6BA}));
        control.setTrackTintMode(android.graphics.PorterDuff.Mode.SRC_IN);
    }

    private void upsertRoutine(JSONObject routine) throws JSONException {
        JSONArray old = state.optJSONArray("customRoutines");
        JSONArray updated = new JSONArray();
        boolean replaced = false;
        if (old != null) for (int i = 0; i < old.length(); i++) {
            JSONObject item = old.optJSONObject(i);
            if (item == null) continue;
            if (item.optString("id").equals(routine.optString("id"))) { updated.put(routine); replaced = true; }
            else updated.put(item);
        }
        if (!replaced) updated.put(routine);
        state.put("customRoutines", updated);
        syncActiveProfile();
        persistState();
        renderTab("schedule");
        toast("Aktivitas tersimpan.");
    }

    private void toggleRoutine(JSONObject routine) {
        try {
            routine.put("enabled", !routine.optBoolean("enabled", true));
            persistState();
            renderTab("schedule");
        } catch (JSONException ignored) { toast("Aktivitas tidak dapat diperbarui."); }
    }

    private void confirmDeleteRoutine(JSONObject routine) {
        new AlertDialog.Builder(this).setTitle("Hapus aktivitas?")
                .setMessage(routine.optString("title", "Aktivitas"))
                .setNegativeButton("Batal", null)
                .setPositiveButton("Hapus", (dialog, which) -> {
                    JSONArray source = state.optJSONArray("customRoutines");
                    JSONArray remaining = new JSONArray();
                    if (source != null) for (int i = 0; i < source.length(); i++) {
                        JSONObject item = source.optJSONObject(i);
                        if (item != null && !item.optString("id").equals(routine.optString("id"))) remaining.put(item);
                    }
                    try {
                        state.put("customRoutines", remaining);
                        syncActiveProfile();
                        persistState();
                        renderTab("schedule");
                    } catch (JSONException ignored) { toast("Aktivitas tidak dapat dihapus."); }
                }).show();
    }

    private void syncActiveProfile() {
        try {
            String id = state.optString("activeRoutineProfileId", "profile_1");
            JSONObject profiles = state.optJSONObject("routineProfiles");
            if (profiles == null) profiles = new JSONObject();
            JSONObject profile = profiles.optJSONObject(id);
            if (profile == null) profile = new JSONObject().put("name", id.replace('_', ' '));
            profile.put("customRoutines", state.optJSONArray("customRoutines") == null
                    ? new JSONArray() : state.optJSONArray("customRoutines"));
            profiles.put(id, profile);
            state.put("routineProfiles", profiles);
        } catch (JSONException ignored) { }
    }

    private void toggleSabaqFreeze() {
        try {
            JSONObject action = new JSONObject()
                    .put("type", "native_sabqi_freeze")
                    .put("prevFrozen", state.optBoolean("sabaqFrozen", false))
                    .put("prevFrozenForNextDay", state.optBoolean("sabaqFrozenForNextDay", false))
                    .put("prevSubStep", state.optInt("primarySubStep", 0))
                    .put("prevBlockTimers", copyJson(state.optJSONObject("blockTimers")));
            JSONArray undo = state.optJSONArray("undoStack");
            if (undo == null) undo = new JSONArray();
            undo.put(action);
            while (undo.length() > 30) undo = removeFirst(undo);
            state.put("undoStack", undo);

            boolean freeze = !state.optBoolean("sabaqFrozen", false);
            state.put("sabaqFrozen", freeze).put("sabaqFrozenForNextDay", false);
            JSONObject timers = state.optJSONObject("blockTimers");
            if (timers == null) timers = new JSONObject();
            if (freeze && state.optInt("primarySubStep", 0) >= 2) {
                state.put("primarySubStep", 1);
                timers.remove("primary_hifz_step_2");
            }
            JSONObject sabqiTimer = timers.optJSONObject("primary_hifz_step_1");
            if (sabqiTimer != null && sabqiTimer.optLong("startedAtMs", 0) > 0) {
                JSONObject plan = getNativeHifzPlan();
                JSONArray steps = plan == null ? null : plan.optJSONArray("steps");
                JSONObject sabqiStep = steps == null ? null : steps.optJSONObject(Math.min(1, steps.length() - 1));
                if (sabqiStep != null) sabqiTimer.put("durSecs", sabqiStep.optInt("durationMins", 30) * 60L);
            }
            state.put("blockTimers", timers);
            persistState();
            syncTimerScreenAwake();
            renderTab("hifz");
        } catch (JSONException ignored) { toast("Setelan Sabaq tidak dapat diubah."); }
    }

    private void setSabaqMode(String mode) {
        JSONObject sabaq = state.optJSONObject("sabaq");
        if (sabaq == null || !contains(SABAQ_MODE_KEYS, mode)) return;
        try {
            String oldMode = sabaq.optString("mode", "half_page");
            if (mode.equals(oldMode)) return;
            int oldSlot = sabaq.optInt("subSlot", 0);
            int page = sabaq.optInt("currentPage", sabaq.optInt("rangeStartPage", 1));
            if ("ayah".equals(mode)) {
                Map<Integer, Integer> counts = nativeAyahCounts();
                NativeQuranPageMap.AyahRef first = NativeQuranPageMap.pageEdge(page, false, counts);
                NativeQuranPageMap.AyahRef last = NativeQuranPageMap.pageEdge(page, true, counts);
                boolean laterHalf = "half_page".equals(oldMode) && oldSlot == 1
                        || "quarter_page".equals(oldMode) && oldSlot >= 2;
                NativeQuranPageMap.AyahRef selected = laterHalf ? last : first;
                sabaq.put("surahNum", selected.surah).put("startAyah", selected.ayah)
                        .put("currentPage", NativeQuranPageMap.estimatePage(selected.surah, selected.ayah, counts))
                        .put("subSlot", 0);
            } else if ("ayah".equals(oldMode)) {
                int newPage = NativeQuranPageMap.estimatePage(sabaq.optInt("surahNum", 1),
                        sabaq.optInt("startAyah", 1), nativeAyahCounts());
                sabaq.put("currentPage", Math.max(sabaq.optInt("rangeStartPage", 1),
                        Math.min(sabaq.optInt("rangeEndPage", 604), newPage))).put("subSlot", 0);
            } else if ("quarter_page".equals(oldMode) && "half_page".equals(mode)) {
                sabaq.put("subSlot", oldSlot / 2);
            } else if ("half_page".equals(oldMode) && "quarter_page".equals(mode)) {
                sabaq.put("subSlot", oldSlot * 2);
            } else {
                sabaq.put("subSlot", 0);
            }
            sabaq.put("mode", mode);
            if (!sabaq.optBoolean("sabaqCompleted") && !sabaq.optBoolean("targetCompleted")) {
                rebuildSabqiForSabaqEdit(sabaq);
            }
            persistState();
            renderTab("hifz");
        } catch (JSONException ignored) { toast("Mode target tidak dapat disimpan."); }
    }

    private String[] getSabaqPresetLabels() {
        String[] labels = new String[31];
        labels[0] = "Seluruh mushaf · halaman 1–604";
        for (int i = 0; i < 30; i++) labels[i + 1] = "Juz " + (i + 1) + " · halaman "
                + JUZ_START_PAGE[i] + "–" + JUZ_END_PAGE[i];
        return labels;
    }

    private void applySabaqPreset(int selected) {
        JSONObject sabaq = state.optJSONObject("sabaq");
        if (sabaq == null || selected < 0 || selected > 30) return;
        try {
            int startPage = selected == 0 ? 1 : JUZ_START_PAGE[selected - 1];
            int endPage = selected == 0 ? 604 : JUZ_END_PAGE[selected - 1];
            int startSurah = selected == 0 ? 1 : JUZ_START_SURAH[selected - 1];
            int startAyah = selected == 0 ? 1 : JUZ_START_AYAH[selected - 1];
            int endSurah = selected == 0 ? 114 : JUZ_END_SURAH[selected - 1];
            int endAyah = selected == 0 ? 6 : JUZ_END_AYAH[selected - 1];
            sabaq.put("targetJuz", selected).put("rangeStartPage", startPage).put("rangeEndPage", endPage)
                    .put("currentPage", startPage).put("surahNum", startSurah).put("startAyah", startAyah)
                    .put("targetStartSurahNum", startSurah).put("targetStartAyah", startAyah)
                    .put("targetEndSurahNum", endSurah).put("targetEndAyah", endAyah).put("subSlot", 0)
                    .put("sabaqCompleted", false).put("targetCompleted", false);
            rebuildSabqiForSabaqEdit(sabaq);
            persistState();
            renderTab("hifz");
        } catch (JSONException ignored) { toast("Preset target tidak dapat disimpan."); }
    }

    private String currentSabaqUnitLabel(JSONObject sabaq) {
        int page = sabaq.optInt("currentPage", 1);
        int slot = sabaq.optInt("subSlot", 0);
        switch (sabaq.optString("mode", "half_page")) {
            case "quarter_page": return "Halaman " + page + " (" + new String[]{"Baris 1–4", "Baris 5–8", "Baris 9–12", "Baris 13–15"}[Math.max(0, Math.min(3, slot))] + ")";
            case "half_page": return "Halaman " + page + " " + (slot == 0 ? "Atas (Baris 1–8)" : "Bawah (Baris 9–15)");
            case "one_page": return "Halaman " + page;
            case "multi_page": return "Halaman " + page + "–" + Math.min(sabaq.optInt("rangeEndPage", 604), page + Math.max(1, sabaq.optInt("pagesPerDay", 2)) - 1);
            case "ayah": return currentAyahUnitLabel(sabaq);
            default: return "Halaman " + page;
        }
    }

    private void rebuildSabqiForSabaqEdit(JSONObject sabaq) throws JSONException {
        state.put("sabqiHistory", NativeSabqiHistory.backfill(sabaq, nativeAyahCounts(), this::currentSabaqUnitLabel));
        state.put("conservedPartialSpans", new JSONObject());
        state.put("hifzPhase", resolveNativeHifzPhase(sabaq));
    }

    private void repairSabqiHistory() {
        JSONObject sabaq = state.optJSONObject("sabaq");
        if (!NativeSabqiHistory.isOutsideTarget(state.optJSONArray("sabqiHistory"), sabaq)) return;
        try {
            state.put("sabqiHistory", NativeSabqiHistory.backfill(sabaq, nativeAyahCounts(), this::currentSabaqUnitLabel));
            state.put("hifzPhase", resolveNativeHifzPhase(sabaq));
            stateStore.save(state);
        } catch (JSONException ignored) { toast("Daftar Sabqi tidak dapat diselaraskan."); }
    }

    private String currentSabaqUnitFullLabel(JSONObject sabaq) {
        if (sabaq.optBoolean("sabaqCompleted", false)) {
            return "Khatam 30 Juz selesai · halaman 1–604 · mode pemeliharaan";
        }
        if (sabaq.optBoolean("targetCompleted", false)) {
            int juz = sabaq.optInt("targetJuz", 0);
            return juz > 0 ? "Target Juz " + juz + " selesai"
                    : "Target halaman " + sabaq.optInt("rangeStartPage", 1) + "–"
                    + sabaq.optInt("rangeEndPage", 604) + " selesai";
        }
        String prefix = "";
        int juz = sabaq.optInt("targetJuz", 0);
        if (juz >= 1 && juz <= 30) {
            prefix = "Juz " + juz + " (" + sabaqAyahReference(JUZ_START_SURAH[juz - 1], JUZ_START_AYAH[juz - 1])
                    + " → " + sabaqAyahReference(JUZ_END_SURAH[juz - 1], JUZ_END_AYAH[juz - 1]) + ") · ";
        } else if (sabaq.optInt("rangeStartPage", 1) != 1 || sabaq.optInt("rangeEndPage", 604) != 604) {
            prefix = "Target halaman " + sabaq.optInt("rangeStartPage", 1) + "–"
                    + sabaq.optInt("rangeEndPage", 604) + " · ";
        }
        return prefix + currentSabaqUnitLabel(sabaq);
    }

    private String sabaqAyahReference(int surah, int ayah) {
        return QuranSurahNames.reference(surah, ayah);
    }

    private String currentAyahUnitLabel(JSONObject sabaq) {
        int surah = Math.max(1, sabaq.optInt("surahNum", 1));
        int ayah = Math.max(1, sabaq.optInt("startAyah", 1));
        int endSurah = Math.max(surah, sabaq.optInt("targetEndSurahNum", 114));
        int endAyah = Math.max(1, sabaq.optInt("targetEndAyah", 6));
        int remaining = Math.max(1, sabaq.optInt("ayahsPerDay", 10));
        List<String> segments = new ArrayList<>();
        int total = 0;
        while (remaining > 0 && surah <= endSurah) {
            List<String> verses = surahAyahs.get(surah);
            int surahEnd = surah == endSurah ? Math.min(verses == null ? endAyah : verses.size(), endAyah)
                    : (verses == null ? 0 : verses.size());
            int take = Math.min(remaining, Math.max(0, surahEnd - ayah + 1));
            if (take <= 0) break;
            int segmentEnd = ayah + take - 1;
            segments.add(QuranSurahNames.reference(surah, ayah) + (segmentEnd == ayah ? "" : "–" + segmentEnd));
            total += take;
            remaining -= take;
            if (segmentEnd < surahEnd) break;
            if (surah == endSurah) break;
            surah++;
            ayah = 1;
        }
        if (segments.isEmpty()) return "Target ayat selesai";
        return String.join(" + ", segments) + " (" + total + " Ayat)";
    }

    private Map<Integer, Integer> nativeAyahCounts() {
        Map<Integer, Integer> result = NativeQuranPageMap.canonicalAyahCounts();
        for (int i = 1; i <= 114; i++) {
            List<String> verses = surahAyahs.get(i);
            if (verses != null && !verses.isEmpty()) result.put(i, verses.size());
        }
        return result;
    }

    private void showSabaqRangeDialog() {
        JSONObject sabaq = state.optJSONObject("sabaq");
        if (sabaq == null) return;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), dp(4), dp(22), dp(4));
        EditText first = input(String.valueOf(sabaq.optInt("rangeStartPage", 1)), "Halaman awal", InputType.TYPE_CLASS_NUMBER);
        EditText last = input(String.valueOf(sabaq.optInt("rangeEndPage", 604)), "Halaman akhir", InputType.TYPE_CLASS_NUMBER);
        form.addView(labeled("Halaman awal (1–604)", first));
        addGap(form, 8);
        form.addView(labeled("Halaman akhir (1–604)", last));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Rentang target")
                .setView(form).setNegativeButton("Batal", null).setPositiveButton("Simpan", null).create();
        dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            int start = parseInt(first.getText().toString(), -1);
            int end = parseInt(last.getText().toString(), -1);
            if (start < 1 || end < start || end > 604) { toast("Rentang harus dari halaman 1–604."); return; }
            if (start == sabaq.optInt("rangeStartPage", 1) && end == sabaq.optInt("rangeEndPage", 604)) {
                dialog.dismiss();
                return;
            }
            try {
                NativeQuranPageMap.AyahRef startRef = NativeQuranPageMap.pageEdge(start, false, nativeAyahCounts());
                NativeQuranPageMap.AyahRef endRef = NativeQuranPageMap.pageEdge(end, true, nativeAyahCounts());
                sabaq.put("rangeStartPage", start).put("rangeEndPage", end)
                        .put("currentPage", start).put("surahNum", startRef.surah).put("startAyah", startRef.ayah)
                        .put("targetStartSurahNum", startRef.surah).put("targetStartAyah", startRef.ayah)
                        .put("targetEndSurahNum", endRef.surah).put("targetEndAyah", endRef.ayah)
                        .put("subSlot", 0).put("targetJuz", 0)
                        .put("sabaqCompleted", false).put("targetCompleted", false);
                rebuildSabqiForSabaqEdit(sabaq);
                persistState();
                dialog.dismiss();
                renderTab("hifz");
            } catch (JSONException ignored) { toast("Rentang tidak dapat disimpan."); }
        }));
        dialog.show();
    }

    private void showPagesPerDayDialog() {
        JSONObject sabaq = state.optJSONObject("sabaq");
        if (sabaq == null) return;
        EditText pages = input(String.valueOf(sabaq.optInt("pagesPerDay", 2)), "Halaman per unit",
                InputType.TYPE_CLASS_NUMBER);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), dp(4), dp(22), dp(4));
        form.addView(labeled("Halaman per hari (1–20)", pages));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Porsi Sabaq harian")
                .setView(form).setNegativeButton("Batal", null).setPositiveButton("Simpan", null).create();
        dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            int value = parseInt(pages.getText().toString(), -1);
            if (value < 1 || value > 20) { toast("Porsi harus 1–20 halaman."); return; }
            if (value == sabaq.optInt("pagesPerDay", 2)) { dialog.dismiss(); return; }
            try {
                sabaq.put("pagesPerDay", value);
                if (!sabaq.optBoolean("sabaqCompleted") && !sabaq.optBoolean("targetCompleted")) {
                    rebuildSabqiForSabaqEdit(sabaq);
                }
                persistState();
                dialog.dismiss();
                renderTab("hifz");
            } catch (JSONException ignored) { toast("Porsi tidak dapat disimpan."); }
        }));
        dialog.show();
    }

    private void showAyahTargetDialog() {
        JSONObject sabaq = state.optJSONObject("sabaq");
        if (sabaq == null) return;
        String[] labels = new String[114];
        String[] arabicLabels = new String[114];
        for (int i = 1; i <= 114; i++) {
            labels[i - 1] = i + ". " + quranName(i);
            arabicLabels[i - 1] = surahArabicNames.getOrDefault(i, "");
        }
        Spinner startSurah = spinner(labels, arabicLabels);
        Spinner endSurah = spinner(labels, arabicLabels);
        startSurah.setSelection(Math.max(0, Math.min(113, sabaq.optInt("targetStartSurahNum", sabaq.optInt("surahNum", 1)) - 1)));
        endSurah.setSelection(Math.max(0, Math.min(113, sabaq.optInt("targetEndSurahNum", 114) - 1)));
        EditText startAyah = input(String.valueOf(sabaq.optInt("targetStartAyah", sabaq.optInt("startAyah", 1))),
                "Ayat awal", InputType.TYPE_CLASS_NUMBER);
        EditText endAyah = input(String.valueOf(sabaq.optInt("targetEndAyah", 6)), "Ayat akhir", InputType.TYPE_CLASS_NUMBER);
        EditText perDay = input(String.valueOf(sabaq.optInt("ayahsPerDay", 10)), "Ayat per hari", InputType.TYPE_CLASS_NUMBER);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), dp(4), dp(22), dp(4));
        TextView startRange = text("", 11, colorMuted(), false);
        TextView endRange = text("", 11, colorMuted(), false);
        Runnable syncRanges = () -> {
            int a = startSurah.getSelectedItemPosition() + 1;
            int b = endSurah.getSelectedItemPosition() + 1;
            int ca = nativeAyahCounts().getOrDefault(a, 0);
            int cb = nativeAyahCounts().getOrDefault(b, 0);
            startRange.setText(quranName(a) + " memiliki ayat 1–" + ca);
            endRange.setText(quranName(b) + " memiliki ayat 1–" + cb);
            startAyah.setHint("1–" + ca);
            endAyah.setHint("1–" + cb);
        };
        startSurah.setOnItemSelectedListener(simpleItemListener(position -> syncRanges.run()));
        endSurah.setOnItemSelectedListener(simpleItemListener(position -> syncRanges.run()));
        syncRanges.run();
        form.addView(labeled("Surah awal", startSurah));
        addGap(form, 6);
        form.addView(labeled("Ayat awal", startAyah));
        addGap(form, 4);
        form.addView(startRange);
        addGap(form, 10);
        form.addView(labeled("Surah akhir", endSurah));
        addGap(form, 6);
        form.addView(labeled("Ayat akhir", endAyah));
        addGap(form, 4);
        form.addView(endRange);
        addGap(form, 6);
        form.addView(labeled("Porsi per hari (1–150 ayat)", perDay));
        ScrollView scroll = scrollView();
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Target hafalan ayat")
                .setView(scroll).setNegativeButton("Batal", null).setPositiveButton("Simpan", null).create();
        dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            int firstSurah = startSurah.getSelectedItemPosition() + 1;
            int firstAyah = parseInt(startAyah.getText().toString(), -1);
            int lastSurah = endSurah.getSelectedItemPosition() + 1;
            int lastAyah = parseInt(endAyah.getText().toString(), -1);
            int countPerDay = parseInt(perDay.getText().toString(), -1);
            Map<Integer, Integer> counts = nativeAyahCounts();
            if (firstAyah < 1 || firstAyah > counts.getOrDefault(firstSurah, 0)) {
                startAyah.setError(quranName(firstSurah) + " hanya memiliki ayat 1–" + counts.getOrDefault(firstSurah, 0) + ".");
                return;
            }
            if (lastAyah < 1 || lastAyah > counts.getOrDefault(lastSurah, 0)) {
                endAyah.setError(quranName(lastSurah) + " hanya memiliki ayat 1–" + counts.getOrDefault(lastSurah, 0) + ".");
                return;
            }
            if (firstSurah > lastSurah || (firstSurah == lastSurah && firstAyah > lastAyah)) {
                toast("Akhir target harus setelah " + QuranSurahNames.reference(firstSurah, firstAyah) + "."); return;
            }
            if (countPerDay < 1 || countPerDay > 150) { toast("Porsi harus 1–150 ayat per hari."); return; }
            int firstPage = NativeQuranPageMap.estimatePage(firstSurah, firstAyah, counts);
            int lastPage = NativeQuranPageMap.estimatePage(lastSurah, lastAyah, counts);
            try {
                boolean rangeChanged = firstSurah != sabaq.optInt("targetStartSurahNum", 1)
                        || firstAyah != sabaq.optInt("targetStartAyah", 1)
                        || lastSurah != sabaq.optInt("targetEndSurahNum", 114)
                        || lastAyah != sabaq.optInt("targetEndAyah", 6);
                boolean paceChanged = countPerDay != sabaq.optInt("ayahsPerDay", 10);
                if (!rangeChanged && !paceChanged) { dialog.dismiss(); return; }
                if (rangeChanged) {
                    sabaq.put("targetJuz", 0).put("rangeStartPage", firstPage).put("rangeEndPage", lastPage)
                            .put("currentPage", firstPage).put("surahNum", firstSurah).put("startAyah", firstAyah)
                            .put("targetStartSurahNum", firstSurah).put("targetStartAyah", firstAyah)
                            .put("targetEndSurahNum", lastSurah).put("targetEndAyah", lastAyah)
                            .put("subSlot", 0).put("sabaqCompleted", false).put("targetCompleted", false);
                }
                sabaq.put("ayahsPerDay", countPerDay);
                if (!sabaq.optBoolean("sabaqCompleted") && !sabaq.optBoolean("targetCompleted")) {
                    rebuildSabqiForSabaqEdit(sabaq);
                }
                persistState();
                dialog.dismiss();
                renderTab("hifz");
            } catch (JSONException ignored) { toast("Target ayat tidak dapat disimpan."); }
        }));
        dialog.show();
    }

    private void showPriorJuzDialog() {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(20), dp(4), dp(20), dp(4));
        CheckBox[] checks = new CheckBox[30];
        JSONArray prior = state.optJSONArray("priorJuzList");
        for (int rowIndex = 0; rowIndex < 5; rowIndex++) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            for (int column = 0; column < 6; column++) {
                int juz = rowIndex * 6 + column + 1;
                CheckBox check = new CheckBox(this);
                check.setText(String.valueOf(juz));
                check.setTextColor(colorPrimary());
                check.setTextSize(12);
                check.setChecked(contains(prior, juz));
                checks[juz - 1] = check;
                row.addView(check, new LinearLayout.LayoutParams(0, WRAP, 1f));
            }
            grid.addView(row);
        }
        ScrollView scroll = scrollView();
        scroll.addView(grid);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Juz yang sudah dihafal")
                .setView(scroll).setNegativeButton("Batal", null).setPositiveButton("Simpan", null).create();
        dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            JSONArray selected = new JSONArray();
            for (int i = 0; i < checks.length; i++) if (checks[i].isChecked()) selected.put(i + 1);
            stateSafePut("priorJuzList", selected);
            JSONObject sabaq = state.optJSONObject("sabaq");
            if (sabaq != null) stateSafePut("hifzPhase", resolveNativeHifzPhase(sabaq));
            persistState();
            dialog.dismiss();
            renderTab("hifz");
        }));
        dialog.show();
    }

    private void advanceAyahUnit(JSONObject sabaq, int endPage) {
        int surah = Math.max(1, sabaq.optInt("surahNum", 1));
        int ayah = Math.max(1, sabaq.optInt("startAyah", 1));
        int endSurah = Math.max(surah, sabaq.optInt("targetEndSurahNum", 114));
        int endAyah = Math.max(1, sabaq.optInt("targetEndAyah", 6));
        int remaining = Math.max(1, sabaq.optInt("ayahsPerDay", 10));
        int nextSurah = surah;
        int nextAyah = ayah;
        boolean complete = false;
        while (remaining > 0 && nextSurah <= endSurah) {
            List<String> verses = surahAyahs.get(nextSurah);
            int surahEnd = nextSurah == endSurah ? Math.min(verses == null ? endAyah : verses.size(), endAyah)
                    : (verses == null ? 0 : verses.size());
            int available = Math.max(0, surahEnd - nextAyah + 1);
            if (available == 0) { complete = true; break; }
            int take = Math.min(remaining, available);
            remaining -= take;
            nextAyah += take;
            if (nextAyah > surahEnd) {
                if (nextSurah == endSurah) { complete = true; break; }
                nextSurah++;
                nextAyah = 1;
            } else break;
        }
        int approxPage = NativeQuranPageMap.estimatePage(nextSurah, nextAyah, nativeAyahCounts());
        try {
            sabaq.put("surahNum", nextSurah).put("startAyah", nextAyah).put("currentPage", approxPage)
                    .put("targetCompleted", complete && !(sabaq.optInt("rangeStartPage", 1) == 1 && endPage == 604))
                    .put("sabaqCompleted", complete && sabaq.optInt("rangeStartPage", 1) == 1 && endPage == 604);
        } catch (JSONException ignored) { }
    }

    private int resolveNativeHifzPhase(JSONObject sabaq) {
        try { return NativeHifzPlan.build(state, false).optInt("phase", 3); }
        catch (JSONException ignored) { return 3; }
    }

    private void moveSabaqPage(int delta) {
        JSONObject sabaq = state.optJSONObject("sabaq");
        if (sabaq == null) return;
        int first = sabaq.optInt("rangeStartPage", 1);
        int last = sabaq.optInt("rangeEndPage", 604);
        int current = sabaq.optInt("currentPage", first);
        int next = Math.max(first, Math.min(last, current + delta));
        if (next == current) return;
        try {
            sabaq.put("currentPage", next);
            sabaq.put("subSlot", 0);
            sabaq.put("sabaqCompleted", false);
            sabaq.put("targetCompleted", false);
            rebuildSabqiForSabaqEdit(sabaq);
            persistState();
            renderTab("hifz");
        } catch (JSONException ignored) { toast("Target hafalan tidak dapat diperbarui."); }
    }

    private void saveManualPrayerTime(String key, String value) {
        if (!validTime(value)) { toast("Gunakan format 24 jam HH:MM."); return; }
        JSONObject manual = state.optJSONObject("manualPrayerTimes");
        if (manual == null) manual = new JSONObject();
        try {
            manual.put(key, value);
            state.put("manualPrayerTimes", manual);
            persistState();
            if (prayerLineView != null) prayerLineView.setText(buildPrayerLine());
        } catch (JSONException ignored) { toast("Waktu shalat tidak dapat disimpan."); }
    }

    private void requestCurrentLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
            return;
        }
        if (locationSearchInProgress) return;
        try {
            LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (manager == null) { toast("Layanan lokasi tidak tersedia."); return; }
            beginLocationSearch();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                locationCancellationSignal = new CancellationSignal();
                manager.getCurrentLocation(LocationManager.NETWORK_PROVIDER, locationCancellationSignal,
                        getMainExecutor(), this::finishLocationSearch);
                return;
            }
            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_COARSE);
            criteria.setPowerRequirement(Criteria.POWER_LOW);
            String provider = manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                    ? LocationManager.NETWORK_PROVIDER : manager.getBestProvider(criteria, true);
            if (provider == null) { finishLocationSearch(null); return; }
            activeLocationListener = new LocationListener() {
                @Override public void onLocationChanged(Location location) { finishLocationSearch(location); }
                @Override public void onProviderDisabled(String providerName) { }
                @Override public void onProviderEnabled(String providerName) { }
                @Override public void onStatusChanged(String providerName, int status, android.os.Bundle extras) { }
            };
            manager.requestSingleUpdate(provider, activeLocationListener, Looper.getMainLooper());
        } catch (SecurityException | IllegalArgumentException exception) {
            finishLocationSearch(null);
        }
    }

    private void beginLocationSearch() {
        locationSearchInProgress = true;
        handler.removeCallbacks(locationSearchTimeout);
        handler.postDelayed(locationSearchTimeout, 15000L);
        toast("Mencari lokasi perangkat…");
    }

    private void finishLocationSearch(Location location) {
        if (!locationSearchInProgress) return;
        locationSearchInProgress = false;
        handler.removeCallbacks(locationSearchTimeout);
        if (locationCancellationSignal != null) {
            locationCancellationSignal.cancel();
            locationCancellationSignal = null;
        }
        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (manager != null && activeLocationListener != null) {
            try { manager.removeUpdates(activeLocationListener); } catch (SecurityException ignored) { }
        }
        activeLocationListener = null;
        if (location == null) {
            toast("Lokasi tidak tersedia. Coba lagi atau masukkan koordinat manual.");
            return;
        }
        try {
            JSONObject gps = state.optJSONObject("gpsLocation");
            if (gps == null) gps = new JSONObject();
            IndonesianCities.City near = IndonesianCities.nearest(location.getLatitude(), location.getLongitude(), 40);
            gps.put("lat", location.getLatitude()).put("lon", location.getLongitude())
                    .put("label", near == null ? "Lokasi perangkat" : "Dekat " + near.name).put("hasFix", true)
                    .put("tzOffsetHours", TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3600000.0);
            state.put("gpsLocation", gps);
            persistState();
            renderTab("prayer");
            toast("Lokasi perangkat diperbarui.");
        } catch (JSONException exception) { toast("Lokasi tidak dapat disimpan."); }
    }

    private void saveManualCoordinates(EditText latitude, EditText longitude) {
        double lat = parseCoordinate(latitude == null ? "" : latitude.getText().toString());
        double lon = parseCoordinate(longitude == null ? "" : longitude.getText().toString());
        if (!Double.isFinite(lat) || Math.abs(lat) > 90 || !Double.isFinite(lon) || Math.abs(lon) > 180) {
            toast("Koordinat tidak valid. Lintang harus −90..90 dan bujur −180..180.");
            return;
        }
        applyPresetCoordinates(lat, lon, "Koordinat manual");
    }

    private void applyPresetCoordinates(double latitude, double longitude, String label) {
        if (!Double.isFinite(latitude) || Math.abs(latitude) > 90
                || !Double.isFinite(longitude) || Math.abs(longitude) > 180) {
            toast("Koordinat tidak valid.");
            return;
        }
        JSONObject gps = state.optJSONObject("gpsLocation");
        if (gps == null) gps = new JSONObject();
        try {
            gps.put("lat", latitude).put("lon", longitude).put("label", label).put("hasFix", false)
                    .put("tzOffsetHours", TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3600000.0);
            state.put("gpsLocation", gps);
            persistState();
            renderTab("prayer");
            toast("Koordinat shalat diterapkan.");
        } catch (JSONException exception) { toast("Koordinat tidak dapat disimpan."); }
    }

    private double parseCoordinate(String value) {
        if (value == null) return Double.NaN;
        try { return Double.parseDouble(value.trim().replace(',', '.')); }
        catch (NumberFormatException exception) { return Double.NaN; }
    }

    private void startOrResumeTimer() {
        JSONObject timers = state.optJSONObject("blockTimers");
        if (timers == null) timers = new JSONObject();
        String key = timerKey();
        JSONObject record = timers.optJSONObject(key);
        try {
            if (record == null || record.optLong("durSecs", 0) <= 0) {
                record = new JSONObject().put("durSecs", currentStepDurationSeconds());
                timers.put(key, record);
            }
            long startedAt = System.currentTimeMillis();
            record.put("startedAtMs", startedAt);
            if (Math.max(0, state.optInt("primarySubStep", 0)) == 0
                    && (timers.optJSONObject("primary_hifz_window") == null
                    || timers.optJSONObject("primary_hifz_window").optLong("startedAtMs", 0) <= 0)) {
                timers.put("primary_hifz_window", new JSONObject().put("startedAtMs", startedAt).put("durSecs", 90 * 60));
            }
            state.put("blockTimers", timers);
        } catch (JSONException ignored) { toast("Timer tidak dapat dimulai."); return; }
        persistState();
        publishCurrentNotification();
        syncTimerScreenAwake();
    }

    private void pauseTimer() {
        JSONObject timers = state.optJSONObject("blockTimers");
        JSONObject record = timers == null ? null : timers.optJSONObject(timerKey());
        if (record == null || !timerIsRunning()) return;
        try {
            record.put("durSecs", timerRemaining());
            record.put("startedAtMs", 0);
            state.put("blockTimers", timers);
            persistState();
            publishCurrentNotification();
            syncTimerScreenAwake();
        } catch (JSONException ignored) { toast("Timer tidak dapat dijeda."); }
    }

    private void completeCurrentStep() {
        JSONObject plan = getNativeHifzPlan();
        int totalSteps = Math.max(1, plan == null ? 3 : plan.optInt("totalSteps", 3));
        int step = Math.max(0, Math.min(totalSteps - 1, state.optInt("primarySubStep", 0)));
        JSONObject timers = state.optJSONObject("blockTimers");
        try {
            if (timers != null) {
                timers.remove(timerKey());
                if (step >= totalSteps - 1) timers.remove("primary_hifz_window");
                state.put("blockTimers", timers);
            }
            if (step < totalSteps - 1) {
                state.put("primarySubStep", step + 1);
            } else {
                JSONArray completed = state.optJSONArray("completedBlocksToday");
                if (completed == null) completed = new JSONArray();
                if (!contains(completed, "primary_hifz")) completed.put("primary_hifz");
                state.put("completedBlocksToday", completed);
            }
            persistState();
            publishCurrentNotification();
            syncTimerScreenAwake();
        } catch (JSONException ignored) { toast("Langkah tidak dapat diselesaikan."); }
    }

    private String timerKey() {
        JSONObject plan = getNativeHifzPlan();
        int total = Math.max(1, plan == null ? 3 : plan.optInt("totalSteps", 3));
        return "primary_hifz_step_" + Math.max(0, Math.min(total - 1, state.optInt("primarySubStep", 0)));
    }

    private boolean timerIsRunning() {
        JSONObject timers = state.optJSONObject("blockTimers");
        JSONObject record = timers == null ? null : timers.optJSONObject(timerKey());
        return record != null && record.optLong("startedAtMs", 0) > 0 && timerRemaining() > 0;
    }

    private boolean timerHasRecord() {
        JSONObject timers = state.optJSONObject("blockTimers");
        return timers != null && timers.optJSONObject(timerKey()) != null;
    }

    private long timerRemaining() {
        JSONObject timers = state.optJSONObject("blockTimers");
        JSONObject record = timers == null ? null : timers.optJSONObject(timerKey());
        if (record == null) return currentStepDurationSeconds();
        return remainingTimerSeconds(record, HIFZ_STEP_SECONDS, System.currentTimeMillis());
    }

    private boolean hasAgendaTimer(String blockId) {
        JSONObject timers = state.optJSONObject("blockTimers");
        JSONObject record = timers == null ? null : timers.optJSONObject(blockId);
        return record != null && record.optLong("durSecs", 0) > 0;
    }

    private boolean agendaTimerRunning(String blockId) {
        JSONObject timers = state.optJSONObject("blockTimers");
        JSONObject record = timers == null ? null : timers.optJSONObject(blockId);
        return record != null && record.optLong("startedAtMs", 0) > 0 && agendaTimerRemaining(blockId) > 0;
    }

    private long agendaTimerRemaining(String blockId) {
        JSONObject timers = state.optJSONObject("blockTimers");
        JSONObject record = timers == null ? null : timers.optJSONObject(blockId);
        if (record == null) return 0;
        return remainingTimerSeconds(record, 0, System.currentTimeMillis());
    }

    static long remainingTimerSeconds(JSONObject record, long defaultDuration, long now) {
        long start = record.optLong("startedAtMs", 0);
        long elapsed = start <= 0 ? 0 : Math.max(0, (now - start) / 1000);
        return Math.max(0, record.optLong("durSecs", defaultDuration) - elapsed);
    }

    private void startAgendaTimer(JSONObject block) {
        String id = block == null ? "" : block.optString("id", "");
        if (id.isEmpty() || "primary_hifz".equals(id)) return;
        JSONObject timers = state.optJSONObject("blockTimers");
        if (timers == null) timers = new JSONObject();
        JSONObject record = timers.optJSONObject(id);
        try {
            long duration = record == null ? 0 : record.optLong("durSecs", 0);
            if (duration <= 0) duration = Math.max(60, block.optInt("effectiveDurationMins",
                    block.optInt("durationMins", 30)) * 60L);
            record = record == null ? new JSONObject() : record;
            record.put("durSecs", duration).put("startedAtMs", System.currentTimeMillis());
            timers.put(id, record);
            state.put("blockTimers", timers);
            persistState();
            publishCurrentNotification();
            syncTimerScreenAwake();
        } catch (JSONException ignored) { toast("Timer aktivitas tidak dapat dimulai."); }
    }

    private void pauseAgendaTimer(String blockId) {
        JSONObject timers = state.optJSONObject("blockTimers");
        JSONObject record = timers == null ? null : timers.optJSONObject(blockId);
        if (record == null || !agendaTimerRunning(blockId)) return;
        try {
            record.put("durSecs", agendaTimerRemaining(blockId)).put("startedAtMs", 0);
            state.put("blockTimers", timers);
            persistState();
            publishCurrentNotification();
            syncTimerScreenAwake();
        } catch (JSONException ignored) { toast("Timer aktivitas tidak dapat dijeda."); }
    }

    private void completeAgendaBlock(String blockId) {
        if (blockId == null || blockId.isEmpty()) return;
        try {
            JSONArray completed = state.optJSONArray("completedBlocksToday");
            if (completed == null) completed = new JSONArray();
            if (!contains(completed, blockId)) completed.put(blockId);
            state.put("completedBlocksToday", completed);
            JSONObject timers = state.optJSONObject("blockTimers");
            if (timers != null) timers.remove(blockId);
            persistState();
            publishCurrentNotification();
            renderTab("schedule");
            syncTimerScreenAwake();
        } catch (JSONException ignored) { toast("Aktivitas tidak dapat ditandai selesai."); }
    }

    private void undoAgendaBlock(String blockId) {
        JSONArray completed = state.optJSONArray("completedBlocksToday");
        if (completed == null) return;
        try {
            JSONArray updated = new JSONArray();
            for (int i = 0; i < completed.length(); i++) if (!blockId.equals(completed.optString(i))) updated.put(completed.get(i));
            state.put("completedBlocksToday", updated);
            persistState();
            renderTab("schedule");
            syncTimerScreenAwake();
        } catch (JSONException ignored) { toast("Status aktivitas tidak dapat dipulihkan."); }
    }

    private void syncTimerScreenAwake() {
        if (!appInitialized) return;
        if (timerIsRunning() || getActiveAgendaTimer() != null) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void playTimerClockChime(boolean forceTest) {
        if (!forceTest && !state.optBoolean("timerAlarmEnabled", true)) return;
        try {
            if (timerToneGenerator != null) {
                timerToneGenerator.stopTone();
                timerToneGenerator.release();
            }
            ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_ALARM, 80);
            timerToneGenerator = tone;
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 220);
            handler.postDelayed(() -> {
                if (timerToneGenerator == tone) tone.startTone(ToneGenerator.TONE_PROP_BEEP, 220);
            }, 300L);
            handler.postDelayed(() -> {
                if (timerToneGenerator == tone) tone.startTone(ToneGenerator.TONE_PROP_ACK, 560);
            }, 640L);
            handler.postDelayed(() -> {
                if (timerToneGenerator == tone) {
                    tone.release();
                    timerToneGenerator = null;
                }
            }, 1400L);
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = {0, 180, 100, 180, 120, 320};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
                } else {
                    vibrator.vibrate(pattern, -1);
                }
            }
        } catch (RuntimeException ignored) { toast("Lonceng jam tidak dapat diputar."); }
    }

    private void refreshTimerText() {
        if (timerView != null) timerView.setText(formatSeconds(timerRemaining()));
        if (timerLabelView != null) timerLabelView.setText(timerIsRunning() ? "SESI BERJALAN" : "WAKTU LANGKAH");
        if (body != null) refreshAgendaTimerViews(body);
    }

    private void refreshAgendaTimerViews(View parent) {
        if (parent instanceof TextView && parent.getTag() instanceof String) {
            String tag = (String) parent.getTag();
            if (tag.startsWith("agenda_timer_")) ((TextView) parent).setText(formatSeconds(agendaTimerRemaining(tag.substring(13))));
        }
        if (parent instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) parent;
            for (int i = 0; i < group.getChildCount(); i++) refreshAgendaTimerViews(group.getChildAt(i));
        }
    }

    private String expireTimerIfNeeded() {
        JSONObject timers = state.optJSONObject("blockTimers");
        if (timers == null || timers.length() == 0) return "";
        boolean hifzExpired = false;
        JSONObject record = timers.optJSONObject("primary_hifz_step_" + Math.max(0, state.optInt("primarySubStep", 0)));
        if (record != null && record.optLong("startedAtMs", 0) > 0
                && remainingTimerSeconds(record, HIFZ_STEP_SECONDS, System.currentTimeMillis()) == 0) {
            try {
                record.put("startedAtMs", 0);
                record.put("durSecs", 0);
                record.put("finishedAtMs", System.currentTimeMillis());
                state.put("blockTimers", timers);
                persistState();
                publishCurrentNotification();
                syncTimerScreenAwake();
                playTimerClockChime(false);
                toast("Waktu langkah selesai.");
                hifzExpired = true;
            } catch (JSONException ignored) { }
        }
        boolean agendaExpired = expireAgendaTimersIfNeeded(timers);
        if ("schedule".equals(currentTab) && (agendaExpired || hifzExpired)) return "schedule";
        return hifzExpired ? "home" : "";
    }

    private boolean expireAgendaTimersIfNeeded(JSONObject timers) {
        boolean changed = false;
        long now = System.currentTimeMillis();
        for (String id : timersKeys(timers)) {
            if (id.startsWith("primary_hifz_") || "primary_hifz_window".equals(id)) continue;
            JSONObject record = timers.optJSONObject(id);
            if (record == null || record.optLong("startedAtMs", 0) <= 0 || agendaTimerRemaining(id) > 0) continue;
            try {
                record.put("startedAtMs", 0).put("durSecs", 0).put("finishedAtMs", now);
                changed = true;
            } catch (JSONException ignored) { }
        }
        if (changed) {
            persistState();
            publishCurrentNotification();
            syncTimerScreenAwake();
            playTimerClockChime(false);
        }
        return changed;
    }

    private static List<String> timersKeys(JSONObject timers) {
        List<String> result = new ArrayList<>();
        JSONArray names = timers == null ? null : timers.names();
        if (names != null) for (int i = 0; i < names.length(); i++) result.add(names.optString(i));
        return result;
    }

    private String currentHifzTarget() {
        JSONObject sabaq = state.optJSONObject("sabaq");
        JSONObject plan = getNativeHifzPlan();
        int totalSteps = Math.max(1, plan == null ? 3 : plan.optInt("totalSteps", 3));
        int step = Math.max(0, Math.min(totalSteps - 1, state.optInt("primarySubStep", 0)));
        if (plan != null) {
            if (step == 0) return plan.optString("dawnTarget", "Belum ada target Manzil");
            if (step == 1) return plan.optString("sabqiTarget", "Belum ada riwayat Sabqi");
            JSONArray steps = plan.optJSONArray("steps");
            JSONObject finalStep = steps == null ? null : steps.optJSONObject(steps.length() - 1);
            if (finalStep != null && plan.optBoolean("weeklyAudit", false)) return finalStep.optString("target", "Audit Sabqi 7 hari");
        }
        return sabaq == null ? "Sabaq · halaman 1" : currentSabaqUnitFullLabel(sabaq);
    }

    private JSONObject getNativeHifzPlan() {
        try {
            return NativeHifzPlan.build(state, Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY);
        } catch (JSONException ignored) { return null; }
    }

    private String hifzStepLabel(JSONObject plan, int step) {
        JSONArray steps = plan == null ? null : plan.optJSONArray("steps");
        JSONObject entry = steps == null ? null : steps.optJSONObject(Math.max(0, step));
        if (entry != null) return entry.optString("label", "LANGKAH " + (step + 1)).toUpperCase(Locale.ROOT);
        return new String[]{"1 · MANZIL", "2 · SABQI", "3 · SABAQ"}[Math.max(0, Math.min(2, step))];
    }

    private long currentStepDurationSeconds() {
        JSONObject plan = getNativeHifzPlan();
        JSONArray steps = plan == null ? null : plan.optJSONArray("steps");
        int step = Math.max(0, Math.min(Math.max(0, (steps == null ? 3 : steps.length()) - 1), state.optInt("primarySubStep", 0)));
        JSONObject selected = steps == null ? null : steps.optJSONObject(step);
        return (long) Math.max(1, selected == null ? HIFZ_STEP_SECONDS / 60 : selected.optInt("durationMins", 30)) * 60L;
    }

    private String stepCue(int step) {
        if (step == 0) return "Mulai dengan murajaah hafalan lama sebelum menambah target baru.";
        if (step == 1) return "Ulangi bacaan dalam antrean Sabqi dan tandai bagian yang belum lancar.";
        JSONObject plan = getNativeHifzPlan();
        if (plan != null && plan.optBoolean("weeklyAudit", false)) {
            return "Aturan audit Minggu: nol Sabaq baru hari ini; mantapkan hafalan 7 hari terakhir.";
        }
        return state.optBoolean("sabaqFrozen", false)
                ? "Sabaq dijeda. Mantapkan Sabqi sebelum melanjutkan hafalan baru."
                : sabaqPaceCue();
    }

    private String sabaqPaceCue() {
        JSONObject sabaq = state.optJSONObject("sabaq");
        if (sabaq == null) return "Lanjutkan target hafalan baru sesuai mode dan rentang yang dipilih.";
        if ("multi_page".equals(sabaq.optString("mode")) && sabaq.optInt("pagesPerDay", 2) > 1) {
            int pages = sabaq.optInt("pagesPerDay", 2);
            return "⚠ " + pages + " halaman dalam satu langkah 30 menit (~" + Math.max(1, 30 / pages)
                    + " menit per halaman): rawan tidak lancar.";
        }
        if ("ayah".equals(sabaq.optString("mode")) && sabaq.optInt("ayahsPerDay", 10) > 15) {
            return "⚠ " + sabaq.optInt("ayahsPerDay") + " ayat melebihi kira-kira satu halaman; pertimbangkan turunkan target.";
        }
        return "Lanjutkan target hafalan baru sesuai mode dan rentang yang dipilih.";
    }

    private Map<String, String> getActivePrayerTimes() {
        return PrayerReminders.timesFor(state, new Date());
    }

    private String buildPrayerLine() {
        if (!PrayerReminders.locationReady(state)) return "ATUR LOKASI JADWAL SHALAT";
        Map<String, String> times = getActivePrayerTimes();
        Calendar now = Calendar.getInstance();
        int currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        String previous = "ISYA";
        String next = "SUBUH";
        int previousMinutes = -1;
        int nextMinutes = Integer.MAX_VALUE;
        for (int i = 0; i < PRAYER_KEYS.length; i++) {
            int mins = NativePrayerCalculator.toMinutes(times.get(PRAYER_KEYS[i]));
            if (mins <= currentMinutes && mins > previousMinutes) { previous = PRAYER_LABELS[i].toUpperCase(Locale.ROOT); previousMinutes = mins; }
            if (mins > currentMinutes && mins < nextMinutes) { next = PRAYER_LABELS[i].toUpperCase(Locale.ROOT); nextMinutes = mins; }
        }
        return previous + " " + times.get("" + prayerKeyForLabel(previous)) + "  →  " + next
                + " " + times.get(prayerKeyForLabel(next));
    }

    private String prayerKeyForLabel(String label) {
        for (int i = 0; i < PRAYER_LABELS.length; i++) if (PRAYER_LABELS[i].equalsIgnoreCase(label)) return PRAYER_KEYS[i];
        return "subuh";
    }

    private String routineAnchorLabel(JSONObject routine) {
        JSONObject anchor = routine.optJSONObject("anchor");
        if (anchor == null) return "Waktu belum diatur";
        String type = anchor.optString("type", "fixed");
        if ("prayer".equals(type)) return anchor.optString("prayer", "Dzuhur") + " + " + anchor.optInt("offsetMins", 0) + " menit";
        if ("after_block".equals(type)) return "Setelah aktivitas · " + anchor.optString("blockId", "Hafalan");
        return anchor.optString("time", "09:00") + " · " + routine.optInt("durationMins", 30) + " menit";
    }

    private void beginImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        try { startActivityForResult(intent, REQUEST_IMPORT); }
        catch (Exception exception) { toast("Pemilih berkas tidak tersedia."); }
    }

    private void beginExport() {
        try {
            pendingExportJson = stateStore.exportBackup(state).toString(2);
            pendingExportName = "rutin-schedule-" + todayString() + ".json";
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, pendingExportName);
            startActivityForResult(intent, REQUEST_EXPORT);
        } catch (Exception exception) { toast("Gagal menyiapkan cadangan."); }
    }

    private void requestWidgetPin() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            toast("Tekan lama layar utama, lalu pilih Widget → Rutinitas.");
            return;
        }
        AppWidgetManager manager = getSystemService(AppWidgetManager.class);
        ComponentName provider = new ComponentName(this, RoutineWidgetProvider.class);
        if (manager == null || !manager.isRequestPinAppWidgetSupported()) {
            toast("Widget tidak didukung launcher ini.");
        } else {
            manager.requestPinAppWidget(provider, null, null);
        }
    }

    private void setNotificationEnabled(boolean enabled) {
        surfacePrefs().edit().putBoolean("notification_enabled", enabled).apply();
        if (!enabled) getSystemService(NotificationManager.class).cancel(NOTIFICATION_ID);
        else if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 5205);
        } else publishCurrentNotification();
    }

    private void setWidgetEnabled(boolean enabled) {
        surfacePrefs().edit().putBoolean("widget_enabled", enabled).apply();
        updateWidget();
    }

    private void openNotificationSettings() {
        Intent intent = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())
                : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
        try { startActivity(intent); }
        catch (Exception exception) {
            try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()))); }
            catch (Exception ignored) { toast("Setelan notifikasi tidak tersedia."); }
        }
    }

    private void updateWidget() {
        surfacePrefs().edit().putString("widget_title", "Hafalan Al-Qur’an Subuh")
                .putString("widget_text", currentHifzTarget()).apply();
        RoutineWidgetProvider.refreshAll(this);
    }

    private void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL,
                    getString(R.string.lockscreen_channel_name), NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(getString(R.string.lockscreen_channel_description));
            channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            channel.setSound(null, null);
            channel.enableVibration(false);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private void publishCurrentNotification() {
        JSONObject activeRoutine = getActiveAgendaTimer();
        boolean hifzActive = isHifzNotificationActive();
        if (!surfacePrefs().getBoolean("notification_enabled", true) || (activeRoutine == null && !hifzActive)) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.cancel(NOTIFICATION_ID);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        int stepCount = Math.max(1, getNativeHifzPlan() == null ? 3 : getNativeHifzPlan().optInt("totalSteps", 3));
        int step = Math.max(0, Math.min(stepCount - 1, state.optInt("primarySubStep", 0)));
        String title = activeRoutine == null ? "Hafalan Al-Qur’an · Langkah " + (step + 1)
                : activeRoutine.optString("title", "Aktivitas berjalan");
        String content = activeRoutine == null ? currentHifzTarget()
                : activeRoutine.optString("protocol", "Timer aktivitas sedang berjalan.");
        long notificationRemaining = activeRoutine == null ? timerRemaining()
                : agendaTimerRemaining(activeRoutine.optString("id", ""));
        Intent openApp = new Intent(this, LockScreenPreviewActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(LockScreenPreviewActivity.EXTRA_TITLE, title)
                .putExtra(LockScreenPreviewActivity.EXTRA_TEXT, content);
        PendingIntent pending = PendingIntent.getActivity(this, NOTIFICATION_ID, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, NOTIFICATION_CHANNEL) : new Notification.Builder(this);
        Notification.Builder publicBuilder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, NOTIFICATION_CHANNEL) : new Notification.Builder(this);
        Notification publicVersion = publicBuilder
                .setSmallIcon(R.drawable.ic_notification).setContentTitle("Rutinitas")
                .setContentText("Buka untuk melihat target hari ini.")
                .setVisibility(Notification.VISIBILITY_PUBLIC).build();
        builder.setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(content)
                .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(publicVersion)
                .setContentIntent(pending).setOnlyAlertOnce(true).setOngoing(true)
                .setCategory(Notification.CATEGORY_STATUS);
        if ((activeRoutine != null || timerIsRunning()) && notificationRemaining > 0) {
            builder.setWhen(System.currentTimeMillis() + notificationRemaining * 1000L)
                    .setUsesChronometer(true).setChronometerCountDown(true);
        }
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, builder.build());
    }

    private JSONObject getActiveAgendaTimer() {
        JSONObject timers = state.optJSONObject("blockTimers");
        if (timers == null) return null;
        for (String id : timersKeys(timers)) {
            if (id.startsWith("primary_hifz_") || "primary_hifz_window".equals(id)) continue;
            JSONObject record = timers.optJSONObject(id);
            if (record == null || record.optLong("startedAtMs", 0) <= 0 || agendaTimerRemaining(id) <= 0) continue;
            JSONArray routines = state.optJSONArray("customRoutines");
            if (routines != null) for (int i = 0; i < routines.length(); i++) {
                JSONObject routine = routines.optJSONObject(i);
                if (routine != null && id.equals(routine.optString("id"))) return routine;
            }
            if (!id.contains("maghrib_manzil")) continue;
            JSONObject plan = getNativeHifzPlan();
            String target = plan == null ? "" : plan.optString("maghribTarget", "");
            try { return new JSONObject().put("id", id).put("title", "Manzil Maghrib · " + target)
                    .put("protocol", "Timer Manzil Maghrib sedang berjalan."); }
            catch (JSONException ignored) { return null; }
        }
        return null;
    }

    private boolean isHifzNotificationActive() {
        if (timerIsRunning()) return true;
        Map<String, String> prayerTimes = getActivePrayerTimes();
        int dawnStart = NativePrayerCalculator.toMinutes(prayerTimes.get("subuh"))
                + state.optInt("subuhOffsetMins", 15);
        int now = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) * 60
                + Calendar.getInstance().get(Calendar.MINUTE);
        return now >= dawnStart && now < dawnStart + 90;
    }

    private SharedPreferences surfacePrefs() { return getSharedPreferences(SURFACES_PREFS, MODE_PRIVATE); }

    private void persistState() {
        if (!appInitialized) return;
        stateSafePut("updatedAt", System.currentTimeMillis());
        stateStore.save(state);
        updateWidget();
        PrayerReminders.schedule(this);
    }

    private void stateSafePut(String key, Object value) {
        try { state.put(key, value); } catch (JSONException ignored) { }
    }

    private LinearLayout card() {
        LinearLayout view = new CardLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(16), dp(14), dp(16), dp(14));
        String surface = surfaceStyle();
        if ("glass".equals(surface)) {
            GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{
                    withAlpha(darkTheme ? 0xFF2B3346 : Color.WHITE, darkTheme ? 190 : 215),
                    withAlpha(colorSurface(), darkTheme ? 140 : 165)});
            glass.setCornerRadius(dp(16));
            glass.setStroke(dp(1), withAlpha(Color.WHITE, darkTheme ? 30 : 190));
            view.setBackground(glass);
            return view;
        }
        int fill = "transparent".equals(surface) || "blur".equals(surface)
                ? withAlpha(colorSurface(), darkTheme ? 200 : 215) : colorSurface();
        view.setBackground(rounded(fill, colorBorder(), dp(16), dp(1)));
        return view;
    }

    /** Latar dengan cahaya lembut; membuat efek transparan/glass terlihat tanpa mengganggu teks. */
    private Drawable stageBackground() {
        if ("solid".equals(surfaceStyle())) return new android.graphics.drawable.ColorDrawable(colorBackground());
        GradientDrawable glowA = new GradientDrawable();
        glowA.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glowA.setColors(new int[]{withAlpha(colorAccent(), darkTheme ? 70 : 46), withAlpha(colorAccent(), 0)});
        glowA.setGradientRadius(dp(320));
        glowA.setGradientCenter(.95f, .05f);
        GradientDrawable glowB = new GradientDrawable();
        glowB.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glowB.setColors(new int[]{withAlpha(0xFF14B8A6, darkTheme ? 46 : 34), withAlpha(0xFF14B8A6, 0)});
        glowB.setGradientRadius(dp(300));
        glowB.setGradientCenter(.05f, .85f);
        return new LayerDrawable(new Drawable[]{new android.graphics.drawable.ColorDrawable(colorBackground()), glowA, glowB});
    }

    private void addKicker(LinearLayout parent, String value) {
        TextView label = text(value, 10, colorAccentText(), true);
        label.setLetterSpacing(0.085f);
        label.setPadding(0, 0, 0, dp(8));
        parent.addView(label);
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value == null ? "" : value);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTypeface(bold ? semiboldTypeface() : appTypeface());
        view.setIncludeFontPadding(false);
        view.setLineSpacing(dp(2), 1.08f);
        return view;
    }

    private TextView pill(String value, int background, int foreground) {
        TextView view = text(value, 10, foreground, true);
        view.setPadding(dp(10), dp(6), dp(10), dp(6));
        view.setBackground(rounded(background, colorAccentBorder(), dp(18), dp(1)));
        return view;
    }

    private LinearLayout actionButton(String title, Runnable action) {
        return actionButton(title, 0, action);
    }

    private LinearLayout actionButton(String title, int iconResource, Runnable action) {
        LinearLayout button = buildStyledButton(title, iconResource, true);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private LinearLayout secondaryButton(String title, Runnable action) {
        return secondaryButton(title, 0, action);
    }

    private LinearLayout secondaryButton(String title, int iconResource, Runnable action) {
        LinearLayout button = buildStyledButton(title, iconResource, false);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private LinearLayout buildStyledButton(String title, int iconResource, boolean primary) {
        LinearLayout button = new LinearLayout(this);
        button.setOrientation(LinearLayout.HORIZONTAL);
        button.setGravity(Gravity.CENTER);
        button.setMinimumHeight(dp(primary ? 52 : 48));
        button.setPadding(dp(15), dp(8), dp(15), dp(8));
        int fill = primary ? colorAccent() : colorSurface();
        int stroke = primary ? colorAccent() : colorBorder();
        int textColor = primary ? colorOnAccent() : colorPrimary();
        button.setBackground(rippleBackground(fill, stroke, dp(primary ? 12 : 11), primary ? 0 : dp(1)));
        button.setClickable(true);
        button.setFocusable(true);
        button.setContentDescription(title);
        pressScale(button);
        if (iconResource != 0) {
            Drawable icon = getDrawable(iconResource);
            if (icon != null) {
                icon = icon.mutate();
                icon.setTint(textColor);
                ImageView image = new ImageView(this);
                image.setImageDrawable(icon);
                image.setContentDescription(null);
                LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(19), dp(19));
                imageParams.rightMargin = dp(9);
                button.addView(image, imageParams);
            }
        }
        TextView label = text(title, primary ? 14 : 13, textColor, true);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        button.addView(label, new LinearLayout.LayoutParams(WRAP, WRAP));
        return button;
    }

    private Button smallButton(String title) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(title);
        button.setTextColor(colorPrimary());
        button.setTextSize(12);
        button.setTypeface(appTypeface(), Typeface.BOLD);
        button.setMinHeight(dp(44));
        button.setPadding(dp(9), dp(6), dp(9), dp(6));
        button.setGravity(Gravity.CENTER);
        button.setStateListAnimator(null);
        button.setElevation(0f);
        button.setTranslationZ(0f);
        button.setBackground(rippleBackground(colorSubtle(), colorBorder(), dp(12), dp(1)));
        pressScale(button);
        return button;
    }

    private EditText input(String value, String hint, int inputType) {
        EditText field = new EditText(this);
        field.setText(value);
        field.setHint(hint);
        field.setInputType(inputType);
        field.setTextColor(colorPrimary());
        field.setHintTextColor(withAlpha(colorMuted(), 170));
        field.setTextSize(15);
        field.setTypeface(appTypeface());
        field.setSingleLine(true);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(dp(16), dp(12), dp(16), dp(12));
        field.setMinHeight(dp(52));
        field.setBackground(inputBackground());
        field.setHighlightColor(withAlpha(colorAccent(), 70));
        if (Build.VERSION.SDK_INT >= 29) {
            GradientDrawable cursor = new GradientDrawable();
            cursor.setColor(colorAccent());
            cursor.setSize(dp(2), dp(20));
            cursor.setCornerRadius(dp(1));
            field.setTextCursorDrawable(cursor);
        }
        return field;
    }

    private Spinner spinner(String[] options) { return spinner(options, null); }

    /** Select bergaya aplikasi: tampilan tertutup seperti field, pilihan dibuka di lembar bawah. */
    private Spinner spinner(String[] options, String[] trailing) {
        SheetSpinner spinner = new SheetSpinner(options, trailing);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, options) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView label = convertView instanceof TextView ? (TextView) convertView : new TextView(NativeMainActivity.this);
                label.setText(options[position]);
                label.setTextColor(colorPrimary());
                label.setTextSize(15);
                label.setTypeface(appTypeface(), Typeface.NORMAL);
                label.setGravity(Gravity.CENTER_VERTICAL);
                label.setIncludeFontPadding(false);
                label.setSingleLine(true);
                label.setEllipsize(TextUtils.TruncateAt.END);
                label.setPadding(dp(16), 0, dp(52), 0);
                label.setMinHeight(dp(52));
                return label;
            }
        };
        spinner.setAdapter(adapter);
        spinner.setBackground(selectBackground());
        spinner.setPadding(0, 0, 0, 0);
        spinner.setMinimumHeight(dp(52));
        return spinner;
    }

    private final class SheetSpinner extends Spinner {
        private final String[] options;
        private final String[] trailing;

        SheetSpinner(String[] options, String[] trailing) {
            super(NativeMainActivity.this, Spinner.MODE_DIALOG);
            this.options = options;
            this.trailing = trailing;
        }

        @Override public boolean performClick() {
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
            playSoundEffect(android.view.SoundEffectConstants.CLICK);
            showOptionSheet(this, options, trailing);
            return true;
        }
    }

    private void showOptionSheet(Spinner owner, String[] options, String[] trailing) {
        CharSequence prompt = owner.getPrompt();
        showOptionSheet(prompt == null ? null : prompt.toString(), options, trailing,
                owner.getSelectedItemPosition(), owner::setSelection);
    }

    private void showOptionSheet(String prompt, String[] options, String[] trailing, int selected, PositionAction onPick) {
        Dialog sheet = new Dialog(this);
        sheet.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(10), dp(16), dp(16));
        GradientDrawable panelBackground = new GradientDrawable();
        panelBackground.setColor(colorSurface());
        float r = dp(24);
        panelBackground.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        panel.setBackground(panelBackground);

        View handle = new View(this);
        handle.setBackground(rounded(colorBorder(), colorBorder(), dp(3), 0));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(40), dp(5));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.bottomMargin = dp(12);
        panel.addView(handle, handleParams);
        if (prompt != null && prompt.length() > 0) {
            TextView title = text(prompt, 17, colorPrimary(), true);
            title.setPadding(dp(4), 0, dp(4), dp(12));
            panel.addView(title);
        }

        final List<Integer> visible = new ArrayList<>();
        for (int i = 0; i < options.length; i++) visible.add(i);
        BaseAdapter adapter = new BaseAdapter() {
            @Override public int getCount() { return visible.size(); }
            @Override public Object getItem(int position) { return options[visible.get(position)]; }
            @Override public long getItemId(int position) { return visible.get(position); }
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                int index = visible.get(position);
                boolean active = index == selected;
                LinearLayout row = new LinearLayout(NativeMainActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(dp(52));
                row.setPadding(dp(14), dp(10), dp(12), dp(10));
                row.setBackground(active ? rounded(colorAccentSoft(), colorAccentBorder(), dp(14), dp(1))
                        : rippleShape(dp(14)));
                TextView label = text(options[index], 15, active ? colorAccentText() : colorPrimary(), active);
                label.setMaxLines(2);
                label.setEllipsize(TextUtils.TruncateAt.END);
                row.addView(label, new LinearLayout.LayoutParams(0, WRAP, 1f));
                if (trailing != null && index < trailing.length && trailing[index] != null && !trailing[index].isEmpty()) {
                    TextView arabic = arabicText(trailing[index], 17);
                    arabic.setTextColor(active ? colorAccentText() : colorSecondary());
                    arabic.setPadding(dp(8), 0, dp(4), 0);
                    row.addView(arabic, new LinearLayout.LayoutParams(WRAP, WRAP));
                }
                ImageView check = new ImageView(NativeMainActivity.this);
                check.setImageResource(R.drawable.ic_check);
                check.setColorFilter(colorAccentText());
                check.setVisibility(active ? View.VISIBLE : View.INVISIBLE);
                LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(dp(20), dp(20));
                checkParams.setMarginStart(dp(8));
                row.addView(check, checkParams);
                row.setContentDescription(options[index] + (active ? ", dipilih" : ""));
                return row;
            }
        };

        if (options.length > 8) {
            EditText search = input("", "Cari…", InputType.TYPE_CLASS_TEXT);
            search.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence c, int a, int b, int d) { }
                @Override public void onTextChanged(CharSequence c, int a, int b, int d) {
                    String query = searchKey(c.toString());
                    visible.clear();
                    for (int i = 0; i < options.length; i++) {
                        String extra = trailing != null && i < trailing.length && trailing[i] != null ? trailing[i] : "";
                        String alias = trailing != null ? surahSearchAliases.getOrDefault(i + 1, "") : "";
                        if (query.isEmpty() || searchKey(options[i]).contains(query)
                                || searchKey(extra).contains(query) || searchKey(alias).contains(query)) visible.add(i);
                    }
                    adapter.notifyDataSetChanged();
                }
                @Override public void afterTextChanged(Editable e) { }
            });
            LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(MATCH, WRAP);
            searchParams.bottomMargin = dp(10);
            panel.addView(search, searchParams);
        }

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(dp(4));
        list.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        styleScrollbars(list);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            sheet.dismiss();
            onPick.run((int) id);
        });
        int maxHeight = Math.round(getResources().getDisplayMetrics().heightPixels * 0.62f);
        int rowsHeight = Math.min(options.length, 9) * dp(56);
        panel.addView(list, new LinearLayout.LayoutParams(MATCH, Math.min(maxHeight, rowsHeight)));
        sheet.setContentView(panel);
        Window window = sheet.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            window.setLayout(MATCH, WRAP);
            window.setGravity(Gravity.BOTTOM);
            window.setWindowAnimations(android.R.style.Animation_InputMethod);
            window.setDimAmount(0.45f);
        }
        sheet.show();
        if (selected > 0) list.setSelectionFromTop(selected, dp(48));
    }

    private ScrollView scrollView() {
        ScrollView scroll = new ScrollView(this);
        styleScrollbars(scroll);
        return scroll;
    }

    /** Scrollbar tipis berwarna aksen yang memudar; pada Android lama disembunyikan. */
    private void styleScrollbars(View view) {
        view.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        if (Build.VERSION.SDK_INT >= 29) {
            view.setVerticalScrollBarEnabled(true);
            view.setScrollBarSize(dp(3));
            view.setVerticalScrollbarThumbDrawable(rounded(withAlpha(colorMuted(), darkTheme ? 120 : 90),
                    Color.TRANSPARENT, dp(2), 0));
            view.setVerticalScrollbarTrackDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            view.setScrollbarFadingEnabled(true);
            view.setScrollBarFadeDuration(450);
            view.setScrollBarDefaultDelayBeforeFade(400);
        } else {
            view.setVerticalScrollBarEnabled(false);
        }
    }

    private View labeled(String label, View control) {
        if (control instanceof Spinner && ((Spinner) control).getPrompt() == null) ((Spinner) control).setPrompt(label);
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.addView(text(label, 12, colorSecondary(), true));
        addGap(group, 6);
        group.addView(control);
        return group;
    }

    private interface SectionContent { void fill(LinearLayout panel); }

    /** Kartu yang bisa dibuka-tutup; status buka diingat selama aplikasi berjalan. */
    private LinearLayout collapsible(String key, String title, String summary, SectionContent content) {
        LinearLayout section = card();
        section.setPadding(dp(16), dp(4), dp(16), dp(4));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(dp(52));
        header.setClickable(true);
        header.setFocusable(true);
        header.setBackground(rippleShape(dp(12)));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(title, 15, colorPrimary(), true));
        if (summary != null && !summary.isEmpty()) {
            TextView hint = text(summary, 12, colorMuted(), false);
            hint.setPadding(0, dp(3), 0, 0);
            hint.setSingleLine(true);
            hint.setEllipsize(TextUtils.TruncateAt.END);
            labels.addView(hint);
        }
        header.addView(labels, new LinearLayout.LayoutParams(0, WRAP, 1f));
        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_chevron_down);
        chevron.setColorFilter(colorMuted());
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(chevron, new LinearLayout.LayoutParams(dp(18), dp(18)));
        section.addView(header, new LinearLayout.LayoutParams(MATCH, WRAP));
        LinearLayout panel = new CardLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(0, 0, 0, dp(12));
        section.addView(panel, new LinearLayout.LayoutParams(MATCH, WRAP));
        Runnable sync = () -> {
            boolean open = openSections.contains(key);
            chevron.setRotation(open ? 180f : 0f);
            panel.removeAllViews();
            if (open) content.fill(panel);
            panel.setVisibility(open ? View.VISIBLE : View.GONE);
            header.setContentDescription(title + (open ? ", terbuka" : ", tertutup"));
        };
        header.setOnClickListener(view -> {
            if (!openSections.remove(key)) openSections.add(key);
            sync.run();
        });
        sync.run();
        return section;
    }

    private void addGap(LinearLayout parent, int sizeDp) {
        View gap = new View(this);
        gap.setTag(SPACING_GAP_TAG);
        parent.addView(gap, new LinearLayout.LayoutParams(1, dp(sizeDp)));
    }

    private int colorField() { return darkTheme ? 0xFF161B25 : 0xFFF6F8FC; }

    private Drawable inputBackground() {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},
                rounded(colorSurface(), colorAccent(), dp(14), dp(2)));
        states.addState(new int[]{}, rounded(colorField(), colorBorder(), dp(14), dp(1)));
        return states;
    }

    private Drawable selectBackground() {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, selectSurface(colorAccent(), dp(2)));
        states.addState(new int[]{android.R.attr.state_focused}, selectSurface(colorAccent(), dp(2)));
        states.addState(new int[]{}, selectSurface(colorBorder(), dp(1)));
        return states;
    }

    private Drawable selectSurface(int stroke, int strokeWidth) {
        Drawable chevron = getDrawable(R.drawable.ic_chevron_down);
        if (chevron != null) chevron = chevron.mutate();
        if (chevron != null) chevron.setTint(colorAccentText());
        GradientDrawable badge = new GradientDrawable();
        badge.setShape(GradientDrawable.OVAL);
        badge.setColor(colorAccentSoft());
        LayerDrawable layers = new LayerDrawable(new Drawable[]{
                rounded(colorField(), stroke, dp(14), strokeWidth), badge, chevron
        });
        layers.setLayerGravity(1, Gravity.END | Gravity.CENTER_VERTICAL);
        layers.setLayerSize(1, dp(28), dp(28));
        layers.setLayerInset(1, 0, 0, dp(12), 0);
        layers.setLayerGravity(2, Gravity.END | Gravity.CENTER_VERTICAL);
        layers.setLayerSize(2, dp(16), dp(16));
        layers.setLayerInset(2, 0, 0, dp(18), 0);
        return layers;
    }

    private Drawable rippleBackground(int fill, int stroke, int radius, int strokeWidth) {
        Drawable content = rounded(fill, stroke, radius, strokeWidth);
        Drawable mask = rounded(fill, stroke, radius, strokeWidth);
        int ripple = darkTheme ? Color.argb(35, 255, 255, 255) : Color.argb(24, 22, 82, 246);
        return new RippleDrawable(ColorStateList.valueOf(ripple), content, mask);
    }

    /** Umpan balik tekan yang halus: elemen mengecil sedikit lalu kembali. */
    private void pressScale(View view) {
        if (reducedMotion()) return;
        android.animation.StateListAnimator animator = new android.animation.StateListAnimator();
        android.animation.AnimatorSet down = new android.animation.AnimatorSet();
        down.playTogether(android.animation.ObjectAnimator.ofFloat(view, View.SCALE_X, .96f),
                android.animation.ObjectAnimator.ofFloat(view, View.SCALE_Y, .96f));
        down.setDuration(90);
        android.animation.AnimatorSet up = new android.animation.AnimatorSet();
        up.playTogether(android.animation.ObjectAnimator.ofFloat(view, View.SCALE_X, 1f),
                android.animation.ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f));
        up.setDuration(160);
        up.setInterpolator(new DecelerateInterpolator());
        animator.addState(new int[]{android.R.attr.state_pressed}, down);
        animator.addState(new int[]{}, up);
        view.setStateListAnimator(animator);
    }

    /** Latar transparan dengan riak sentuh yang terlihat (mask harus opak). */
    private Drawable rippleShape(int radius) {
        int ripple = darkTheme ? Color.argb(35, 255, 255, 255) : Color.argb(24, 22, 82, 246);
        return new RippleDrawable(ColorStateList.valueOf(ripple), null, rounded(Color.WHITE, Color.WHITE, radius, 0));
    }

    private final class CardLayout extends LinearLayout {
        CardLayout(Activity activity) { super(activity); }

        @Override public void addView(View child, int index, ViewGroup.LayoutParams params) {
            boolean spacingGap = SPACING_GAP_TAG.equals(child.getTag());
            int lastIndex = getChildCount() - 1;
            boolean previousIsGap = lastIndex >= 0
                    && SPACING_GAP_TAG.equals(getChildAt(lastIndex).getTag());
            if (!spacingGap && lastIndex >= 0 && !previousIsGap) {
                LinearLayout.LayoutParams spaced;
                if (params instanceof LinearLayout.LayoutParams) {
                    spaced = new LinearLayout.LayoutParams((LinearLayout.LayoutParams) params);
                } else if (params != null) {
                    spaced = new LinearLayout.LayoutParams(params);
                } else {
                    spaced = new LinearLayout.LayoutParams(MATCH, WRAP);
                }
                spaced.topMargin = Math.max(spaced.topMargin, dp(10));
                params = spaced;
            }
            super.addView(child, index, params);
        }
    }

    private android.graphics.drawable.GradientDrawable rounded(int fill, int stroke, int radius, int strokeWidth) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, stroke);
        return drawable;
    }

    private Typeface appTypeface() {
        if (regularFont == null) {
            try { regularFont = Typeface.createFromAsset(getAssets(), "fonts/Inter-Regular.otf"); }
            catch (Exception ignored) { regularFont = Typeface.create("sans-serif", Typeface.NORMAL); }
        }
        return regularFont;
    }

    private Typeface semiboldTypeface() {
        if (semiboldFont == null) {
            try { semiboldFont = Typeface.createFromAsset(getAssets(), "fonts/Inter-SemiBold.otf"); }
            catch (Exception ignored) { semiboldFont = Typeface.create("sans-serif", Typeface.BOLD); }
        }
        return semiboldFont;
    }

    private Typeface monoTypeface() { return Typeface.create("monospace", Typeface.NORMAL); }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private String surfaceStyle() { return surfacePrefs().getString("surface_style", "solid"); }

    private Drawable navigationSurface() {
        String style = surfaceStyle();
        int surface = colorSurface();
        if ("glass".equals(style)) {
            GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{
                    withAlpha(darkTheme ? 0xFF384157 : Color.WHITE, 238), withAlpha(surface, 194)});
            glass.setCornerRadius(dp(NAV_RADIUS_DP));
            glass.setStroke(dp(1), colorAccentBorder());
            return glass;
        }
        if (darkTheme) surface = 0xFF1B1F27;
        if ("transparent".equals(style) || "blur".equals(style)) surface = withAlpha(surface, 225);
        return rounded(surface, colorBorder(), dp(NAV_RADIUS_DP), dp(1));
    }

    private static int withAlpha(int color, int alpha) { return (color & 0x00FFFFFF) | (alpha << 24); }

    private int colorBackground() { return darkTheme ? 0xFF10141D : 0xFFF3F5F9; }
    private int colorSurface() { return darkTheme ? 0xFF1A202C : Color.WHITE; }
    private int colorSubtle() { return darkTheme ? 0xFF222A38 : 0xFFF0F3F9; }
    private int colorBorder() { return darkTheme ? 0xFF323B4D : 0xFFDBE0EB; }
    private int colorPrimary() { return darkTheme ? 0xFFF2F5FA : 0xFF172132; }
    private int colorSecondary() { return darkTheme ? 0xFFB6C1D4 : 0xFF424E62; }
    private int colorMuted() { return darkTheme ? 0xFF9CAAC3 : 0xFF546279; }
    private int colorAccent() { return darkTheme ? Color.rgb(29, 86, 255) : Color.rgb(22, 82, 246); }
    private int colorAccentText() { return darkTheme ? Color.rgb(143, 174, 255) : Color.rgb(22, 82, 246); }
    private int colorAccentSoft() { return darkTheme ? Color.rgb(25, 37, 77) : Color.rgb(232, 238, 255); }
    private int colorAccentBorder() { return darkTheme ? Color.rgb(51, 67, 111) : Color.rgb(205, 216, 255); }
    private int colorInk() { return darkTheme ? Color.rgb(242, 241, 238) : Color.rgb(17, 17, 17); }
    private int colorOnInk() { return darkTheme ? Color.rgb(17, 17, 17) : Color.WHITE; }
    private int colorOnAccent() { return Color.WHITE; }

    private boolean resolveDarkTheme() {
        String preference = state.optString("themePreference", "system");
        if ("dark".equals(preference)) return true;
        if ("light".equals(preference)) return false;
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    private boolean applyPreferredTheme() {
        boolean nextDark = resolveDarkTheme();
        boolean changed = darkTheme != nextDark;
        darkTheme = nextDark;
        setTheme(darkTheme ? R.style.RutinitasDarkTheme : R.style.RutinitasLightTheme);
        return changed;
    }

    private void applySystemBars() {
        int background = colorBackground();
        Window window = getWindow();
        window.setStatusBarColor(background);
        window.setNavigationBarColor(background);
        int flags = window.getDecorView().getSystemUiVisibility();
        if (darkTheme) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        } else {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        window.getDecorView().setSystemUiVisibility(flags);
    }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }

    private static String todayString() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
    }

    private static boolean validTime(String value) {
        return value != null && value.matches("(?:[01]\\d|2[0-3]):[0-5]\\d");
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value.trim()); }
        catch (Exception ignored) { return fallback; }
    }

    private static int indexOf(String[] values, String wanted) {
        for (int i = 0; i < values.length; i++) if (values[i].equals(wanted)) return i;
        return 0;
    }

    private static boolean contains(String[] values, String wanted) {
        for (String value : values) if (value.equals(wanted)) return true;
        return false;
    }

    private static String formatSeconds(long value) {
        return String.format(Locale.ROOT, "%02d:%02d", Math.max(0, value) / 60, Math.max(0, value) % 60);
    }

    private static boolean contains(JSONArray values, String wanted) {
        if (values == null) return false;
        for (int i = 0; i < values.length(); i++) if (wanted.equals(values.optString(i))) return true;
        return false;
    }

    private static boolean contains(JSONArray values, int wanted) {
        if (values == null) return false;
        for (int i = 0; i < values.length(); i++) if (wanted == values.optInt(i, Integer.MIN_VALUE)) return true;
        return false;
    }

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private interface PositionAction { void run(int position); }

    private android.widget.AdapterView.OnItemSelectedListener simpleItemListener(PositionAction action) {
        return new android.widget.AdapterView.OnItemSelectedListener() {
            private boolean initial = true;
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (initial) { initial = false; return; }
                action.run(position);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        };
    }
}
