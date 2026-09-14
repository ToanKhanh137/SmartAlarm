package com.example.smartalarm.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.smartalarm.R;
import com.example.smartalarm.data.model.Alarm;
import com.example.smartalarm.settings.AppPreferences;
import com.example.smartalarm.settings.LocaleHelper;
import com.example.smartalarm.ui.ring.RingActivity;

/**
 * AlarmRingingService – Foreground Service phát báo thức.
 *
 * Là foreground service nên tiếp tục chạy dù app bị đóng.
 * Khi khởi động: load alarm từ DB → tạo notification → phát nhạc + rung.
 */
public class AlarmRingingService extends Service {

    private static final String TAG = "AlarmRingingService";

    public static final String CHANNEL_ID_RINGING  = "alarm_ringing";
    public static final String CHANNEL_ID_UPCOMING = "alarm_upcoming";

    /** Dừng hẳn báo thức. Dùng thay stopService() để tránh race khi service đang load DB. */
    public static final String ACTION_STOP = "com.example.smartalarm.service.STOP";

    /**
     * Phát khi báo thức bắt đầu reo, để Activity đang mở nhảy sang màn hình báo thức
     * ngay lập tức (onResume không chạy lại nếu Activity vốn đã ở tiền cảnh).
     */
    public static final String ACTION_RINGING_STARTED = "com.example.smartalarm.service.RINGING_STARTED";

    /** Màn hình báo thức bị rời khỏi trong lúc vẫn đang reo → kéo nó trở lại. */
    public static final String ACTION_REASSERT = "com.example.smartalarm.service.REASSERT";

    /** Không kéo lại dồn dập, tránh tạo vòng lặp làm máy không dùng được. */
    private static final long REASSERT_INTERVAL_MS = 3000;

    private static final int NOTIF_ID_RINGING = 1001;

    // Tăng âm lượng dần trong 60 giây
    private static final int GRADUAL_STEPS    = 20;
    private static final int GRADUAL_INTERVAL = 3000; // ms giữa mỗi bước (3s × 20 = 60s)

    /** Hai lần bấm nút nguồn trong khoảng này được coi là bấm đúp. */
    private static final long DOUBLE_PRESS_WINDOW_MS = 2500;

    // Báo thức thường: kêu quá lâu thì nhỏ dần
    private static final long FADE_START_MS    = 3 * 60 * 1000L; // sau 3 phút
    private static final int  FADE_STEPS       = 20;
    private static final long FADE_INTERVAL_MS = 6000;           // nhỏ dần trong 2 phút
    private static final int  FADE_FLOOR_PERCENT = 25;           // không nhỏ hơn 25% mức đã đặt

    public static boolean isRinging = false;
    public static int ringingAlarmId = -1;

    /**
     * MediaPlayer đang phát, giữ ở static để instance sau có thể thu hồi player mồ côi
     * của instance trước (xảy ra nếu process bị kill giữa lúc đang phát).
     */
    private static MediaPlayer activePlayer;

    private Vibrator vibrator;
    private Handler handler;
    private Context localeContext;
    private Alarm currentAlarm;
    private PowerManager.WakeLock wakeLock;
    private ScreenButtonReceiver screenButtonReceiver;

    /** Đã có yêu cầu dừng → mọi việc còn dở (load DB xong mới phát nhạc) phải bỏ qua. */
    private volatile boolean stopRequested = false;

    private int currentVolume = 0;
    private int gradualStep = 0;
    private long lastReassertMs = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        localeContext = LocaleHelper.wrap(this);
        releaseActivePlayer(); // thu hồi player mồ côi nếu có
        createNotificationChannels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopEverything();
            return START_NOT_STICKY;
        }

        if (ACTION_STOP.equals(intent.getAction())) {
            stopEverything();
            return START_NOT_STICKY;
        }

        if (ACTION_REASSERT.equals(intent.getAction())) {
            reassertRingingScreen();
            return START_NOT_STICKY;
        }

        int alarmId = intent.getIntExtra(AlarmReceiver.EXTRA_ALARM_ID, -1);
        if (alarmId == -1) {
            stopEverything();
            return START_NOT_STICKY;
        }

        stopRequested = false;

        // Load alarm từ database trên background thread
        new Thread(() -> {
            Alarm loaded = com.example.smartalarm.data.database.AppDatabase
                    .getInstance(this).alarmDao().getByIdSync(alarmId);

            if (loaded == null || stopRequested) {
                handler.post(this::stopEverything);
                return;
            }

            currentAlarm = loaded;
            isRinging = true;
            ringingAlarmId = alarmId;

            handler.post(() -> {
                // Người dùng có thể đã tắt/xóa báo thức trong lúc đang load DB.
                if (stopRequested) {
                    stopEverything();
                    return;
                }
                startRinging(loaded);
            });
        }).start();

        // START_NOT_STICKY: không tự khởi động lại với intent null nếu process bị kill,
        // vì AlarmManager mới là nguồn duy nhất quyết định khi nào báo thức reo.
        return START_NOT_STICKY;
    }

    // ===== START RINGING =====

    private void startRinging(Alarm alarm) {
        // Bỏ các tác vụ hẹn của báo thức trước (tăng âm lượng dần, auto snooze/dismiss)
        handler.removeCallbacksAndMessages(null);

        // 0. Bật màn hình. Không có wake lock thì máy đang tắt màn sẽ chỉ kêu
        // trong bóng tối, RingActivity không bao giờ hiện ra.
        acquireWakeLock();
        registerScreenButtonReceiver();

        // 1. Notification full-screen (hiển thị kể cả khi màn hình khóa)
        startForeground(NOTIF_ID_RINGING, buildRingingNotification(alarm));

        // 2. Force start Activity để đảm bảo UI luôn hiện
        Intent fullScreenIntent = new Intent(this, RingActivity.class);
        fullScreenIntent.putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id);
        fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_USER_ACTION
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            startActivity(fullScreenIntent);
        } catch (Exception e) {
            // Bị hạn chế mở Activity từ nền – full-screen intent của notification sẽ lo
            Log.w(TAG, "Không mở được RingActivity trực tiếp", e);
        }

        // Activity nào đang mở thì tự nhảy sang màn hình báo thức
        Intent started = new Intent(ACTION_RINGING_STARTED);
        started.putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id);
        started.setPackage(getPackageName());
        sendBroadcast(started);

        // 3. Phát nhạc
        startMedia(alarm);

        // 4. Rung (nếu bật)
        if (alarm.vibrate) startVibration();

        // 4b. Báo thức thường kêu quá lâu thì nhỏ dần cho đỡ phiền.
        // Báo thức quan trọng thì không, phải giữ nguyên đến khi người dùng dậy.
        if (!alarm.importantAlarm) scheduleCourtesyFade(alarm);

        // 5. Auto action (nếu cài đặt)
        if (alarm.autoAction != Alarm.AUTO_NONE) scheduleAutoAction(alarm);
    }

    /**
     * Kéo màn hình báo thức trở lại khi người dùng thoát ra (bấm Home) mà chưa tắt báo thức.
     * Đăng lại notification để full-screen intent kích hoạt lần nữa – cách mở Activity từ
     * nền đáng tin cậy nhất, vì startActivity trực tiếp bị hệ thống hạn chế.
     */
    private void reassertRingingScreen() {
        Alarm alarm = currentAlarm;
        if (!isRinging || alarm == null || stopRequested) return;

        long now = System.currentTimeMillis();
        if (now - lastReassertMs < REASSERT_INTERVAL_MS) return;
        lastReassertMs = now;

        acquireWakeLock();

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            // Hủy rồi đăng lại: đăng đè lên notification cũ sẽ không kích hoạt
            // full-screen intent thêm lần nào nữa.
            nm.cancel(NOTIF_ID_RINGING);
            nm.notify(NOTIF_ID_RINGING, buildRingingNotification(alarm));
        }

        Intent ring = new Intent(this, RingActivity.class);
        ring.putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id);
        ring.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            startActivity(ring);
        } catch (Exception e) {
            Log.w(TAG, "Không kéo lại được màn hình báo thức", e);
        }
    }

    // ===== WAKE LOCK =====

    /**
     * Bật màn hình khi báo thức reo. Dùng SCREEN_BRIGHT_WAKE_LOCK vì
     * PARTIAL_WAKE_LOCK chỉ giữ CPU chạy chứ không đánh thức màn hình.
     */
    private void acquireWakeLock() {
        releaseWakeLock();
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm == null) return;
            //noinspection deprecation – các cờ này vẫn là cách duy nhất để bật màn hình
            wakeLock = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                            | PowerManager.ACQUIRE_CAUSES_WAKEUP
                            | PowerManager.ON_AFTER_RELEASE,
                    "SmartAlarm:ringing");
            wakeLock.setReferenceCounted(false);
            // Tự nhả sau 10 phút phòng khi service chết bất thường, không giữ màn hình mãi
            wakeLock.acquire(10 * 60 * 1000L);
        } catch (Exception e) {
            Log.w(TAG, "Không lấy được wake lock", e);
        }
    }

    private void releaseWakeLock() {
        if (wakeLock == null) return;
        try {
            if (wakeLock.isHeld()) wakeLock.release();
        } catch (Exception ignored) {
        } finally {
            wakeLock = null;
        }
    }

    // ===== NÚT NGUỒN =====

    /**
     * Không app nào chặn được nút nguồn trực tiếp, nhưng mỗi lần bấm hệ thống phát
     * ACTION_SCREEN_ON/OFF. Hai lần đổi trạng thái liên tiếp = bấm nguồn hai lần.
     */
    private class ScreenButtonReceiver extends BroadcastReceiver {
        private int presses = 0;
        private long firstPressMs = 0;

        @Override
        public void onReceive(Context context, Intent intent) {
            long now = System.currentTimeMillis();
            if (now - firstPressMs > DOUBLE_PRESS_WINDOW_MS) {
                presses = 0;
                firstPressMs = now;
            }
            presses++;
            if (presses >= 2) {
                presses = 0;
                snoozeFromPowerButton();
            }
        }
    }

    private void registerScreenButtonReceiver() {
        if (screenButtonReceiver != null) return;
        screenButtonReceiver = new ScreenButtonReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenButtonReceiver, filter);
    }

    private void unregisterScreenButtonReceiver() {
        if (screenButtonReceiver == null) return;
        try {
            unregisterReceiver(screenButtonReceiver);
        } catch (Exception ignored) {
        } finally {
            screenButtonReceiver = null;
        }
    }

    private void snoozeFromPowerButton() {
        Alarm alarm = currentAlarm;
        if (alarm == null || !AppPreferences.getInstance(this).isSnoozeEnabled()) return;
        if (!alarm.canSnoozeAgain()) return;

        Intent intent = new Intent(this, AlarmReceiver.class);
        intent.setAction(AlarmReceiver.ACTION_SNOOZE);
        intent.putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id);
        sendBroadcast(intent);
    }

    // ===== MEDIA =====

    private void startMedia(Alarm alarm) {
        if (stopRequested) return;

        // Báo thức khác có thể đang reo (hai báo thức cùng giờ) – giải phóng trước
        // để không phát chồng hai bản nhạc.
        releaseActivePlayer();

        // Im lặng: chỉ rung, không phát nhạc
        if ("silent".equals(alarm.ringtoneUri)) return;

        Uri defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);

        Uri wanted = null;
        boolean hasCustom = false;
        if (alarm.shuffleRingtone) {
            wanted = pickRandomAlarmSound();
            hasCustom = wanted != null;
        }
        if (wanted == null) {
            hasCustom = alarm.ringtoneUri != null && !alarm.ringtoneUri.isEmpty();
            wanted = hasCustom ? Uri.parse(alarm.ringtoneUri) : defaultUri;
        }

        if (!play(alarm, wanted) && hasCustom) {
            // Nhạc người dùng chọn không đọc được (thường do thiếu quyền READ_MEDIA_AUDIO)
            Log.w(TAG, "Không phát được nhạc chuông đã chọn, dùng mặc định: " + alarm.ringtoneUri);
            play(alarm, defaultUri);
        }
    }

    /** Chọn ngẫu nhiên một nhạc báo thức của hệ thống. null nếu máy không có gì để chọn. */
    private Uri pickRandomAlarmSound() {
        try {
            RingtoneManager manager = new RingtoneManager(this);
            manager.setType(RingtoneManager.TYPE_ALARM);
            int count = manager.getCursor().getCount();
            if (count <= 0) return null;
            return manager.getRingtoneUri(new java.util.Random().nextInt(count));
        } catch (Exception e) {
            Log.w(TAG, "Không lấy được danh sách nhạc báo thức để shuffle", e);
            return null;
        }
    }

    /** Trả về true nếu phát thành công. */
    private boolean play(Alarm alarm, Uri uri) {
        if (uri == null) return false;
        MediaPlayer player = new MediaPlayer();
        try {
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            player.setDataSource(this, uri);
            player.setLooping(true);

            float vol = alarm.gradualVolume ? 0f : alarm.volume / 100f;
            player.setVolume(vol, vol);
            player.prepare();

            // Kiểm tra lần cuối: nếu đã có yêu cầu dừng thì không phát,
            // tránh để lại MediaPlayer mồ côi kêu mãi không tắt được.
            if (stopRequested) {
                player.release();
                return true;
            }

            player.start();
            activePlayer = player;

            if (alarm.gradualVolume) startGradualVolume(alarm.volume);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Không phát được " + uri, e);
            try {
                player.release();
            } catch (Exception ignored) {}
            return false;
        }
    }

    private void startGradualVolume(int targetVolume) {
        gradualStep = 0;
        currentVolume = 0;
        Runnable gradualRunnable = new Runnable() {
            @Override
            public void run() {
                MediaPlayer player = activePlayer;
                if (player == null || stopRequested) return;
                if (gradualStep >= GRADUAL_STEPS) return;

                gradualStep++;
                currentVolume = (targetVolume * gradualStep) / GRADUAL_STEPS;
                float vol = currentVolume / 100f;
                try {
                    player.setVolume(vol, vol);
                } catch (IllegalStateException e) {
                    return; // player đã release
                }

                if (gradualStep < GRADUAL_STEPS) {
                    handler.postDelayed(this, GRADUAL_INTERVAL);
                }
            }
        };
        handler.postDelayed(gradualRunnable, GRADUAL_INTERVAL);
    }

    /**
     * Sau FADE_START_MS, hạ âm lượng dần xuống FADE_FLOOR_PERCENT của mức đã đặt.
     * Không bao giờ hạ về 0 – nó vẫn là báo thức, chỉ là bớt chói tai khi kêu mãi.
     */
    private void scheduleCourtesyFade(Alarm alarm) {
        handler.postDelayed(new Runnable() {
            private int step = 0;

            @Override
            public void run() {
                MediaPlayer player = activePlayer;
                if (player == null || stopRequested || step >= FADE_STEPS) return;

                step++;
                float startVol = alarm.volume / 100f;
                float floorVol = startVol * FADE_FLOOR_PERCENT / 100f;
                float vol = startVol + (floorVol - startVol) * step / FADE_STEPS;
                try {
                    player.setVolume(vol, vol);
                } catch (IllegalStateException e) {
                    return;
                }
                if (step < FADE_STEPS) handler.postDelayed(this, FADE_INTERVAL_MS);
            }
        }, FADE_START_MS);
    }

    // ===== VIBRATION =====

    private void startVibration() {
        stopVibration();
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
            String action = alarm.autoAction == Alarm.AUTO_SNOOZE
                    ? AlarmReceiver.ACTION_SNOOZE
                    : AlarmReceiver.ACTION_DISMISS;
            Intent intent = new Intent(this, AlarmReceiver.class);
            intent.setAction(action);
            intent.putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id);
            sendBroadcast(intent);
        }, delayMs);
    }

    // ===== NOTIFICATION =====

    private Notification buildRingingNotification(Alarm alarm) {
        Intent fullScreenIntent = new Intent(this, RingActivity.class);
        fullScreenIntent.putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id);
        fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_NO_USER_ACTION);

        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

        PendingIntent fullScreenPI = PendingIntent.getActivity(
                this, alarm.id, fullScreenIntent, piFlags);

        Intent snoozeIntent = new Intent(this, AlarmReceiver.class);
        snoozeIntent.setAction(AlarmReceiver.ACTION_SNOOZE);
        snoozeIntent.putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id);
        PendingIntent snoozePI = PendingIntent.getBroadcast(
                this, alarm.id + 2000, snoozeIntent, piFlags);

        Intent dismissIntent = new Intent(this, AlarmReceiver.class);
        dismissIntent.setAction(AlarmReceiver.ACTION_DISMISS);
        dismissIntent.putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id);
        PendingIntent dismissPI = PendingIntent.getBroadcast(
                this, alarm.id + 3000, dismissIntent, piFlags);

        String time = String.format(java.util.Locale.US, "%02d:%02d", alarm.hour, alarm.minute);
        boolean hasLabel = alarm.label != null && !alarm.label.isEmpty();
        String title = hasLabel ? time + " · " + alarm.label : time;

        // Nói rõ cần làm gì để tắt, thay vì chỉ hiện lại giờ.
        String content = alarm.challengeType == Alarm.CHALLENGE_NONE
                ? localeContext.getString(R.string.notification_ringing_plain)
                : localeContext.getString(R.string.notification_ringing_challenge,
                        challengeName(alarm.challengeType));

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID_RINGING)
                .setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setFullScreenIntent(fullScreenPI, true)
                .setContentIntent(fullScreenPI);

        AppPreferences prefs = AppPreferences.getInstance(this);
        boolean plainAlarm = alarm.challengeType == Alarm.CHALLENGE_NONE;

        if (prefs.isSnoozeEnabled() && alarm.canSnoozeAgain()) {
            int left = alarm.snoozesLeft();
            String label = left < 0
                    ? localeContext.getString(R.string.notification_action_snooze)
                    : localeContext.getString(R.string.notification_action_snooze_left, left);
            builder.addAction(R.drawable.ic_snooze, label, snoozePI);
        }

        // Chỉ báo thức không có thử thách mới tắt được thẳng từ notification.
        // Báo thức có thử thách phải mở màn hình thử thách (ở đó vẫn có nút bỏ qua
        // sau 60 giây nếu cảm biến/quyền có vấn đề), nếu không thì thử thách vô nghĩa.
        if (plainAlarm) {
            builder.addAction(R.drawable.ic_dismiss,
                    localeContext.getString(R.string.notification_action_dismiss), dismissPI);
        } else {
            builder.addAction(R.drawable.ic_alarm,
                    localeContext.getString(R.string.notification_action_open), fullScreenPI);
        }

        return builder.build();
    }

    private String challengeName(int challengeType) {
        switch (challengeType) {
            case Alarm.CHALLENGE_MATH:  return localeContext.getString(R.string.challenge_math);
            case Alarm.CHALLENGE_SHAKE: return localeContext.getString(R.string.challenge_shake);
            case Alarm.CHALLENGE_SQUAT: return localeContext.getString(R.string.challenge_squat);
            case Alarm.CHALLENGE_STEP:  return localeContext.getString(R.string.challenge_step);
            case Alarm.CHALLENGE_QR:    return localeContext.getString(R.string.challenge_qr);
            default: return localeContext.getString(R.string.challenge_none);
        }
    }

    // ===== NOTIFICATION CHANNELS =====

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);

            // Kênh đang reo – ưu tiên cao, không có âm thanh (âm thanh do MediaPlayer quản lý)
            NotificationChannel ringingChannel = new NotificationChannel(
                    CHANNEL_ID_RINGING,
                    localeContext.getString(R.string.ringing_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
            );
            ringingChannel.setSound(null, null);
            ringingChannel.enableVibration(false);
            ringingChannel.setBypassDnd(true);
            nm.createNotificationChannel(ringingChannel);

            NotificationChannel upcomingChannel = new NotificationChannel(
                    CHANNEL_ID_UPCOMING,
                    localeContext.getString(R.string.upcoming_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            nm.createNotificationChannel(upcomingChannel);
        }
    }

    // ===== STOP =====

    private void stopEverything() {
        stopRequested = true;
        isRinging = false;
        ringingAlarmId = -1;
        if (handler != null) handler.removeCallbacksAndMessages(null);
        releaseActivePlayer();
        stopVibration();
        unregisterScreenButtonReceiver();
        releaseWakeLock();
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopRequested = true;
        isRinging = false;
        ringingAlarmId = -1;
        if (handler != null) handler.removeCallbacksAndMessages(null);
        releaseActivePlayer();
        stopVibration();
        unregisterScreenButtonReceiver();
        releaseWakeLock();
    }

    private static synchronized void releaseActivePlayer() {
        if (activePlayer == null) return;
        try {
            if (activePlayer.isPlaying()) activePlayer.stop();
        } catch (Exception e) {
            Log.w(TAG, "stop() thất bại", e);
        }
        try {
            activePlayer.release();
        } catch (Exception e) {
            Log.w(TAG, "release() thất bại", e);
        }
        activePlayer = null;
    }

    private void stopVibration() {
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
