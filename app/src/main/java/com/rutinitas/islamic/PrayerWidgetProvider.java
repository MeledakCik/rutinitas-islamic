package com.rutinitas.islamic;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.util.Date;
import java.util.Map;

/** Widget waktu shalat: shalat berikutnya, hitung mundur, dan jadwal hari ini. */
public final class PrayerWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        // Menjadwalkan ulang juga memperbarui widget dan memasang alarm penyegaran berikutnya.
        PrayerReminders.schedule(context);
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
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.prayer_widget);

        PrayerReminders.Next next = PrayerReminders.locationReady(state) ? PrayerReminders.nextPrayer(state, now) : null;
        if (next == null) {
            views.setTextViewText(R.id.prayerWidgetName, "Atur lokasi");
            views.setTextViewText(R.id.prayerWidgetTime, "");
            views.setViewVisibility(R.id.prayerWidgetCountdown, View.GONE);
            views.setTextViewText(R.id.prayerWidgetList, "Ketuk untuk memilih kota agar jadwal sesuai tempatmu.");
        } else {
            views.setTextViewText(R.id.prayerWidgetName, PrayerReminders.label(next.key));
            views.setTextViewText(R.id.prayerWidgetTime, next.time);
            views.setViewVisibility(R.id.prayerWidgetCountdown, View.VISIBLE);
            long base = SystemClock.elapsedRealtime() + Math.max(0, next.at - now);
            views.setChronometer(R.id.prayerWidgetCountdown, base, "%s lagi", true);
            views.setChronometerCountDown(R.id.prayerWidgetCountdown, true);
            views.setTextViewText(R.id.prayerWidgetList, todayList(state));
        }

        Intent open = new Intent(context, NativeMainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("open_tab", "prayer");
        views.setOnClickPendingIntent(R.id.prayerWidgetRoot, PendingIntent.getActivity(context, 4401, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        manager.updateAppWidget(ids, views);
    }

    /** "Subuh 04:12 · Dzuhur 11:45 · Ashar 15:06 · Maghrib 17:58 · Isya 19:10". */
    private static String todayList(JSONObject state) {
        Map<String, String> times = PrayerReminders.timesFor(state, new Date());
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < PrayerReminders.KEYS.length; i++) {
            if (list.length() > 0) list.append(" · ");
            list.append(PrayerReminders.LABELS[i]).append(' ').append(times.get(PrayerReminders.KEYS[i]));
        }
        return list.toString();
    }
}
