package com.roadrunner.dispatch.presentation.dispatch.tasklist;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.roadrunner.dispatch.R;
import com.roadrunner.dispatch.core.domain.model.Task;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * RecyclerView adapter for the task list screen.
 *
 * <p>Uses {@link ListAdapter} with {@link DiffUtil} for efficient updates.
 * Status chip colors follow the spec: OPEN=green, ASSIGNED=blue, IN_PROGRESS=amber, COMPLETED=grey.
 */
public class TaskAdapter extends ListAdapter<Task, TaskAdapter.TaskViewHolder> {

    // Status chip colors per spec
    private static final int COLOR_OPEN          = 0xFF388E3C;
    private static final int COLOR_ASSIGNED      = 0xFF1565C0;
    private static final int COLOR_IN_PROGRESS   = 0xFFFF6F00;
    private static final int COLOR_COMPLETED     = 0xFF757575;
    private static final int COLOR_TEXT_ON_DARK  = 0xFFFFFFFF;

    private final OnTaskClickListener listener;

    public interface OnTaskClickListener {
        void onTaskClick(Task task);
    }

    public TaskAdapter(OnTaskClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<Task> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Task>() {
                @Override
                public boolean areItemsTheSame(@NonNull Task oldItem, @NonNull Task newItem) {
                    return oldItem.id.equals(newItem.id);
                }

                @Override
                public boolean areContentsTheSame(@NonNull Task oldItem, @NonNull Task newItem) {
                    return oldItem.status.equals(newItem.status)
                            && oldItem.title.equals(newItem.title)
                            && oldItem.zoneId.equals(newItem.zoneId);
                }
            };

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvTitle;
        private final Chip chipStatus;
        private final Chip chipZone;
        private final TextView tvTimeWindow;
        private final TextView tvPriority;
        private final TextView tvAssignedWorker;

        private static final SimpleDateFormat TIME_FMT =
                new SimpleDateFormat("h:mm a", Locale.getDefault());

        TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle          = itemView.findViewById(R.id.tv_task_title);
            chipStatus       = itemView.findViewById(R.id.chip_status);
            chipZone         = itemView.findViewById(R.id.chip_zone);
            tvTimeWindow     = itemView.findViewById(R.id.tv_time_window);
            tvPriority       = itemView.findViewById(R.id.tv_priority);
            tvAssignedWorker = itemView.findViewById(R.id.tv_assigned_worker);
        }

        void bind(Task task, OnTaskClickListener listener) {
            tvTitle.setText(task.title);

            // Status chip
            chipStatus.setText(formatStatus(task.status));
            int statusColor = colorForStatus(task.status);
            chipStatus.setChipBackgroundColor(
                    android.content.res.ColorStateList.valueOf(statusColor));
            chipStatus.setTextColor(COLOR_TEXT_ON_DARK);

            // Zone chip
            chipZone.setText(task.zoneId != null ? task.zoneId : "");

            // Time window
            String window = TIME_FMT.format(new Date(task.windowStart))
                    + " – " + TIME_FMT.format(new Date(task.windowEnd));
            tvTimeWindow.setText(window);

            // Priority
            tvPriority.setText(itemView.getContext().getString(
                    R.string.label_priority) + ": " + task.priority);

            // Assigned worker
            if (task.assignedWorkerId != null && !task.assignedWorkerId.isEmpty()) {
                tvAssignedWorker.setVisibility(View.VISIBLE);
                tvAssignedWorker.setText(task.assignedWorkerId);
            } else {
                tvAssignedWorker.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> listener.onTaskClick(task));
        }

        private static String formatStatus(String status) {
            if (status == null) return "";
            switch (status) {
                case "OPEN":        return "Open";
                case "ASSIGNED":    return "Assigned";
                case "IN_PROGRESS": return "In Progress";
                case "COMPLETED":   return "Completed";
                default:            return status;
            }
        }

        private static int colorForStatus(String status) {
            if (status == null) return COLOR_COMPLETED;
            switch (status) {
                case "OPEN":        return COLOR_OPEN;
                case "ASSIGNED":    return COLOR_ASSIGNED;
                case "IN_PROGRESS": return COLOR_IN_PROGRESS;
                case "COMPLETED":
                default:            return COLOR_COMPLETED;
            }
        }
    }
}
