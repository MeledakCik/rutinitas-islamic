package com.rutinitas.islamic;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Waktu mulai tiap ayat dari MP3Quran (endpoint ayat_timing). Read id harus cocok dengan folder audio
 * yang diputar NativeQuranAudio: 92 = yasser-dosari/r1, 123 = mishary-alafasy/r1.
 */
final class NativeQuranTiming {
    static final int[] READ_IDS = {92, 123};
    private static final int MAX_BYTES = 512 * 1024;

    interface Callback { void loaded(int reciter, int surah); }

    private final File directory;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<String, int[]> cache = new HashMap<>();
    private final Set<String> pending = new HashSet<>();

    NativeQuranTiming(Context context) {
        directory = new File(context.getFilesDir(), "murattal");
    }

    private static String key(int reciter, int surah) { return reciter + ":" + surah; }

    /** starts[a] = milidetik mulai ayat a (indeks 0 = isti'adzah/basmalah pembuka), atau null jika belum ada. */
    int[] get(int reciter, int surah) { return cache.get(key(reciter, surah)); }

    void load(int reciter, int surah, int ayahCount, Callback callback) {
        if (reciter < 0 || reciter >= READ_IDS.length || surah < 1 || surah > 114) return;
        String key = key(reciter, surah);
        if (cache.containsKey(key) || !pending.add(key)) return;
        File file = new File(directory, String.format(Locale.ROOT, "timing-%d-%03d.json", READ_IDS[reciter], surah));
        executor.execute(() -> {
            int[] starts = null;
            try {
                String body = file.isFile() ? read(new FileInputStream(file)) : null;
                if (body == null) {
                    body = download(READ_IDS[reciter], surah);
                    starts = parse(body, ayahCount);
                    if (starts != null) {
                        if (!directory.isDirectory()) directory.mkdirs();
                        try (FileOutputStream out = new FileOutputStream(file)) { out.write(body.getBytes(StandardCharsets.UTF_8)); }
                    }
                } else {
                    starts = parse(body, ayahCount);
                }
            } catch (Exception ignored) { }
            final int[] result = starts;
            main.post(() -> {
                pending.remove(key);
                if (result != null) {
                    cache.put(key, result);
                    callback.loaded(reciter, surah);
                }
            });
        });
    }

    /** Ayat yang sedang dibacakan pada posisi ms (0 = pembuka sebelum ayat 1). */
    static int ayahAt(int[] starts, int positionMs) {
        if (starts == null) return -1;
        int current = 0;
        for (int ayah = 1; ayah < starts.length; ayah++) {
            if (starts[ayah] >= 0 && positionMs >= starts[ayah]) current = ayah;
        }
        return current;
    }

    static int[] parse(String body, int ayahCount) throws Exception {
        if (body == null) return null;
        JSONArray rows = body.trim().startsWith("[") ? new JSONArray(body) : new JSONObject(body).optJSONArray("data");
        if (rows == null || ayahCount < 1) return null;
        int[] starts = new int[ayahCount + 1];
        java.util.Arrays.fill(starts, -1);
        starts[0] = 0;
        int found = 0;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            int ayah = row.optInt("ayah", -1);
            int start = row.optInt("start_time", -1);
            if (ayah >= 1 && ayah <= ayahCount && start >= 0) { starts[ayah] = start; found++; }
        }
        // Data harus lengkap untuk surah ini; kalau tidak, jangan dipakai agar sorotan tidak salah ayat.
        return found == ayahCount ? starts : null;
    }

    private static String download(int read, int surah) throws Exception {
        URL url = new URL(String.format(Locale.ROOT, "https://mp3quran.net/api/v3/ayat_timing?surah=%d&read=%d", surah, read));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(15000);
        try {
            if (connection.getResponseCode() != 200) return null;
            return read(connection.getInputStream());
        } finally {
            connection.disconnect();
        }
    }

    private static String read(InputStream stream) throws Exception {
        try (InputStream input = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) > 0) {
                total += count;
                if (total > MAX_BYTES) return null;
                out.write(buffer, 0, count);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
