package com.example.smartalarm.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.core.app.NotificationCompat;

import com.example.smartalarm.R;
import com.example.smartalarm.data.model.Alarm;
import com.example.smartalarm.data.repository.AlarmRepository;
import com.example.smartalarm.settings.AppPreferences;
import com.example.smartalarm.ui.ring.RingActivity;

/**
 * AlarmRingingService – Foreground Service phát báo thức.
 *
 * Là foreground service nên tiếp tục chạy dù app bị đóng.
 * Khi khởi động: load alarm từ DB → tạo notification → phát nhạc + rung.
 * Khi dừng: giải phóng MediaPlayer, Vibrator, Handler.
 */
public class AlarmRingingService extends Service {

    public static final String CHANNEL_ID_RINGING  = "alarm_ringing";
    public static final String CHANNEL_ID_UPCOMING = "alarm_upcoming";

    private static final int NOTIF_ID_RINGING = 1001;

    // Tăng âm lượng dần trong 60 giây
    private static final int GRADUAL_STEPS    = 20;
    private static final int GRADUAL_INTERVAL = 3000; // ms giữa mỗi bước (3s × 20 = 60s)

    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private Handler handler;
    private Alarm currentAlarm;
    private int currentVolume = 0;
    private int gradualStep = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        int alarmId = intent.getIntExtra(AlarmReceiver.EXTRA_ALARM_ID, -1);
        if (alarmId == -1) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // Load alarm từ database trên background thread
        new Thread(() -> {
            // Thay bằng direct DB access:
            // Thay bằng direct DB access:
            currentAlarm = com.example.smartalarm.data.database.AppDatabase
                    .getInstance(this).alarmDao().getByIdSync(alarmId);

            if (currentAlarm == null) {
                stopSelf();
                return;
            }
            // Chuyển lên main thread để cập nhật UI và khởi động media
            handler.post(() -> startRinging(currentAlarm));
        }).start();

        return START_STICKY;
    }

    // ===== START RINGING =====

    private void startRinging(Alarm alarm) {
        // 1. Tạo notification full-screen (hiển thị kể cả khi màn hình khóa)
        Notification notification = buildRingingNotification(alarm);
        startForeground(NOTIF_ID_RINGING, notification);

        // Force start Activity để đảm bảo UI luôn hiện
        Intent fullScreenIntent = new Intent(this, RingActivity.class);
        fullScreenIntent.putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id);
        fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        startActivity(fullScreenIntent);

        // 2. Phát nhạc
        startMedia(alarm);

        // 3. Rung (nếu bật)
        if (alarm.vibrate) {
            startVibration();
        }

        // 4. Auto action (nếu cài đặt)
        if (alarm.autoAction != Alarm.AUTO_NONE) {
            scheduleAutoAction(alarm);
        }
    }

    // ===== MEDIA =====

    private void startMedia(Alarm alarm) {
        try {
            // Chọn URI nhạc
            Uri ringtoneUri;
            if ("silent".equals(alarm.ringtoneUri)) {
                // Im lặng: chỉ rung, không phát nhạc
                return;
            } else if (alarm.ringtoneUri == null || alarm.ringtoneUri.isEmpty()) {
                ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            } else {
                ringtoneUri = Uri.parse(alarm.ringtoneUri);
            }

            mediaPlayer = new MediaPlayer();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build());
            } else {
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            }
            mediaPlayer.setDataSource(this, ringtoneUri);
            mediaPlayer.setLooping(true);

            // Cài đặt âm lượng ban đầu
            float vol = alarm.gradualVolume ? 0f : alarm.volume / 100f;
            mediaPlayer.setVolume(vol, vol);

            mediaPlayer.prepare();
            mediaPlayer.start();

            // Tăng âm lượng dần nếu bật
            if (alarm.gradualVolume) {
                startGradualVolume(alarm.volume);
            }

        } catch (Exception e) {
            // Fallback: thử dùng ringtone mặc định
            try {
                if (mediaPlayer != null) {
                    mediaPlayer.release();
                    mediaPlayer = null;
                }
                Uri defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
                mediaPlayer.setDataSource(this, defaultUri);
                mediaPlayer.setLooping(true);
                float vol = alarm.volume / 100f;
                mediaPlayer.setVolume(vol, vol);
                mediaPlayer.prepare();
                mediaPlayer.start();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private void startGradualVolume(int targetVolume) {
        gradualStep = 0;
        currentVolume = 0;
        Runnable gradualRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer == null || !mediaPlayer.isPlaying()) return;
                if (gradualStep >= GRADUAL_STEPS) return;

                gradualStep++;
                currentVolume = (targetVolume * gradualStep) / GRADUAL_STEPS;
                float vol = currentVolume / 100f;
                mediaPlayer.setVolume(vol, vol);

                if (gradualStep < GRADUAL_STEPS) {
                    handler.postDelayed(this, GRADUAL_INTERVAL);
                }
            }
        };
        handler.postDelayed(gradualRunnable, GRADUAL_INTERVAL);
    }

    // ===== VIBRATION =====

    private void startVibration() {
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator == null) return;

        // Pattern: dừng 0ms, rung 500ms, dừng 500ms (lặp vô hạn từ index 0)
        long[] pattern = {0, 500, 500};

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
        } else {
            vibrator.vibrate(pattern, 0);
        }
    }

    // ===== AUTO ACTION =====

    private void scheduleAutoAction(Alarm alarm) {
        long delayMs = (long) alarm.autoAfterMinutes * 60 * 1000;
        handler.postDelayed(() -> {
            if (alarm.autoAction == Alarm.AUTO_SNOOZE) {
                // Gửi broadcast snooze
                Intent intent = new Intent(this, AlarmReceiver.class);
                intent.setAction(AlarmReceiver.ACTION_SNOOZE);
                intent.putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id);
                sendBroadcast(intent);
            } else if (alarm.autoAction == Alarm.AUTO_DISMISS) {
                Intent intent = new Intent(this, AlarmReceiver.class);
                intent.setAction(AlarmReceiver.ACTION_DISMISS);
                intent.putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id);
                sendBroadcast(intent);
            }
        }, delayMs);
    }

    // ===== NOTIFICATION =====

    private Notification buildRingingNotification(Alarm alarm) {
        // Intent mở RingActivity khi tap notification
        Intent fullScreenIntent = new Intent(this, RingActivity.class);
        fullScreenIntent.putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id);
        fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_NO_USER_ACTION);

        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            piFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent fullScreenPI = PendingIntent.getActivity(
                this, alarm.id, fullScreenIntent, piFlags);

        // Intent Snooze (action button trên notification)
        Intent snoozeIntent = new Intent(this, AlarmReceiver.class);
        snoozeIntent.setAction(AlarmReceiver.ACTION_SNOOZE);
        snoozeIntent.putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id);
        PendingIntent snoozePI = PendingIntent.getBroadcast(
                this, alarm.id + 2000, snoozeIntent, piFlags);

        // Intent Dismiss (action button trên notification)
        Intent dismissIntent = new Intent(this, AlarmReceiver.class);
        dismissIntent.setAction(AlarmReceiver.ACTION_DISMISS);
        dismissIntent.putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id);
        PendingIntent dismissPI = PendingIntent.getBroadcast(
                this, alarm.id + 3000, dismissIntent, piFlags);

        String title = alarm.getDisplayLabel();
        String content = String.format("%02d:%02d", alarm.hour, alarm.minute);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID_RINGING)
                .setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setFullScreenIntent(fullScreenPI, true)
                .setContentIntent(fullScreenPI);

        // Thêm nút Snooze nếu được bật trong settings
        AppPreferences prefs = AppPreferences.getInstance(this);
        if (prefs.isSnoozeEnabled() && alarm.challengeType == Alarm.CHALLENGE_NONE) {
            builder.addAction(R.drawable.ic_snooze,
                    getString(R.string.notification_action_snooze), snoozePI);
        }

        // Nút Tắt (chỉ khi không có challenge)
        if (alarm.challengeType == Alarm.CHALLENGE_NONE) {
            builder.addAction(R.drawable.ic_dismiss,
                    getString(R.string.notification_action_dismiss), dismissPI);
        }

        return builder.build();
    }

    // ===== NOTIFICATION CHANNELS =====

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);

            // Kênh đang reo – ưu tiên cao, không có âm thanh (âm thanh do MediaPlayer quản lý)
            NotificationChannel ringingChannel = new NotificationChannel(
                    CHANNEL_ID_RINGING,
                    getString(R.string.ringing_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
            );
            ringingChannel.setSound(null, null); // tắt sound của notification
            ringingChannel.enableVibration(false); // tắt vibration của notification
            ringingChannel.setBypassDnd(true);
            nm.createNotificationChannel(ringingChannel);

            // Kênh sắp tới – ưu tiên mặc định
            NotificationChannel upcomingChannel = new NotificationChannel(
                    CHANNEL_ID_UPCOMING,
                    getString(R.string.upcoming_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            nm.createNotificationChannel(upcomingChannel);
        }
    }

    // ===== STOP =====

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopMedia();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }

    private void stopMedia() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mediaPlayer = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Không dùng bound service
    }
}
