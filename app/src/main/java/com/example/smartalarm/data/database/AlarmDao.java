package com.example.smartalarm.data.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.smartalarm.data.model.Alarm;

import java.util.List;

/**
 * DAO – Data Access Object cho bảng alarms.
 * Mọi query trả về LiveData sẽ tự cập nhật UI khi database thay đổi.
 * Mọi query đồng bộ (non-LiveData) phải chạy trên background thread.
 */
@Dao
public interface AlarmDao {

    // ===== INSERT =====

    /** Thêm báo thức mới, trả về ID được sinh ra. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Alarm alarm);

    // ===== UPDATE =====

    /** Cập nhật toàn bộ thông tin báo thức. */
    @Update
    void update(Alarm alarm);

    /**
     * Chỉ cập nhật trạng thái bật/tắt – dùng khi người dùng toggle switch trong danh sách.
     * Hiệu quả hơn @Update vì không cần load toàn bộ object.
     */
    @Query("UPDATE alarms SET isActive = :active WHERE id = :id")
    void setActive(int id, boolean active);

    /**
     * Cập nhật thời gian skip – bỏ qua lần reo tiếp theo.
     */
    @Query("UPDATE alarms SET skipUntilMillis = :skipUntilMillis WHERE id = :id")
    void setSkipUntil(int id, long skipUntilMillis);

    /** Đếm số lần đã hoãn cho lần reo hiện tại (để giới hạn báo thức quan trọng). */
    @Query("UPDATE alarms SET snoozeCount = snoozeCount + 1 WHERE id = :id")
    void incrementSnoozeCount(int id);

    /** Về 0 khi báo thức được tắt hẳn, để lần reo sau lại được hoãn đủ số lần. */
    @Query("UPDATE alarms SET snoozeCount = 0 WHERE id = :id")
    void resetSnoozeCount(int id);

    // ===== DELETE =====

    /** Xóa báo thức khỏi database. */
    @Delete
    void delete(Alarm alarm);

    /** Xóa theo ID (dùng khi không có object đầy đủ). */
    @Query("DELETE FROM alarms WHERE id = :id")
    void deleteById(int id);

    // ===== QUERY =====

    /**
     * Lấy tất cả báo thức, sắp xếp theo giờ:phút.
     * Trả về LiveData → UI tự cập nhật khi có thay đổi.
     */
    @Query("SELECT * FROM alarms ORDER BY hour ASC, minute ASC")
    LiveData<List<Alarm>> getAllAlarms();

    /**
     * Lấy chỉ các báo thức đang bật.
     * Dùng trong BootReceiver để reschedule.
     */
    @Query("SELECT * FROM alarms WHERE isActive = 1")
    List<Alarm> getActiveAlarmsSync();

    /**
     * Lấy một báo thức theo ID (đồng bộ, chạy trên background thread).
     */
    @Query("SELECT * FROM alarms WHERE id = :id LIMIT 1")
    Alarm getByIdSync(int id);

    /**
     * Lấy tất cả quick alarm đang bật (dùng khi reschedule).
     */
    @Query("SELECT * FROM alarms WHERE isActive = 1 AND isQuickAlarm = 1")
    List<Alarm> getActiveQuickAlarmsSync();
}
