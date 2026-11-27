package com.rutinitas.islamic;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class NativeQuranAudio {
    static final String[] NAMES = {"Yasir Al-Dosari", "Mishary Alafasy"};
    private static final String[] PATHS = {"yasser-dosari", "mishary-alafasy"};
    private static final int MAX_AUDIO_BYTES = 160 * 1024 * 1024;
    private static final AudioAttributes ATTRIBUTES = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();

    interface Listener {
        void audioChanged();
        void audioMessage(String message);
        void audioCompleted(int reciter, int surah);
    }

    private final Listener listener;
    private final File directory;
    private final AudioManager audioManager;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AudioManager.OnAudioFocusChangeListener focusListener = change -> {
        if (change < 0) main.post(this::pause);
    };
    private MediaPlayer player;
    private AudioFocusRequest focusRequest;
    private boolean hasFocus;
    private boolean ready;
    private boolean closed;
    private int reciter = -1;
    private int surah;
    private int downloadReciter = -1;
    private int downloadSurah;
    private int downloadPercent = -1;

    NativeQuranAudio(Context context, Listener listener) {
        this.listener = listener;
        this.audioManager = context.getSystemService(AudioManager.class);
        this.directory = new File(context.getFilesDir(), "murattal");
    }

    boolean matches(int voice, int chapter) { return reciter == voice && surah == chapter && player != null; }
    boolean isPlaying() { return ready && player != null && player.isPlaying(); }
    boolean loading() { return player != null && !ready; }
    int duration() { return ready ? player.getDuration() : 0; }
    int position() { return ready ? player.getCurrentPosition() : 0; }
    void seek(int millis) { if (ready) player.seekTo(millis); }
    int surah() { return surah; }
    boolean hasDownload(int voice, int chapter) { return file(voice, chapter).length() > 0L; }
    boolean isDownloading() { return downloadReciter >= 0; }
    boolean isDownloading(int voice, int chapter) { return voice == downloadReciter && chapter == downloadSurah; }
    int downloadPercent() { return downloadPercent; }

    void toggle(int voice, int chapter) {
        if (!matches(voice, chapter)) {
            play(voice, chapter);
        } else if (loading()) {
            stop();
        } else if (isPlaying()) {
            pause();
        } else if (takeFocus()) {
            player.start();
            listener.audioChanged();
        }
    }

    void play(int voice, int chapter) {
        releasePlayer();
        if (!takeFocus()) return;
        reciter = voice;
        surah = chapter;
        try {
            MediaPlayer next = new MediaPlayer();
            player = next;
            next.setAudioAttributes(ATTRIBUTES);
            next.setOnPreparedListener(loaded -> {
                if (loaded != player) return;
                ready = true;
                if (hasFocus) loaded.start();
                listener.audioChanged();
            });
            next.setOnCompletionListener(ended -> {
                if (ended != player) return;
                int completedVoice = reciter;
                int completedSurah = surah;
                releasePlayer();
                listener.audioChanged();
                listener.audioCompleted(completedVoice, completedSurah);
            });
            next.setOnErrorListener((failed, what, extra) -> {
                if (failed == player) playbackFailed();
                return true;
            });
            File local = file(voice, chapter);
            if (local.isFile() && local.length() > 0L) {
                try (java.io.FileInputStream input = new java.io.FileInputStream(local)) {
                    next.setDataSource(input.getFD());
                }
            } else {
                next.setDataSource(url(voice, chapter));
            }
            next.prepareAsync();
            listener.audioChanged();
        } catch (IOException | RuntimeException exception) {
            playbackFailed();
        }
    }

    void pause() {
        if (closed) return;
        if (isPlaying()) player.pause();
        abandonFocus();
        listener.audioChanged();
    }

    void stop() {
        releasePlayer();
        listener.audioChanged();
    }

    private void playbackFailed() {
        stop();
        listener.audioMessage("Audio tidak dapat diputar. Periksa koneksi atau unduh ulang surah ini.");
    }

    private boolean takeFocus() {
        if (audioManager == null) { hasFocus = true; return true; }
        if (hasFocus) return true;
        int result;
        if (Build.VERSION.SDK_INT >= 26) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(ATTRIBUTES).setOnAudioFocusChangeListener(focusListener, main).build();
            result = audioManager.requestAudioFocus(focusRequest);
        } else {
            result = audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
        hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if (!hasFocus) listener.audioMessage("Audio sedang digunakan oleh aplikasi lain.");
        return hasFocus;
    }

    private void abandonFocus() {
        if (hasFocus && audioManager != null) {
            if (Build.VERSION.SDK_INT >= 26 && focusRequest != null) audioManager.abandonAudioFocusRequest(focusRequest);
            else audioManager.abandonAudioFocus(focusListener);
        }
        hasFocus = false;
        focusRequest = null;
    }

    private void releasePlayer() {
        MediaPlayer previous = player;
        player = null;
        ready = false;
        reciter = -1;
        surah = 0;
        if (previous != null) previous.release();
        abandonFocus();
    }

    void download(int voice, int chapter) {
        if (isDownloading() || closed || hasDownload(voice, chapter)) return;
        downloadReciter = voice;
        downloadSurah = chapter;
        downloadPercent = -1;
        listener.audioChanged();
        executor.execute(() -> {
            boolean success = false;
            File destination = file(voice, chapter);
            File partial = new File(directory, destination.getName() + ".part");
            HttpURLConnection connection = null;
            try {
                if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Storage unavailable");
                connection = (HttpURLConnection) new URL(url(voice, chapter)).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                if (connection.getResponseCode() != 200) throw new IOException("Audio unavailable");
                int expected = connection.getContentLength();
                String type = connection.getContentType();
                if (expected > MAX_AUDIO_BYTES || (type != null && !type.startsWith("audio/")
                        && !type.startsWith("application/octet-stream"))) throw new IOException("Invalid audio");
                try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(partial)) {
                    byte[] buffer = new byte[32 * 1024];
                    int total = 0;
                    int previousPercent = -5;
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        total += read;
                        if (Thread.currentThread().isInterrupted() || total > MAX_AUDIO_BYTES) throw new IOException();
                        output.write(buffer, 0, read);
                        if (expected > 0) {
                            int percent = (int) (100L * total / expected);
                            if (percent >= previousPercent + 5) {
                                previousPercent = percent;
                                main.post(() -> {
                                    if (closed) return;
                                    downloadPercent = percent;
                                    listener.audioChanged();
                                });
                            }
                        }
                    }
                    if (total == 0 || (expected > 0 && total != expected)) throw new IOException("Incomplete audio");
                }
                success = partial.renameTo(destination);
            } catch (IOException | RuntimeException ignored) {
                // A partial download is never exposed to the player.
            } finally {
                if (connection != null) connection.disconnect();
                if (!success) partial.delete();
            }
            boolean downloaded = success;
            main.post(() -> {
                if (closed) return;
                downloadReciter = -1;
                downloadSurah = 0;
                listener.audioChanged();
                listener.audioMessage(downloaded ? "Audio tersimpan untuk diputar offline."
                        : "Unduhan gagal. Periksa koneksi dan ruang penyimpanan.");
            });
        });
    }

    boolean removeDownload(int voice, int chapter) {
        if (matches(voice, chapter)) stop();
        return file(voice, chapter).delete();
    }

    void close() {
        closed = true;
        executor.shutdownNow();
        main.removeCallbacksAndMessages(null);
        releasePlayer();
    }

    private File file(int voice, int chapter) { return new File(directory, PATHS[voice] + "-" + chapter + ".mp3"); }

    static String url(int voice, int chapter) {
        return String.format(Locale.ROOT, "https://cdn.mp3quran.net/audio/%s/r1/%03d.mp3", PATHS[voice], chapter);
    }
}
