package com.rutinitas.islamic;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Jadwal shalat aktif dan pengingat saat masuk waktu adzan (satu alarm berikutnya, dijadwalkan ulang berantai). */
final class PrayerReminders {
    static final String CHANNEL = "adzan_v1";
    static final String CHANNEL_ALARM = "adzan_alarm_v1";
    static final String ACTION_ADZAN = "com.rutinitas.islamic.action.ADZAN";
    static final String ACTION_DISMISS = "com.rutinitas.islamic.action.ADZAN_DISMISS";
    static final String ACTION_BEFORE = "com.rutinitas.islamic.action.ADZAN_BEFORE";
    static final String ACTION_WIDGET_REFRESH = "com.rutinitas.islamic.action.PRAYER_WIDGET_REFRESH";
    /** Pilihan pengingat sebelum waktu shalat, dalam menit (0 = mati). */
    static final int[] BEFORE_CHOICES = {0, 5, 10, 15, 20, 30};
    static final String EXTRA_KEY = "prayer_key";
    static final String EXTRA_TIME = "prayer_time";
    static final String EXTRA_AT = "prayer_at";
    static final String EXTRA_NOTIFICATION = "notification_id";
    static final String[] KEYS = {"subuh", "dzuhur", "ashar", "maghrib", "isya"};
    static final String[] LABELS = {"Subuh", "Dzuhur", "Ashar", "Maghrib", "Isya"};
    static final String MODE_NOTIFICATION = "notification";
    static final String MODE_ALARM = "alarm";
    static final String MODE_ADZAN = "adzan";
    static final String CHANNEL_INFO = "adzan_info_v1";
    private static final String PREFS = "routine_surfaces";
    private static final int ALARM_REQUEST = 7301;
    private static final int BEFORE_REQUEST = 7302;
    private static final int WIDGET_REQUEST = 7303;
    private static final int NOTIFICATION_BASE = 7400;
    private static final int BEFORE_NOTIFICATION_BASE = 7410;
    private static final int TEST_NOTIFICATION = 7499;
    private static final long LATE_LIMIT_MS = 45 * 60 * 1000L;

    private PrayerReminders() { }

    static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean enabled(Context context) { return prefs(context).getBoolean("adzan_enabled", true); }

    static boolean enabled(Context context, String key) {
        return enabled(context) && prefs(context).getBoolean("adzan_" + key, true);
    }

    static String mode(Context context) { return prefs(context).getString("adzan_mode", MODE_ADZAN); }

    /** Menit sebelum waktu shalat untuk pengingat persiapan; 0 berarti mati. */
    static int beforeMinutes(Context context) {
        return Math.max(0, Math.min(120, prefs(context).getInt("adzan_before_mins", 0)));
    }

    static void setBeforeMinutes(Context context, int minutes) {
        prefs(context).edit().putInt("adzan_before_mins", Math.max(0, Math.min(120, minutes))).apply();
    }

    /** Adzan Mekkah bawaan aplikasi (res/raw/adzan_mekkah.mp3). */
    static Uri bundledAdzan(Context context) {
        return Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.adzan_mekkah);
    }

    /** true bila pengguna memilih file sendiri (bukan adzan bawaan) untuk waktu ini. */
    static boolean hasCustomAdzan(Context context, String key) {
        SharedPreferences prefs = prefs(context);
        return ("subuh".equals(key) && prefs.contains("adzan_uri_subuh")) || prefs.contains("adzan_uri");
    }

    /** File adzan pilihan pengguna (Subuh memakai file khusus Subuh bila ada); bila tidak ada, adzan bawaan. */
    static Uri adzanUri(Context context, String key) {
        SharedPreferences prefs = prefs(context);
        String value = "subuh".equals(key) ? prefs.getString("adzan_uri_subuh", null) : null;
        if (value == null) value = prefs.getString("adzan_uri", null);
        return value == null ? bundledAdzan(context) : Uri.parse(value);
    }

    static void clearAdzanUri(Context context, String key) {
        SharedPreferences prefs = prefs(context);
        if ("subuh".equals(key) && prefs.contains("adzan_uri_subuh")) prefs.edit().remove("adzan_uri_subuh").apply();
        else prefs.edit().remove("adzan_uri").apply();
    }

    static String label(String key) {
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return LABELS[i];
        return key;
    }

    private static int index(String key) {
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return i;
        return 0;
    }

    /** true jika jadwal mengikuti lokasi nyata (kota, GPS, atau input manual), bukan jadwal contoh. */
    static boolean locationReady(JSONObject state) {
        String source = state == null ? "fixed" : state.optString("prayerSource", "fixed");
        if ("city".equals(source)) return city(state) != null;
        return "gps".equals(source) || "manual".equals(source);
    }

    static IndonesianCities.City city(JSONObject state) {
        JSONObject chosen = state == null ? null : state.optJSONObject("cityLocation");
        return chosen == null ? null : IndonesianCities.find(chosen.optString("name", null));
    }

    /** Ringkasan sumber jadwal, misalnya "Bandung · Kemenag RI". */
    static String sourceLabel(JSONObject state) {
        String source = state == null ? "fixed" : state.optString("prayerSource", "fixed");
        String method = methodLabel(state == null ? "kemenag" : state.optString("gpsMethod", "kemenag"));
        if ("city".equals(source)) {
            IndonesianCities.City city = city(state);
            return city == null ? "Kota belum dipilih" : city.name + " · " + method;
        }
        if ("gps".equals(source)) {
            JSONObject gps = state.optJSONObject("gpsLocation");
            String place = gps == null ? "Lokasi GPS" : gps.optString("label", "Lokasi GPS");
            return place + " · " + method;
        }
        if ("manual".equals(source)) return "Waktu diisi manual";
        return "Jadwal contoh — belum sesuai lokasimu";
    }

    static String methodLabel(String key) {
        switch (key == null ? "" : key) {
            case "mwl": return "Muslim World League";
            case "isna": return "ISNA";
            case "makkah": return "Umm al-Qura";
            case "egypt": return "Mesir";
            default: return "Kemenag RI";
        }
    }

    /** Waktu shalat untuk tanggal tertentu, sama persis dengan yang tampil di aplikasi. */
    static Map<String, String> timesFor(JSONObject state, Date date) {
        NativePrayerCalculator.PrayerTimes times = prayerTimesFor(state, date);
        if (times != null) return times.asMap();
        JSONObject manual = state == null ? null : state.optJSONObject("manualPrayerTimes");
        Map<String, String> fixed = NativePrayerCalculator.fixed().asMap();
        if (state == null || !"manual".equals(state.optString("prayerSource"))) return fixed;
        Map<String, String> values = new HashMap<>();
        for (String key : KEYS) values.put(key, manual == null ? fixed.get(key) : manual.optString(key, fixed.get(key)));
        return values;
    }

    /** Hasil hitungan lengkap (termasuk terbit) untuk sumber kota/GPS; null untuk manual/contoh. */
    static NativePrayerCalculator.PrayerTimes prayerTimesFor(JSONObject state, Date date) {
        String source = state == null ? "fixed" : state.optString("prayerSource", "fixed");
        String method = state == null ? "kemenag" : state.optString("gpsMethod", "kemenag");
        try {
            if ("city".equals(source)) {
                IndonesianCities.City city = city(state);
                if (city == null) return null;
                return NativePrayerCalculator.calculate(date, city.lat, city.lon, method, city.tz, city.elevation);
            }
            if ("gps".equals(source)) {
                JSONObject gps = state.optJSONObject("gpsLocation");
                double lat = gps == null ? -6.9175 : gps.optDouble("lat", -6.9175);
                double lon = gps == null ? 107.6191 : gps.optDouble("lon", 107.6191);
                Double tz = gps == null || gps.optBoolean("hasFix", false) ? null : gps.optDouble("tzOffsetHours", 7);
                double elevation = gps != null && gps.has("altitude") ? gps.optDouble("altitude", 0) : Double.NaN;
                if (!Double.isFinite(elevation)) {
                    IndonesianCities.City near = IndonesianCities.nearest(lat, lon, 40);
                    elevation = near == null ? 0 : near.elevation;
                }
                return NativePrayerCalculator.calculate(date, lat, lon, method, tz, elevation);
            }
        } catch (IllegalArgumentException invalid) {
            return null;
        }
        return null;
    }

    static final class Next {
        final String key;
        final String time;
        final long at;
        Next(String key, String time, long at) { this.key = key; this.time = time; this.at = at; }
    }

    /** Waktu shalat berikutnya, terlepas dari setelan pengingat. */
    static Next nextPrayer(JSONObject state, long now) { return find(null, state, now); }

    static Next next(Context context, JSONObject state, long now) {
        if (!locationReady(state)) return null;
        return find(context, state, now);
    }

    private static Next find(Context context, JSONObject state, long now) {
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(now);
        for (int offset = 0; offset < 3; offset++) {
            Calendar noon = (Calendar) day.clone();
            noon.add(Calendar.DAY_OF_MONTH, offset);
            noon.set(Calendar.HOUR_OF_DAY, 12);
            noon.set(Calendar.MINUTE, 0);
            noon.set(Calendar.SECOND, 0);
            noon.set(Calendar.MILLISECOND, 0);
            Map<String, String> times = timesFor(state, noon.getTime());
            for (String key : KEYS) {
                if (context != null && !enabled(context, key)) continue;
                int minutes = NativePrayerCalculator.toMinutes(times.get(key));
                Calendar at = (Calendar) noon.clone();
                at.set(Calendar.HOUR_OF_DAY, minutes / 60);
                at.set(Calendar.MINUTE, minutes % 60);
                if (at.getTimeInMillis() > now + 1000L) return new Next(key, times.get(key), at.getTimeInMillis());
            }
        }
        return null;
    }

    static boolean canScheduleExact(Context context) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        return alarms != null && alarms.canScheduleExactAlarms();
    }

    /**
     * Menjadwalkan (atau membatalkan) alarm adzan berikutnya, pengingat sebelum waktu shalat,
     * dan penyegaran widget waktu shalat.
     */
    static Next schedule(Context context) {
        Context app = context.getApplicationContext();
        AlarmManager alarms = app.getSystemService(AlarmManager.class);
        if (alarms == null) return null;
        JSONObject state = new NativeStateStore(app).load();
        long now = System.currentTimeMillis();
        scheduleWidgetRefresh(app, alarms, state, now);
        PrayerWidgetProvider.refreshAll(app);

        cancel(app, alarms, ACTION_BEFORE, BEFORE_REQUEST);
        Intent intent = new Intent(app, PrayerReminderReceiver.class).setAction(ACTION_ADZAN);
        cancel(app, alarms, ACTION_ADZAN, ALARM_REQUEST);
        if (!enabled(app)) return null;
        Next next = next(app, state, now);
        if (next == null) return null;
        intent.putExtra(EXTRA_KEY, next.key).putExtra(EXTRA_TIME, next.time).putExtra(EXTRA_AT, next.at);
        setWakeup(app, alarms, next.at, PendingIntent.getBroadcast(app, ALARM_REQUEST, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        long beforeMs = beforeMinutes(app) * 60_000L;
        if (beforeMs > 0) {
            // Shalat pertama yang masih punya jeda cukup untuk diingatkan sebelumnya.
            Next upcoming = next(app, state, now + beforeMs);
            if (upcoming != null) {
                Intent before = new Intent(app, PrayerReminderReceiver.class).setAction(ACTION_BEFORE)
                        .putExtra(EXTRA_KEY, upcoming.key).putExtra(EXTRA_TIME, upcoming.time)
                        .putExtra(EXTRA_AT, upcoming.at);
                setWakeup(app, alarms, upcoming.at - beforeMs, PendingIntent.getBroadcast(app, BEFORE_REQUEST, before,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            }
        }
        return next;
    }

    private static void cancel(Context app, AlarmManager alarms, String action, int request) {
        Intent intent = new Intent(app, PrayerReminderReceiver.class).setAction(action);
        PendingIntent existing = PendingIntent.getBroadcast(app, request, intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (existing != null) alarms.cancel(existing);
    }

    private static void setWakeup(Context app, AlarmManager alarms, long at, PendingIntent pending) {
        try {
            if (canScheduleExact(app)) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending);
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending);
            }
        } catch (SecurityException denied) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending);
        }
    }

    /** Widget berganti ke shalat berikutnya tepat saat waktu masuk, dan jadwalnya berganti saat tengah malam. */
    private static void scheduleWidgetRefresh(Context app, AlarmManager alarms, JSONObject state, long now) {
        cancel(app, alarms, ACTION_WIDGET_REFRESH, WIDGET_REQUEST);
        if (!PrayerWidgetProvider.hasWidgets(app)) return;
        Calendar midnight = Calendar.getInstance();
        midnight.setTimeInMillis(now);
        midnight.add(Calendar.DAY_OF_MONTH, 1);
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 5);
        midnight.set(Calendar.MILLISECOND, 0);
        long at = midnight.getTimeInMillis();
        Next upcoming = nextPrayer(state, now);
        if (upcoming != null && upcoming.at + 1000L < at) at = upcoming.at + 1000L;
        Intent intent = new Intent(app, PrayerReminderReceiver.class).setAction(ACTION_WIDGET_REFRESH);
        PendingIntent pending = PendingIntent.getBroadcast(app, WIDGET_REQUEST, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        // Tanpa membangunkan HP: widget cukup diperbarui saat layar menyala lagi.
        alarms.setWindow(AlarmManager.RTC, at, 60_000L, pending);
    }

    /** Pengingat persiapan, misalnya "Ashar 10 menit lagi". */
    static void notifyBefore(Context context, String key, String time, long at) {
        if (key == null || !enabled(context, key) || beforeMinutes(context) == 0) return;
        long remaining = at - System.currentTimeMillis();
        if (at > 0 && remaining <= 0) return;
        long minutes = Math.max(1, (remaining + 30_000L) / 60_000L);
        String name = label(key);
        post(context, BEFORE_NOTIFICATION_BASE + index(key), name + " " + minutes + " menit lagi · " + (time == null ? "" : time),
                "Bersiap untuk shalat " + name + ". Waktunya masuk pukul " + time + ".", System.currentTimeMillis(), false,
                remaining);
    }

    static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Pengingat waktu shalat",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Notifikasi saat masuk waktu Subuh, Dzuhur, Ashar, Maghrib, dan Isya.");
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        channel.enableVibration(true);
        manager.createNotificationChannel(channel);

        NotificationChannel info = new NotificationChannel(CHANNEL_INFO, "Info waktu shalat (tanpa suara)",
                NotificationManager.IMPORTANCE_DEFAULT);
        info.setDescription("Catatan waktu shalat saat suara adzan atau alarm diputar oleh aplikasi.");
        info.setSound(null, null);
        info.enableVibration(false);
        info.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(info);
        manager.deleteNotificationChannel(CHANNEL_ALARM);
    }

    private static boolean notificationsAllowed(Context context) {
        return Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    static void notifyPrayer(Context context, String key, String time, long at) {
        if (key == null || !enabled(context, key)) return;
        if (at > 0 && System.currentTimeMillis() - at > LATE_LIMIT_MS) return;
        String place = sourceLabel(new NativeStateStore(context).load());
        String name = label(key);
        String title = "Waktu " + name + " · " + (time == null ? "" : time);
        boolean playsSound = !MODE_NOTIFICATION.equals(mode(context));
        post(context, NOTIFICATION_BASE + index(key), title, "Telah masuk waktu shalat " + name + ". " + place, at, playsSound);
        if (playsSound) {
            try { AdzanPlayerService.play(context, key, title); } catch (RuntimeException blocked) { /* notifikasi tetap tampil */ }
        }
    }

    /** Contoh pengingat dari tombol uji; teksnya jelas menyebut contoh agar tidak dikira waktu shalat sudah masuk. */
    static boolean notifyTest(Context context) {
        JSONObject state = new NativeStateStore(context).load();
        Next upcoming = next(context, state, System.currentTimeMillis());
        String detail = upcoming == null ? "Aktifkan minimal satu waktu shalat."
                : "Pengingat asli: " + label(upcoming.key) + " pukul " + upcoming.time + ".";
        boolean playsSound = !MODE_NOTIFICATION.equals(mode(context));
        boolean shown = post(context, TEST_NOTIFICATION, "Contoh pengingat adzan", "Ini hanya contoh. " + detail, 0L, playsSound);
        if (playsSound) AdzanPlayerService.play(context, upcoming == null ? "dzuhur" : upcoming.key, "Contoh · suara pengingat");
        return shown || playsSound;
    }

    private static boolean post(Context context, int id, String title, String text, long at, boolean alarm) {
        return post(context, id, title, text, at, alarm, 0L);
    }

    /** timeoutMs &gt; 0: notifikasi hilang sendiri setelah waktu itu (pengingat persiapan hilang saat adzan masuk). */
    private static boolean post(Context context, int id, String title, String text, long at, boolean alarm, long timeoutMs) {
        if (!notificationsAllowed(context)) return false;
        ensureChannel(context);
        Intent open = new Intent(context, NativeMainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("open_tab", "prayer");
        PendingIntent content = PendingIntent.getActivity(context, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, alarm ? CHANNEL_INFO : CHANNEL) : new Notification.Builder(context);
        builder.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(content)
                .setShowWhen(true)
                .setWhen(at > 0 ? at : System.currentTimeMillis());
        if (timeoutMs > 0 && Build.VERSION.SDK_INT >= 26) builder.setTimeoutAfter(timeoutMs);
        if (alarm) {
            Intent stop = new Intent(context, AdzanPlayerService.class).setAction(AdzanPlayerService.ACTION_STOP);
            PendingIntent stopSound = PendingIntent.getService(context, id, stop,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(new Notification.Action.Builder(null, "Matikan suara", stopSound).build());
        } else if (Build.VERSION.SDK_INT < 26) {
            builder.setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL);
        }
        Notification notification = builder.build();
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return false;
        manager.notify(id, notification);
        return true;
    }

    static void dismiss(Context context, int id) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null && id > 0) manager.cancel(id);
    }

    static String describe(Next next) {
        if (next == null) return "Tidak ada pengingat terjadwal.";
        Calendar at = Calendar.getInstance();
        at.setTimeInMillis(next.at);
        Calendar today = Calendar.getInstance();
        boolean tomorrow = at.get(Calendar.DAY_OF_YEAR) != today.get(Calendar.DAY_OF_YEAR);
        return String.format(Locale.ROOT, "Pengingat berikutnya: %s %s%s", label(next.key), next.time, tomorrow ? " (besok)" : "");
    }

    /** "2 j 15 m lagi" / "8 m lagi". */
    static String countdown(long millis) {
        long minutes = Math.max(0, (millis + 59_999L) / 60_000L);
        if (minutes >= 60) return (minutes / 60) + " j " + (minutes % 60) + " m lagi";
        return minutes + " m lagi";
    }
}
