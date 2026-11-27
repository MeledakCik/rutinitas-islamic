package com.rutinitas.islamic;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Penghitung dzikir (tasbih digital) yang dipakai bersama oleh aplikasi dan widget.
 * Data disimpan di SharedPreferences agar widget bisa menambah hitungan tanpa membuka aplikasi.
 */
final class NativeDzikir {
    static final class Preset {
        final String latin;
        final String arabic;
        final String meaning;
        final int target;
        /** Bagian dari tasbih setelah shalat: setelah target tercapai, ketukan berikutnya pindah ke bacaan selanjutnya. */
        final boolean sequence;

        Preset(String latin, String arabic, String meaning, int target, boolean sequence) {
            this.latin = latin;
            this.arabic = arabic;
            this.meaning = meaning;
            this.target = target;
            this.sequence = sequence;
        }
    }

    static final Preset[] PRESETS = {
            new Preset("Subhanallah", "سُبْحَانَ اللّٰهِ", "Maha Suci Allah", 33, true),
            new Preset("Alhamdulillah", "اَلْحَمْدُ لِلّٰهِ", "Segala puji bagi Allah", 33, true),
            new Preset("Allahu Akbar", "اَللّٰهُ أَكْبَرُ", "Allah Maha Besar", 33, true),
            new Preset("Astaghfirullah", "أَسْتَغْفِرُ اللّٰهَ", "Aku memohon ampun kepada Allah", 100, false),
            new Preset("La ilaha illallah", "لَا إِلٰهَ إِلَّا اللّٰهُ", "Tiada tuhan selain Allah", 100, false),
            new Preset("Shalawat", "اَللّٰهُمَّ صَلِّ عَلٰى مُحَمَّدٍ", "Ya Allah, limpahkan shalawat kepada Muhammad", 100, false),
            new Preset("Hitungan bebas", "", "Tanpa bacaan tertentu", 100, false),
    };

    static final int[] TARGET_CHOICES = {33, 99, 100, 1000};

    static final int EVENT_COUNTED = 0;
    static final int EVENT_TARGET_REACHED = 1;
    static final int EVENT_NEXT_PRESET = 2;

    /** Keadaan hitungan saat ini. */
    static final class Counter {
        int index;
        int count;
        int today;
        String day = "";
    }

    private NativeDzikir() { }

    static int clampIndex(int index) { return Math.max(0, Math.min(PRESETS.length - 1, index)); }

    /** Bacaan berikutnya: berputar di dalam urutan tasbih, atau ke bacaan selanjutnya di daftar. */
    static int nextIndex(int index) {
        index = clampIndex(index);
        if (PRESETS[index].sequence) {
            int next = index + 1;
            return next < PRESETS.length && PRESETS[next].sequence ? next : 0;
        }
        return (index + 1) % PRESETS.length;
    }

    /** Menghitung ulang total harian bila sudah berganti hari. */
    static void rollDay(Counter counter, String today) {
        if (!today.equals(counter.day)) {
            counter.day = today;
            counter.today = 0;
        }
    }

    /** Satu ketukan. Bacaan urutan tasbih yang sudah mencapai target berpindah ke bacaan berikutnya mulai dari 1. */
    static int tap(Counter counter, int target, String today) {
        rollDay(counter, today);
        counter.index = clampIndex(counter.index);
        counter.today++;
        if (PRESETS[counter.index].sequence && counter.count >= target) {
            counter.index = nextIndex(counter.index);
            counter.count = 1;
            return EVENT_NEXT_PRESET;
        }
        counter.count++;
        return counter.count == target ? EVENT_TARGET_REACHED : EVENT_COUNTED;
    }

    static void select(Counter counter, int index) {
        counter.index = clampIndex(index);
        counter.count = 0;
    }

    static void reset(Counter counter) { counter.count = 0; }

    // ---- Penyimpanan ----

    private static final String PREFS = "routine_surfaces";

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String todayKey() { return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date()); }

    static Counter load(Context context) {
        SharedPreferences prefs = prefs(context);
        Counter counter = new Counter();
        counter.index = clampIndex(prefs.getInt("dzikir_index", 0));
        counter.count = Math.max(0, prefs.getInt("dzikir_count", 0));
        counter.today = Math.max(0, prefs.getInt("dzikir_today", 0));
        counter.day = prefs.getString("dzikir_day", "");
        rollDay(counter, todayKey());
        return counter;
    }

    static void save(Context context, Counter counter) {
        prefs(context).edit()
                .putInt("dzikir_index", counter.index)
                .putInt("dzikir_count", counter.count)
                .putInt("dzikir_today", counter.today)
                .putString("dzikir_day", counter.day)
                .apply();
    }

    static int target(Context context, int index) {
        index = clampIndex(index);
        return Math.max(1, prefs(context).getInt("dzikir_target_" + index, PRESETS[index].target));
    }

    static void setTarget(Context context, int index, int target) {
        prefs(context).edit().putInt("dzikir_target_" + clampIndex(index), Math.max(1, target)).apply();
    }

    static boolean vibrateEnabled(Context context) { return prefs(context).getBoolean("dzikir_vibrate", true); }

    static void setVibrateEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean("dzikir_vibrate", enabled).apply();
    }

    /** Ketukan dari aplikasi atau widget: simpan, getarkan, lalu perbarui widget. */
    static int tapAndSave(Context context) {
        Counter counter = load(context);
        int event = tap(counter, target(context, counter.index), todayKey());
        save(context, counter);
        vibrate(context, event);
        DzikirWidgetProvider.refreshAll(context);
        return event;
    }

    static void vibrate(Context context, int event) {
        if (!vibrateEnabled(context)) return;
        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) return;
            long[] pattern = event == EVENT_COUNTED ? new long[]{0, 18}
                    : event == EVENT_TARGET_REACHED ? new long[]{0, 220, 90, 220} : new long[]{0, 60};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                vibrator.vibrate(pattern, -1);
            }
        } catch (RuntimeException ignored) { /* getar tidak tersedia */ }
    }
}
