package com.example.smartalarm.settings;

import android.content.Context;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Liệt kê nhạc có trên máy để người dùng chọn vào danh sách shuffle.
 */
public final class RingtoneCatalog {

    private static final String TAG = "RingtoneCatalog";

    public static class Item {
        public final String title;
        public final String uri;

        Item(String title, String uri) {
            this.title = title;
            this.uri = uri;
        }
    }

    private RingtoneCatalog() {}

    /**
     * Nhạc báo thức và nhạc chuông của máy. Gộp cả hai vì riêng nhạc báo thức
     * trên nhiều máy chỉ có vài bài, không đủ để shuffle cho thú vị.
     */
    public static List<Item> load(Context context) {
        List<Item> items = new ArrayList<>();
        addType(context, RingtoneManager.TYPE_ALARM, items);
        addType(context, RingtoneManager.TYPE_RINGTONE, items);
        return items;
    }

    private static void addType(Context context, int type, List<Item> target) {
        try {
            RingtoneManager manager = new RingtoneManager(context);
            manager.setType(type);
            Cursor cursor = manager.getCursor();
            for (int i = 0; i < cursor.getCount(); i++) {
                Uri uri = manager.getRingtoneUri(i);
                if (uri == null) continue;
                String uriString = uri.toString();
                if (contains(target, uriString)) continue;
                cursor.moveToPosition(i);
                target.add(new Item(cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX), uriString));
            }
        } catch (Exception e) {
            Log.w(TAG, "Không đọc được danh sách nhạc loại " + type, e);
        }
    }

    private static boolean contains(List<Item> items, String uri) {
        for (Item item : items) {
            if (item.uri.equals(uri)) return true;
        }
        return false;
    }
}
