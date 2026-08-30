package com.example.smartalarm.ui.main;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartalarm.R;
import com.example.smartalarm.data.model.Alarm;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView Adapter cho danh sách báo thức.
 * Hiển thị: giờ, label, ngày lặp, trạng thái, challenge icon.
 */
public class AlarmAdapter extends RecyclerView.Adapter<AlarmAdapter.AlarmViewHolder> {

    // ===== INTERFACES =====
    public interface OnAlarmActionListener {
        void onToggle(Alarm alarm, boolean isActive);
        void onEdit(Alarm alarm);
        void onDelete(Alarm alarm);
    }

    // ===== DATA =====
    private List<Alarm> alarms = new ArrayList<>();
    private final OnAlarmActionListener listener;

    public AlarmAdapter(OnAlarmActionListener listener) {
        this.listener = listener;
    }

    public void submitList(List<Alarm> newList) {
        this.alarms = newList != null ? newList : new ArrayList<>();
        notifyDataSetChanged();
    }

    public List<Alarm> getCurrentList() {
        return alarms;
    }

    // ===== RECYCLERVIEW =====

    @NonNull
    @Override
    public AlarmViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_alarm, parent, false);
        return new AlarmViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AlarmViewHolder holder, int position) {
        holder.bind(alarms.get(position));
    }

    @Override
    public int getItemCount() {
        return alarms.size();
    }

    // ===== VIEW HOLDER =====

    class AlarmViewHolder extends RecyclerView.ViewHolder {
        TextView tvTime, tvAmPm, tvLabel, tvChallengeIcon;
        TextView tvMon, tvTue, tvWed, tvThu, tvFri, tvSat, tvSun;
        SwitchMaterial switchActive;
        ImageButton btnDelete;

        AlarmViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTime          = itemView.findViewById(R.id.tvTime);
            tvAmPm          = itemView.findViewById(R.id.tvAmPm);
            tvLabel         = itemView.findViewById(R.id.tvLabel);
            tvChallengeIcon = itemView.findViewById(R.id.tvChallengeIcon);
            switchActive    = itemView.findViewById(R.id.switchActive);
            btnDelete       = itemView.findViewById(R.id.btnDelete);
            tvMon = itemView.findViewById(R.id.tvMon);
            tvTue = itemView.findViewById(R.id.tvTue);
            tvWed = itemView.findViewById(R.id.tvWed);
            tvThu = itemView.findViewById(R.id.tvThu);
            tvFri = itemView.findViewById(R.id.tvFri);
            tvSat = itemView.findViewById(R.id.tvSat);
            tvSun = itemView.findViewById(R.id.tvSun);
        }

        void bind(Alarm alarm) {
            // Giờ + AM/PM
            boolean use24h = android.text.format.DateFormat.is24HourFormat(itemView.getContext());
            if (use24h) {
                tvTime.setText(String.format("%02d:%02d", alarm.hour, alarm.minute));
                if (tvAmPm != null) tvAmPm.setText("");
            } else {
                int h = alarm.hour % 12;
                if (h == 0) h = 12;
                tvTime.setText(String.format("%d:%02d", h, alarm.minute));
                if (tvAmPm != null) tvAmPm.setText(alarm.hour < 12 ? "AM" : "PM");
            }
            tvTime.setAlpha(alarm.isActive ? 1f : 0.38f);
            if (tvAmPm != null) tvAmPm.setAlpha(alarm.isActive ? 0.7f : 0.25f);

            // Label
            if (alarm.label != null && !alarm.label.isEmpty()) {
                tvLabel.setText(alarm.label);
                tvLabel.setVisibility(View.VISIBLE);
            } else {
                tvLabel.setVisibility(View.GONE);
            }

            // Challenge icon
            String icon = getChallengeIcon(alarm.challengeType);
            if (!icon.isEmpty()) {
                tvChallengeIcon.setText(icon);
                tvChallengeIcon.setVisibility(View.VISIBLE);
            } else {
                tvChallengeIcon.setVisibility(View.GONE);
            }

            // Switch (không trigger listener khi bind)
            switchActive.setOnCheckedChangeListener(null);
            switchActive.setChecked(alarm.isActive);
            switchActive.setOnCheckedChangeListener((btn, checked) ->
                    listener.onToggle(alarm, checked));

            // Day chips
            setDayChip(tvMon, alarm.mon);
            setDayChip(tvTue, alarm.tue);
            setDayChip(tvWed, alarm.wed);
            setDayChip(tvThu, alarm.thu);
            setDayChip(tvFri, alarm.fri);
            setDayChip(tvSat, alarm.sat);
            setDayChip(tvSun, alarm.sun);

            // Click vào card → edit
            itemView.setOnClickListener(v -> listener.onEdit(alarm));

            // Nút xóa
            btnDelete.setOnClickListener(v -> listener.onDelete(alarm));
        }

        /** Tô màu chip ngày active/inactive */
        private void setDayChip(TextView tv, boolean active) {
            if (active) {
                tv.setBackgroundResource(R.drawable.bg_day_chip_active);
                tv.setTextColor(androidx.core.content.ContextCompat.getColor(tv.getContext(), R.color.day_active_text));
            } else {
                tv.setBackgroundResource(R.drawable.bg_day_chip);
                tv.setTextColor(androidx.core.content.ContextCompat.getColor(tv.getContext(), R.color.day_inactive_text));
            }
        }

        private String getChallengeIcon(int type) {
            switch (type) {
                case Alarm.CHALLENGE_MATH:  return "Giải toán";
                case Alarm.CHALLENGE_SHAKE: return "Lắc máy";
                case Alarm.CHALLENGE_SQUAT: return "Squat";
                case Alarm.CHALLENGE_STEP:  return "Đếm bước";
                case Alarm.CHALLENGE_QR:    return "Quét QR";
                default: return "";
            }
        }
    }
}
