package com.example.smartalarm.ui.main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextClock;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.smartalarm.R;
import java.util.TimeZone;

public class WorldClockAdapter extends RecyclerView.Adapter<WorldClockAdapter.ClockViewHolder> {
    
    // Sample cities
    private final String[] timezones = {
        "America/New_York", "Europe/London", "Europe/Paris",
        "Asia/Tokyo", "Australia/Sydney", "America/Los_Angeles"
    };
    
    private final String[] cities = {
        "New York", "London", "Paris", "Tokyo", "Sydney", "Los Angeles"
    };

    @NonNull
    @Override
    public ClockViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_world_clock, parent, false);
        return new ClockViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ClockViewHolder holder, int position) {
        String tzId = timezones[position];
        holder.tvCityName.setText(cities[position]);
        holder.tcWorld.setTimeZone(tzId);
        
        TimeZone tz = TimeZone.getTimeZone(tzId);
        TimeZone localTz = TimeZone.getDefault();
        long diffMs = tz.getOffset(System.currentTimeMillis()) - localTz.getOffset(System.currentTimeMillis());
        int diffHours = (int) (diffMs / 3600000);
        
        android.content.Context context = holder.itemView.getContext();
        if (diffHours == 0) {
            holder.tvTimeDiff.setText(context.getString(R.string.world_clock_local));
        } else if (diffHours > 0) {
            holder.tvTimeDiff.setText(context.getString(R.string.world_clock_diff_ahead, diffHours));
        } else {
            holder.tvTimeDiff.setText(context.getString(R.string.world_clock_diff_behind, diffHours));
        }
    }

    @Override
    public int getItemCount() {
        return timezones.length;
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
