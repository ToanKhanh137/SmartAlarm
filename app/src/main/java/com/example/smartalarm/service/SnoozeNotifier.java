package com.example.smartalarm.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.example.smartalarm.R;
import com.example.smartalarm.settings.LocaleHelper;
import com.example.smartalarm.ui.main.MainActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Thông báo xác nhận đã hoãn báo thức.
 *
 * Không có nó thì người dùng bấm hoãn xong chỉ thấy báo thức im bặt, không biết
 * là đã hoãn thành công hay báo thức bị mất.
 */
final class SnoozeNotifier {

    private static final int NOTIF_ID_BASE = 5000;

    private SnoozeNotifier() {}

    static void show(Context rawContext, int alarmId, long triggerAtMillis) {
        Context context = LocaleHelper.wrap(rawContext);
        createChannel(context);

        String time = new SimpleDateFormat("HH:mm", Locale.US).format(new Date(triggerAtMillis));

        Intent openApp = new Intent(context, MainActivity.class);
        PendingIntent contentPI = PendingIntent.getActivity(context, alarmId + NOTIF_ID_BASE,
                openApp, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Cho phép hủy hoãn để tắt hẳn báo thức mà không cần đợi nó reo lại
        Intent dismiss = new Intent(context, AlarmReceiver.class);
        dismiss.setAction(AlarmReceiver.ACTION_DISMISS);
        dismiss.putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId);
        PendingIntent dismissPI = PendingIntent.getBroadcast(context, alarmId + NOTIF_ID_BASE + 1,
                dismiss, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        nm.notify(NOTIF_ID_BASE + alarmId, new NotificationCompat.Builder(
                        context, AlarmRingingService.CHANNEL_ID_UPCOMING)
                .setSmallIcon(R.drawable.ic_snooze)
                .setContentTitle(context.getString(R.string.snoozed_title))
                .setContentText(context.getString(R.string.snoozed_until, time))
                .setContentIntent(contentPI)
                .addAction(R.drawable.ic_dismiss,
                        context.getString(R.string.notification_action_dismiss), dismissPI)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build());
    }

    /** Kênh này do AlarmRingingService tạo, nhưng tạo lại cho chắc nếu service đã bị hủy. */
    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel(
                AlarmRingingService.CHANNEL_ID_UPCOMING,
                context.getString(R.string.upcoming_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT));
    }
}
