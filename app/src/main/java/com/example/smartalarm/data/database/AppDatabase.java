package com.example.smartalarm.data.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.smartalarm.data.model.Alarm;

/**
 * Room Database singleton.
 *
 * Version history:
 *  1 – khởi tạo (tất cả thuộc tính hiện tại)
 *
 * Chỉ có một instance duy nhất trong toàn app (Singleton pattern).
 * Truy cập thông qua AppDatabase.getInstance(context).
 */
@Database(entities = {Alarm.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DB_NAME = "smart_alarm_db";
    private static volatile AppDatabase instance;

    /** Trả về DAO để thao tác với bảng alarms. */
    public abstract AlarmDao alarmDao();

    /**
     * Lấy instance duy nhất của database.
     * Thread-safe với double-checked locking.
     */
    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            DB_NAME
                    )
                    // Nếu sau này cần migration, thêm .addMigrations(MIGRATION_1_2) ở đây
                    .fallbackToDestructiveMigration() // chỉ dùng khi dev, xóa khi release
                    .build();
                }
            }
        }
        return instance;
    }
}
