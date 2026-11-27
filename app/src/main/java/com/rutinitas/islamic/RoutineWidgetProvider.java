package com.rutinitas.islamic;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

public final class RoutineWidgetProvider extends AppWidgetProvider {
    private static final String PREFS_NAME = "routine_surfaces";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        refreshAll(context);
    }

    static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] widgetIds = manager.getAppWidgetIds(
                new ComponentName(context, RoutineWidgetProvider.class));
        if (widgetIds.length == 0) return;

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean("widget_enabled", false);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.routine_widget);
        views.setTextViewText(R.id.routineWidgetTitle, enabled
                ? prefs.getString("widget_title", context.getString(R.string.routine_widget_title_default))
                : context.getString(R.string.routine_widget_title_default));
        views.setTextViewText(R.id.routineWidgetText, enabled
                ? prefs.getString("widget_text", context.getString(R.string.routine_widget_text_default))
                : "Widget belum aktif. Aktifkan dari menu Sistem di aplikasi.");

        Intent openApp = new Intent(context, NativeMainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 4201, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.routineWidgetRoot, pendingIntent);
        manager.updateAppWidget(widgetIds, views);
    }
}
