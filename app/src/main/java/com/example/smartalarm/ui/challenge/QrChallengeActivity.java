package com.example.smartalarm.ui.challenge;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

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

    private int alarmId;
    private String expectedQrCode;
    private DecoratedBarcodeView barcodeView;
    private TextView tvInstruction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge_qr);

        alarmId = getIntent().getIntExtra(AlarmReceiver.EXTRA_ALARM_ID, -1);
        tvInstruction = findViewById(R.id.tvInstruction);
        barcodeView   = findViewById(R.id.barcodeView);

        AppPreferences prefs = AppPreferences.getInstance(this);
        expectedQrCode = prefs.getQrCode();

        if (expectedQrCode == null || expectedQrCode.isEmpty()) {
            // Chưa có QR → đề xuất tạo, tự dismiss sau 5 giây
            tvInstruction.setText(getString(R.string.qr_no_setup));
            Toast.makeText(this, "Chưa có mã QR! Tắt báo thức tự động.", Toast.LENGTH_LONG).show();
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed(this::dismissAlarm, 5000);
            return;
        }

        tvInstruction.setText(getString(R.string.qr_instruction));
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
        if (barcodeView != null) barcodeView.resume();
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
