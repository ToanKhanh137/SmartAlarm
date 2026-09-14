package com.example.smartalarm.ui.main;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextClock;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartalarm.R;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

public class WorldClockAdapter extends RecyclerView.Adapter<WorldClockAdapter.ClockViewHolder> {

    public interface OnClockLongClickListener {
        void onRemoveRequested(String timezoneId);
    }

    private final List<String> timezones = new ArrayList<>();
    private final OnClockLongClickListener listener;

    public WorldClockAdapter(OnClockLongClickListener listener) {
        this.listener = listener;
    }

    public void submit(List<String> timezoneIds) {
        timezones.clear();
        timezones.addAll(timezoneIds);
        notifyDataSetChanged();
    }

    /** "America/New_York" → "New York". Suy ra từ ID nên thành phố nào cũng hiển thị được. */
    public static String displayName(String timezoneId) {
        int slash = timezoneId.lastIndexOf('/');
        String city = slash >= 0 ? timezoneId.substring(slash + 1) : timezoneId;
        return city.replace('_', ' ');
    }

    @NonNull
    @Override
    public ClockViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_world_clock, parent, false);
        return new ClockViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ClockViewHolder holder, int position) {
        String tzId = timezones.get(position);
        Context context = holder.itemView.getContext();

        holder.tvCityName.setText(displayName(tzId));
        holder.tcWorld.setTimeZone(tzId);

        long nowMs = System.currentTimeMillis();
        long diffMs = TimeZone.getTimeZone(tzId).getOffset(nowMs)
                - TimeZone.getDefault().getOffset(nowMs);
        int diffHours = (int) (diffMs / 3600000);

        if (diffHours == 0) {
            holder.tvTimeDiff.setText(context.getString(R.string.world_clock_local));
        } else if (diffHours > 0) {
            holder.tvTimeDiff.setText(context.getString(R.string.world_clock_diff_ahead, diffHours));
        } else {
            holder.tvTimeDiff.setText(context.getString(R.string.world_clock_diff_behind, diffHours));
        }

        holder.itemView.setOnLongClickListener(v -> {
            listener.onRemoveRequested(tzId);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return timezones.size();
    }

    static class ClockViewHolder extends RecyclerView.ViewHolder {
        TextView tvCityName, tvTimeDiff;
        TextClock tcWorld;

        ClockViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCityName = itemView.findViewById(R.id.tvCityName);
            tvTimeDiff = itemView.findViewById(R.id.tvTimeDiff);
            tcWorld = itemView.findViewById(R.id.tcWorld);
        }
    }
}
