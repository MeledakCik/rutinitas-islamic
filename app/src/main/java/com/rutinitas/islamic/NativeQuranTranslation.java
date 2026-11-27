package com.rutinitas.islamic;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class NativeQuranTranslation {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private final File directory;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean closed;
    private boolean loading;

    NativeQuranTranslation(Context context) {
        directory = new File(context.getFilesDir(), "terjemahan");
    }

    boolean loading() { return loading; }

    List<String> read(int surah, int expected) {
        try {
            JSONArray data = new JSONArray(new String(file(surah).readFully(), StandardCharsets.UTF_8));
            if (data.length() != expected) return null;
            List<String> verses = new ArrayList<>(expected);
            for (int i = 0; i < expected; i++) verses.add(data.getString(i));
            return verses;
        } catch (Exception ignored) { return null; }
    }

    void download(int surah, int expected, java.util.function.Consumer<Boolean> complete) {
        if (loading || closed) return;
        loading = true;
        executor.execute(() -> {
            boolean success = false;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL("https://equran.id/api/v2/surat/" + surah).openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty("Accept", "application/json");
                if (connection.getResponseCode() != 200) throw new IOException("Translation unavailable");
                JSONObject chapter;
                try (InputStream input = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int length;
                    while ((length = input.read(buffer)) != -1) {
                        if (bytes.size() + length > MAX_BYTES) throw new IOException("Translation too large");
                        bytes.write(buffer, 0, length);
                    }
                    chapter = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8)).getJSONObject("data");
                }
                JSONArray rows = chapter.getJSONArray("ayat");
                if (chapter.getInt("nomor") != surah || rows.length() != expected) throw new IOException("Verse count mismatch");
                JSONArray verses = new JSONArray();
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    String verse = row.getString("teksIndonesia");
                    if (row.getInt("nomorAyat") != i + 1 || verse.trim().isEmpty()) throw new IOException("Verse mismatch");
                    verses.put(verse);
                }
                AtomicFile destination = file(surah);
                FileOutputStream output = destination.startWrite();
                try {
                    output.write(verses.toString().getBytes(StandardCharsets.UTF_8));
                    destination.finishWrite(output);
                } catch (IOException exception) {
                    destination.failWrite(output);
                    throw exception;
                }
                success = true;
            } catch (Exception ignored) {
                // Keep the previous valid copy if a new download fails.
            } finally {
                if (connection != null) connection.disconnect();
            }
            boolean downloaded = success;
            main.post(() -> {
                if (closed) return;
                loading = false;
                complete.accept(downloaded);
            });
        });
    }

    void close() {
        closed = true;
        executor.shutdownNow();
        main.removeCallbacksAndMessages(null);
    }

    private AtomicFile file(int surah) { return new AtomicFile(new File(directory, surah + ".json")); }
}
