package com.example.smartalarm.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.example.smartalarm.R;
import com.example.smartalarm.ui.main.MainActivity;

public class UpcomingReceiver extends BroadcastReceiver {

    public static final String ACTION_UPCOMING = "com.example.smartalarm.ACTION_UPCOMING";
    public static final String EXTRA_ALARM_ID = "alarm_id";
    public static final String EXTRA_ALARM_LABEL = "alarm_label";

    private static final String CHANNEL_ID = "upcoming_alarm_channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_UPCOMING.equals(intent.getAction())) {
            int alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1);
            String label = intent.getStringExtra(EXTRA_ALARM_LABEL);
            if (alarmId == -1) return;

            createNotificationChannel(context);

            Intent skipOnceIntent = new Intent(context, AlarmReceiver.class);
            skipOnceIntent.setAction(AlarmReceiver.ACTION_SKIP_ONCE);
            skipOnceIntent.putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId);
            PendingIntent piSkipOnce = PendingIntent.getBroadcast(context, alarmId + 2000,
                    skipOnceIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Intent disableIntent = new Intent(context, AlarmReceiver.class);
            disableIntent.setAction(AlarmReceiver.ACTION_DISABLE);
            disableIntent.putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId);
            PendingIntent piDisable = PendingIntent.getBroadcast(context, alarmId + 2001,
                    disableIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Intent mainIntent = new Intent(context, MainActivity.class);
            PendingIntent piMain = PendingIntent.getActivity(context, alarmId + 3000,
                    mainIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            String title = context.getString(R.string.app_name);
            String text = "Báo thức" + (label != null && !label.isEmpty() ? " '" + label + "'" : "") + " sẽ reo trong 30 phút nữa.";

            Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_alarm)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setContentIntent(piMain)
                    .addAction(R.drawable.ic_dismiss, context.getString(R.string.skip_once), piSkipOnce)
                    .addAction(R.drawable.ic_dismiss, context.getString(R.string.disable_alarm), piDisable)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build();

            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(alarmId + 1000, notification);
            }
        }
    }

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.upcoming_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }
}
