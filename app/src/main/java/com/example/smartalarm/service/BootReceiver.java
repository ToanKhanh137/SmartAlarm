package com.example.smartalarm.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.smartalarm.data.repository.AlarmRepository;

/**
 * BootReceiver – khôi phục tất cả báo thức sau các sự kiện hệ thống.
 *
 * AlarmManager mất toàn bộ lịch sau khi:
 *  - Thiết bị khởi động lại (BOOT_COMPLETED)
 *  - Người dùng thay đổi giờ hệ thống (TIME_SET)
 *  - Múi giờ thay đổi (TIMEZONE_CHANGED)
 *
 * Giải pháp: rescheduleAll() đặt lại toàn bộ báo thức đang active từ Room DB.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        String action = intent.getAction();

        boolean shouldReschedule =
                Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                Intent.ACTION_TIME_CHANGED.equals(action) ||
                Intent.ACTION_TIMEZONE_CHANGED.equals(action);

        if (shouldReschedule) {
            // Chạy trên background thread vì rescheduleAll truy cập database
            new Thread(() ->
                AlarmRepository.getInstance(context).rescheduleAll()
            ).start();
        }
    }
}
