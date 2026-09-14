package com.example.smartalarm.data.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.smartalarm.data.model.Alarm;

/**
 * Room Database singleton.
 *
 * Version history:
 *  1 – khởi tạo (tất cả thuộc tính hiện tại)
 *  2 – thêm cột shuffleRingtone
 *  3 – thêm cột importantAlarm và snoozeCount
 *  4 – thêm cột shufflePlaylist
 *
 * Chỉ có một instance duy nhất trong toàn app (Singleton pattern).
 * Truy cập thông qua AppDatabase.getInstance(context).
 */
@Database(entities = {Alarm.class}, version = 4, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    /** Giữ lại báo thức người dùng đã đặt khi cập nhật app. */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE alarms ADD COLUMN shuffleRingtone INTEGER NOT NULL DEFAULT 0");
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE alarms ADD COLUMN importantAlarm INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE alarms ADD COLUMN snoozeCount INTEGER NOT NULL DEFAULT 0");
        }
    };

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE alarms ADD COLUMN shufflePlaylist TEXT");
        }
    };

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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build();
                }
            }
        }
        return instance;
    }
}
