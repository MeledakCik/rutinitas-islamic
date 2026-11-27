package com.rutinitas.islamic;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * Memutar suara adzan (file pilihan pengguna) atau nada alarm lewat jalur ALARM, sehingga tetap terdengar
 * walau notifikasi HP disenyapkan. Berhenti saat selesai, saat "Matikan" ditekan, atau paling lama 6 menit.
 */
public final class AdzanPlayerService extends Service {
    static final String ACTION_PLAY = "com.rutinitas.islamic.action.ADZAN_PLAY";
    static final String ACTION_STOP = "com.rutinitas.islamic.action.ADZAN_STOP";
    static final String EXTRA_TITLE = "title";
    static final String EXTRA_KEY = "prayer_key";
    private static final String CHANNEL = "adzan_playing_v1";
    private static final int NOTIFICATION_ID = 7480;
    private static final long MAX_PLAY_MS = 6 * 60 * 1000L;
    private static final long ALARM_TONE_MS = 3 * 60 * 1000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable stopper = this::stopPlayback;
    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focusRequest;
    private boolean forceTone;

    static void play(Context context, String key, String title) {
        Intent intent = new Intent(context, AdzanPlayerService.class).setAction(ACTION_PLAY)
                .putExtra(EXTRA_KEY, key).putExtra(EXTRA_TITLE, title);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
        else context.startService(intent);
    }

    static void stop(Context context) {
        context.stopService(new Intent(context, AdzanPlayerService.class));
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopPlayback();
            return START_NOT_STICKY;
        }
        String title = intent == null ? null : intent.getStringExtra(EXTRA_TITLE);
        String key = intent == null ? null : intent.getStringExtra(EXTRA_KEY);
        forceTone = false;
        Notification notification = buildNotification(title == null ? "Adzan" : title);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        startPlayback(key);
        return START_NOT_STICKY;
    }

    private Notification buildNotification(String title) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26 && manager != null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "Adzan sedang diputar", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Kontrol saat suara adzan atau alarm waktu shalat berbunyi.");
            channel.setSound(null, null);
            channel.enableVibration(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            manager.createNotificationChannel(channel);
        }
        Intent stop = new Intent(this, AdzanPlayerService.class).setAction(ACTION_STOP);
        PendingIntent stopIntent = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent open = new Intent(this, NativeMainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("open_tab", "prayer");
        PendingIntent content = PendingIntent.getActivity(this, 2, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText("Ketuk Matikan untuk menghentikan suara.")
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setContentIntent(content)
                .setDeleteIntent(stopIntent)
                .addAction(new Notification.Action.Builder(null, "Matikan", stopIntent).build());
        if (Build.VERSION.SDK_INT < 26) builder.setPriority(Notification.PRIORITY_HIGH);
        return builder.build();
    }

    private void startPlayback(String key) {
        releasePlayer();
        audio = getSystemService(AudioManager.class);
        AudioAttributes attributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
        Uri adzan = !forceTone && PrayerReminders.MODE_ADZAN.equals(PrayerReminders.mode(this))
                ? PrayerReminders.adzanUri(this, key) : null;
        boolean tone = adzan == null;
        Uri source = tone ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) : adzan;
        if (source == null) source = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        if (source == null) { stopPlayback(); return; }
        requestFocus(attributes);
        try {
            MediaPlayer next = new MediaPlayer();
            player = next;
            next.setAudioAttributes(attributes);
            next.setDataSource(this, source);
            next.setLooping(tone);
            next.setOnCompletionListener(done -> stopPlayback());
            next.setOnErrorListener((failed, what, extra) -> {
                fallBack(key, tone);
                return true;
            });
            next.setOnPreparedListener(MediaPlayer::start);
            next.prepareAsync();
            handler.removeCallbacks(stopper);
            handler.postDelayed(stopper, tone ? ALARM_TONE_MS : MAX_PLAY_MS);
        } catch (Exception failed) {
            fallBack(key, tone);
        }
    }

    /** File pilihan pengguna gagal → adzan bawaan; adzan bawaan gagal → nada alarm; nada alarm gagal → berhenti. */
    private void fallBack(String key, boolean tone) {
        if (tone) { stopPlayback(); return; }
        if (PrayerReminders.hasCustomAdzan(this, key)) PrayerReminders.clearAdzanUri(this, key);
        else forceTone = true;
        startPlayback(key);
    }

    private void requestFocus(AudioAttributes attributes) {
        if (audio == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attributes).setOnAudioFocusChangeListener(change -> { }).build();
            audio.requestAudioFocus(focusRequest);
        } else {
            audio.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }
    }

    private void releasePlayer() {
        handler.removeCallbacks(stopper);
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            player.release();
            player = null;
        }
        if (audio != null) {
            if (Build.VERSION.SDK_INT >= 26 && focusRequest != null) audio.abandonAudioFocusRequest(focusRequest);
            else audio.abandonAudioFocus(null);
        }
    }

    private void stopPlayback() {
        releasePlayer();
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
        stopSelf();
    }

    @Override public void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }
}
