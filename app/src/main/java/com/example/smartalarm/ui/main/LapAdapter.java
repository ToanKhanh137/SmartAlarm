package com.example.smartalarm.ui.main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.smartalarm.R;
import java.util.ArrayList;
import java.util.List;

public class LapAdapter extends RecyclerView.Adapter<LapAdapter.LapViewHolder> {
    private final List<String> lapTexts = new ArrayList<>();

    public void addLap(String lapText) {
        lapTexts.add(0, lapText);
        notifyItemInserted(0);
    }

    public void clear() {
        lapTexts.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public LapViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_lap, parent, false);
        return new LapViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LapViewHolder holder, int position) {
        String lapInfo = lapTexts.get(position);
        String[] parts = lapInfo.split("  ");
        if (parts.length == 2) {
            holder.tvLapNumber.setText(parts[0]);
            holder.tvLapTime.setText(parts[1]);
        }
    }

    @Override
    public int getItemCount() {
        return lapTexts.size();
    }

    static class LapViewHolder extends RecyclerView.ViewHolder {
        TextView tvLapNumber, tvLapTime;
        LapViewHolder(@NonNull View itemView) {
            super(itemView);
            tvLapNumber = itemView.findViewById(R.id.tvLapNumber);
            tvLapTime = itemView.findViewById(R.id.tvLapTime);
        }
    }
}
