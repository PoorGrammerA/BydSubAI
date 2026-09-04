package com.poorgrammera.bydsubai.ui;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogViewHolder> {

    private final List<String> logs = new ArrayList<>();
    private static final int MAX_LOGS = 1000;

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
        return new LogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        String log = logs.get(position);
        holder.textView.setText(log);
        holder.textView.setTextSize(12f);
        holder.textView.setTextColor(Color.parseColor("#80FF80"));
        holder.textView.setPadding(8, 2, 8, 2);
    }

    @Override
    public int getItemCount() {
        return logs.size();
    }

    public void addLogs(List<String> newLogs) {
        int oldSize = logs.size();
        logs.addAll(newLogs);
        
        if (logs.size() > MAX_LOGS) {
            int removeCount = logs.size() - MAX_LOGS;
            for (int i = 0; i < removeCount; i++) {
                logs.remove(0);
            }
            notifyDataSetChanged();
        } else {
            notifyItemRangeInserted(oldSize, newLogs.size());
        }
    }

    public void clear() {
        logs.clear();
        notifyDataSetChanged();
    }

    static class LogViewHolder extends RecyclerView.ViewHolder {
        TextView textView;

        LogViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(android.R.id.text1);
        }
    }
}
