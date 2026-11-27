package com.rutinitas.islamic;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Menerima alarm adzan, tombol "Matikan", dan menjadwalkan ulang setelah reboot, ganti jam/zona waktu, pembaruan aplikasi. */
public final class PrayerReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (PrayerReminders.ACTION_DISMISS.equals(action)) {
            PrayerReminders.dismiss(context, intent.getIntExtra(PrayerReminders.EXTRA_NOTIFICATION, 0));
            return;
        }
        if (PrayerReminders.ACTION_ADZAN.equals(action)) {
            PrayerReminders.notifyPrayer(context, intent.getStringExtra(PrayerReminders.EXTRA_KEY),
                    intent.getStringExtra(PrayerReminders.EXTRA_TIME), intent.getLongExtra(PrayerReminders.EXTRA_AT, 0L));
        } else if (PrayerReminders.ACTION_BEFORE.equals(action)) {
            PrayerReminders.notifyBefore(context, intent.getStringExtra(PrayerReminders.EXTRA_KEY),
                    intent.getStringExtra(PrayerReminders.EXTRA_TIME), intent.getLongExtra(PrayerReminders.EXTRA_AT, 0L));
        }
        // ACTION_WIDGET_REFRESH dan siaran sistem cukup menjadwalkan ulang; schedule() juga memperbarui widget.
        PrayerReminders.schedule(context);
    }
}
