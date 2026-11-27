package com.rutinitas.islamic;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.SizeF;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/** Widget waktu shalat: shalat berikutnya, hitung mundur, dan jadwal lima waktu hari ini. */
public final class PrayerWidgetProvider extends AppWidgetProvider {
    /** Mulai tinggi ini jadwal ditulis ke bawah; di bawahnya memakai lima kolom. */
    private static final int TALL_MIN_HEIGHT_DP = 180;

    private static final int[] ROWS = {R.id.prayerWidgetRow0, R.id.prayerWidgetRow1, R.id.prayerWidgetRow2,
            R.id.prayerWidgetRow3, R.id.prayerWidgetRow4};
    private static final int[] LABELS = {R.id.prayerWidgetLabel0, R.id.prayerWidgetLabel1, R.id.prayerWidgetLabel2,
            R.id.prayerWidgetLabel3, R.id.prayerWidgetLabel4};
    private static final int[] CLOCKS = {R.id.prayerWidgetClock0, R.id.prayerWidgetClock1, R.id.prayerWidgetClock2,
            R.id.prayerWidgetClock3, R.id.prayerWidgetClock4};

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        // Menjadwalkan ulang juga memperbarui widget dan memasang alarm penyegaran berikutnya.
        PrayerReminders.schedule(context);
    }

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int appWidgetId,
                                                    Bundle newOptions) {
        refreshAll(context);
    }

    static boolean hasWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        return manager.getAppWidgetIds(new ComponentName(context, PrayerWidgetProvider.class)).length > 0;
    }

    static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, PrayerWidgetProvider.class));
        if (ids.length == 0) return;
        JSONObject state = new NativeStateStore(context).load();
        long now = System.currentTimeMillis();
        if (Build.VERSION.SDK_INT >= 31) {
            // Launcher memilih tata letak sendiri sesuai ukuran widget saat ini.
            Map<SizeF, RemoteViews> layouts = new HashMap<>();
            layouts.put(new SizeF(110f, 40f), build(context, state, now, R.layout.prayer_widget));
            layouts.put(new SizeF(110f, TALL_MIN_HEIGHT_DP), build(context, state, now, R.layout.prayer_widget_tall));
            manager.updateAppWidget(ids, new RemoteViews(layouts));
            return;
        }
        for (int id : ids) {
            Bundle options = manager.getAppWidgetOptions(id);
            int height = options == null ? 0 : options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
            int layout = height >= TALL_MIN_HEIGHT_DP ? R.layout.prayer_widget_tall : R.layout.prayer_widget;
            manager.updateAppWidget(id, build(context, state, now, layout));
        }
    }

    private static RemoteViews build(Context context, JSONObject state, long now, int layout) {
        RemoteViews views = new RemoteViews(context.getPackageName(), layout);
        boolean ready = PrayerReminders.locationReady(state);
        PrayerReminders.Next next = ready ? PrayerReminders.nextPrayer(state, now) : null;
        if (next == null) {
            views.setTextViewText(R.id.prayerWidgetName, "Atur lokasi");
            views.setTextViewText(R.id.prayerWidgetTime, "");
            views.setViewVisibility(R.id.prayerWidgetCountdown, View.GONE);
            views.setViewVisibility(R.id.prayerWidgetMessage, View.VISIBLE);
            views.setTextViewText(R.id.prayerWidgetMessage, "Ketuk untuk memilih kota agar jadwal sesuai tempatmu.");
            views.setViewVisibility(R.id.prayerWidgetDivider, View.GONE);
            views.setViewVisibility(R.id.prayerWidgetTable, View.GONE);
        } else {
            views.setTextViewText(R.id.prayerWidgetName, PrayerReminders.label(next.key));
            views.setTextViewText(R.id.prayerWidgetTime, next.time);
            long base = SystemClock.elapsedRealtime() + Math.max(0, next.at - now);
            views.setChronometer(R.id.prayerWidgetCountdown, base, "%s lagi", true);
            views.setChronometerCountDown(R.id.prayerWidgetCountdown, true);
            fillTable(context, views, state, next, now);
        }
        Intent open = new Intent(context, NativeMainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("open_tab", "prayer");
        views.setOnClickPendingIntent(R.id.prayerWidgetRoot, PendingIntent.getActivity(context, 4401, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return views;
    }

    /** Jadwal hari ini: shalat berikutnya disorot, yang sudah lewat diredupkan. */
    private static void fillTable(Context context, RemoteViews views, JSONObject state, PrayerReminders.Next next, long now) {
        Calendar today = Calendar.getInstance();
        today.setTimeInMillis(now);
        Calendar nextDay = Calendar.getInstance();
        nextDay.setTimeInMillis(next.at);
        boolean nextIsToday = today.get(Calendar.DAY_OF_YEAR) == nextDay.get(Calendar.DAY_OF_YEAR)
                && today.get(Calendar.YEAR) == nextDay.get(Calendar.YEAR);
        // Setelah Isya, tabel menampilkan jadwal besok agar Subuh berikutnya ikut tersorot.
        Map<String, String> times = PrayerReminders.timesFor(state, nextIsToday ? new Date(now) : new Date(next.at));
        int nowMinutes = today.get(Calendar.HOUR_OF_DAY) * 60 + today.get(Calendar.MINUTE);
        int text = context.getColor(R.color.widget_text);
        int muted = context.getColor(R.color.widget_secondary);
        int accent = context.getColor(R.color.widget_accent);
        for (int i = 0; i < PrayerReminders.KEYS.length; i++) {
            String key = PrayerReminders.KEYS[i];
            String time = times.get(key);
            boolean isNext = key.equals(next.key);
            boolean past = nextIsToday && !isNext && NativePrayerCalculator.toMinutes(time) <= nowMinutes;
            int color = isNext ? accent : past ? muted : text;
            views.setTextViewText(LABELS[i], PrayerReminders.LABELS[i]);
            views.setTextViewText(CLOCKS[i], time);
            views.setTextColor(LABELS[i], color);
            views.setTextColor(CLOCKS[i], color);
            views.setInt(ROWS[i], "setBackgroundResource", isNext ? R.drawable.widget_button_background : 0);
        }
    }
}
