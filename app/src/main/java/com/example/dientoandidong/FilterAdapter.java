package com.example.dientoandidong;

import android.graphics.Color;
import android.view.*;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class FilterAdapter extends RecyclerView.Adapter<FilterAdapter.FilterViewHolder> {

    private final List<String> filterList;
    private final OnFilterClickListener listener;
    private int selectedPosition = RecyclerView.NO_POSITION;

    public interface OnFilterClickListener {
        void onFilterClick(String filterName);
    }

    public FilterAdapter(List<String> filterList, OnFilterClickListener listener) {
        this.filterList = filterList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public FilterViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_1, parent, false);
        return new FilterViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FilterViewHolder holder, int position) {
        String filterName = filterList.get(position);
        holder.textView.setText(filterName);
        holder.textView.setTextColor(position == selectedPosition ? Color.BLUE : Color.BLACK);
        holder.itemView.setOnClickListener(v -> {
            int previousPosition = selectedPosition;
            selectedPosition = holder.getAdapterPosition();
            notifyItemChanged(previousPosition);
            notifyItemChanged(selectedPosition);
            listener.onFilterClick(filterName);
        });
    }

    @Override
    public int getItemCount() {
        return filterList.size();
    }

    static class FilterViewHolder extends RecyclerView.ViewHolder {
        TextView textView;

        FilterViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = (TextView) itemView.findViewById(android.R.id.text1);
        }
    }
}
