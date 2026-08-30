package com.example.smartalarm.ui.challenge;

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import com.example.smartalarm.R;
import com.example.smartalarm.service.AlarmReceiver;
import com.example.smartalarm.settings.AppPreferences;
import com.example.smartalarm.ui.common.BaseActivity;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import java.util.List;

/**
 * QrChallengeActivity – quét đúng QR code để tắt báo thức.
 * Dùng ZXing embedded scanner.
 * So sánh nội dung quét được với qrCode lưu trong AppPreferences.
 */
public class QrChallengeActivity extends BaseActivity {

    private static final int CAMERA_PERMISSION_REQUEST = 101;
    private int alarmId;
    private String expectedQrCode;
    private DecoratedBarcodeView barcodeView;
    private TextView tvInstruction;
    private Button btnEmergencyStop;
    private android.os.Handler timeoutHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge_qr);

        alarmId = getIntent().getIntExtra(AlarmReceiver.EXTRA_ALARM_ID, -1);
        tvInstruction = findViewById(R.id.tvInstruction);
        barcodeView   = findViewById(R.id.barcodeView);
        btnEmergencyStop = findViewById(R.id.btnEmergencyStop);

        AppPreferences prefs = AppPreferences.getInstance(this);
        expectedQrCode = prefs.getQrCode();

        if (expectedQrCode == null || expectedQrCode.isEmpty()) {
            tvInstruction.setText(getString(R.string.qr_no_setup));
            Toast.makeText(this, getString(R.string.qr_no_setup_toast), Toast.LENGTH_LONG).show();
            timeoutHandler.postDelayed(this::dismissAlarm, 5000);
            return;
        }

        tvInstruction.setText(getString(R.string.qr_instruction));
        
        btnEmergencyStop.setOnClickListener(v -> {
            Toast.makeText(this, getString(R.string.emergency_stop), Toast.LENGTH_SHORT).show();
            dismissAlarm();
        });
        
        timeoutHandler.postDelayed(() -> {
            btnEmergencyStop.setVisibility(View.VISIBLE);
        }, 15000);
        barcodeView.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                if (result.getText().equals(expectedQrCode)) {
                    Toast.makeText(QrChallengeActivity.this,
                            getString(R.string.qr_success), Toast.LENGTH_SHORT).show();
                    barcodeView.pause();
                    dismissAlarm();
                } else {
                    Toast.makeText(QrChallengeActivity.this,
                            getString(R.string.qr_wrong), Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void possibleResultPoints(List points) {}
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            if (barcodeView != null) barcodeView.resume();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (barcodeView != null) barcodeView.resume();
            } else {
                Toast.makeText(this, getString(R.string.qr_camera_permission), Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (barcodeView != null) barcodeView.pause();
    }

    private void dismissAlarm() {
        Intent intent = new Intent(this, AlarmReceiver.class);
        intent.setAction(AlarmReceiver.ACTION_DISMISS);
        intent.putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId);
        sendBroadcast(intent);
        finishAffinity();
    }

    @Override
    public void onBackPressed() {}
}
