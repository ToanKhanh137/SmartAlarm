package com.example.smartalarm.ui.challenge;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.smartalarm.R;
import com.example.smartalarm.data.model.Alarm;
import com.example.smartalarm.settings.AppPreferences;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import java.util.List;

/**
 * QrChallengeActivity – quét đúng QR code để tắt báo thức.
 *
 * Nếu không có quyền camera thì thử thách là bất khả thi, nên màn hình phải nói rõ
 * lý do và mở sẵn đường tắt báo thức, thay vì hiện camera đen không giải thích gì.
 */
public class QrChallengeActivity extends ChallengeActivity {

    private static final int CAMERA_PERMISSION_REQUEST = 101;
    private static final long NO_QR_AUTO_DISMISS_MS = 5000;

    private String expectedQrCode;
    private DecoratedBarcodeView barcodeView;
    private TextView tvInstruction;
    private View llPermission;
    private View btnSkip;

    private boolean permissionRequested = false;
    private boolean scanning = false;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge_qr);

        tvInstruction = findViewById(R.id.tvInstruction);
        barcodeView   = findViewById(R.id.barcodeView);
        llPermission  = findViewById(R.id.llPermission);
        btnSkip       = findViewById(R.id.btnSkipChallenge);

        findViewById(R.id.btnOpenSettings).setOnClickListener(v -> openAppSettings());

        expectedQrCode = AppPreferences.getInstance(this).getQrCode();

        setupChallenge();

        if (expectedQrCode == null || expectedQrCode.isEmpty()) {
            // Không có mã QR nào để so sánh → thử thách vô nghĩa, tắt báo thức luôn.
            tvInstruction.setText(getString(R.string.qr_no_setup));
            Toast.makeText(this, getString(R.string.qr_no_setup_toast), Toast.LENGTH_LONG).show();
            handler.postDelayed(this::dismissAlarm, NO_QR_AUTO_DISMISS_MS);
            return;
        }

        tvInstruction.setText(getString(R.string.qr_instruction));
        barcodeView.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                onQrScanned(result.getText());
            }

            @Override
            public void possibleResultPoints(List points) {}
        });
    }

    @Override
    protected void onAlarmLoaded(@Nullable Alarm alarm) {
        // Mã QR lưu chung trong AppPreferences, không phụ thuộc từng báo thức.
    }

    private void onQrScanned(String text) {
        if (text == null) return;
        if (text.equals(expectedQrCode)) {
            Toast.makeText(this, getString(R.string.qr_success), Toast.LENGTH_SHORT).show();
            pauseScanner();
            dismissAlarm();
        } else {
            Toast.makeText(this, getString(R.string.qr_wrong), Toast.LENGTH_SHORT).show();
        }
    }

    // ===== CAMERA PERMISSION =====

    @Override
    protected void onResume() {
        super.onResume();
        if (expectedQrCode == null || expectedQrCode.isEmpty()) return;

        if (hasCameraPermission()) {
            showScanner();
        } else if (!permissionRequested) {
            // Chỉ hỏi một lần cho mỗi lần mở màn hình; hỏi lặp trong onResume sẽ bị
            // hệ thống tự từ chối và người dùng không thấy hộp thoại nào cả.
            permissionRequested = true;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
        } else {
            showPermissionNeeded();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != CAMERA_PERMISSION_REQUEST) return;

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            showScanner();
        } else {
            showPermissionNeeded();
        }
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void showScanner() {
        llPermission.setVisibility(View.GONE);
        if (!scanning) {
            barcodeView.resume();
            scanning = true;
        }
    }

    /** Không quét được thì phải cho người dùng tắt báo thức ngay, đừng bắt chờ. */
    private void showPermissionNeeded() {
        pauseScanner();
        llPermission.setVisibility(View.VISIBLE);
        if (btnSkip != null) btnSkip.setVisibility(View.VISIBLE);
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void pauseScanner() {
        if (scanning) {
            barcodeView.pause();
            scanning = false;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        pauseScanner();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
