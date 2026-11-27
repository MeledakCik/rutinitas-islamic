package com.rutinitas.islamic;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** Widget tasbih: ketuk area tengah untuk menambah hitungan tanpa membuka aplikasi. */
public final class DzikirWidgetProvider extends AppWidgetProvider {
    static final String ACTION_TAP = "com.rutinitas.islamic.action.DZIKIR_TAP";
    static final String ACTION_RESET = "com.rutinitas.islamic.action.DZIKIR_RESET";
    static final String ACTION_NEXT = "com.rutinitas.islamic.action.DZIKIR_NEXT";

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        refreshAll(context);
    }

    @Override public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_TAP.equals(action)) {
            NativeDzikir.tapAndSave(context);
            return;
        }
        if (ACTION_RESET.equals(action) || ACTION_NEXT.equals(action)) {
            NativeDzikir.Counter counter = NativeDzikir.load(context);
            if (ACTION_RESET.equals(action)) NativeDzikir.reset(counter);
            else NativeDzikir.select(counter, NativeDzikir.nextIndex(counter.index));
            NativeDzikir.save(context, counter);
            NativeDzikir.vibrate(context, NativeDzikir.EVENT_NEXT_PRESET);
            refreshAll(context);
            return;
        }
        super.onReceive(context, intent);
    }

    static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, DzikirWidgetProvider.class));
        if (ids.length == 0) return;
        NativeDzikir.Counter counter = NativeDzikir.load(context);
        NativeDzikir.Preset preset = NativeDzikir.PRESETS[counter.index];
        int target = NativeDzikir.target(context, counter.index);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.dzikir_widget);
        views.setTextViewText(R.id.dzikirWidgetCount, counter.count >= target
                ? counter.count + " ✓" : String.valueOf(counter.count));
        views.setTextViewText(R.id.dzikirWidgetLabel, preset.latin + " · " + counter.count + "/" + target);
        views.setTextViewText(R.id.dzikirWidgetToday, "Hari ini " + counter.today);
        views.setContentDescription(R.id.dzikirWidgetTap, preset.latin + ", " + counter.count + " dari " + target
                + ". Ketuk untuk menambah.");
        views.setOnClickPendingIntent(R.id.dzikirWidgetTap, broadcast(context, ACTION_TAP, 4301));
        views.setOnClickPendingIntent(R.id.dzikirWidgetReset, broadcast(context, ACTION_RESET, 4302));
        views.setOnClickPendingIntent(R.id.dzikirWidgetNext, broadcast(context, ACTION_NEXT, 4303));

        Intent open = new Intent(context, NativeMainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("open_tab", "dzikir");
        views.setOnClickPendingIntent(R.id.dzikirWidgetHeader, PendingIntent.getActivity(context, 4304, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        manager.updateAppWidget(ids, views);
    }

    private static PendingIntent broadcast(Context context, String action, int request) {
        Intent intent = new Intent(context, DzikirWidgetProvider.class).setAction(action);
        return PendingIntent.getBroadcast(context, request, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
