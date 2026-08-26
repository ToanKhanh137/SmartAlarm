package com.example.smartalarm.ui.qr;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.smartalarm.R;
import com.example.smartalarm.settings.AppPreferences;
import com.example.smartalarm.ui.common.BaseActivity;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.io.OutputStream;
import java.util.UUID;

public class QrGeneratorActivity extends BaseActivity {

    private ImageView ivQrCode;
    private Bitmap currentQrBitmap;
    private AppPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_generator);

        prefs = AppPreferences.getInstance(this);

        ivQrCode = findViewById(R.id.ivQrCode);
        findViewById(R.id.btnGenerate).setOnClickListener(v -> generateNewQr());
        findViewById(R.id.btnSave).setOnClickListener(v -> saveQrToGallery());

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> finish());

        // Load existing or generate new
        if (prefs.hasQrCode()) {
            loadQrImage(prefs.getQrCode());
        } else {
            generateNewQr();
        }
    }

    private void generateNewQr() {
        String newCode = "SMART_ALARM_" + UUID.randomUUID().toString();
        prefs.setQrCode(newCode);
        loadQrImage(newCode);
    }

    private void loadQrImage(String code) {
        try {
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            currentQrBitmap = barcodeEncoder.encodeBitmap(code, BarcodeFormat.QR_CODE, 500, 500);
            ivQrCode.setImageBitmap(currentQrBitmap);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveQrToGallery() {
        if (currentQrBitmap == null) return;
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
                return;
            }
        }
        
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "SmartAlarm_QR_" + System.currentTimeMillis() + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SmartAlarm");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);
            }

            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                OutputStream out = getContentResolver().openOutputStream(uri);
                currentQrBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                if (out != null) out.close();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear();
                    values.put(MediaStore.Images.Media.IS_PENDING, 0);
                    getContentResolver().update(uri, values, null, null);
                }
                
                Toast.makeText(this, getString(R.string.qr_saved), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Lỗi khi lưu ảnh", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            saveQrToGallery();
        }
    }
}
