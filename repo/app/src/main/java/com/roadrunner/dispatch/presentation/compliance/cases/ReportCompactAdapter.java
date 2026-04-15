package com.roadrunner.dispatch.presentation.compliance.cases;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.roadrunner.dispatch.R;
import com.roadrunner.dispatch.core.domain.model.Report;

/**
 * Compact report adapter used inside the case detail card.
 */
public class ReportCompactAdapter extends ListAdapter<Report, ReportCompactAdapter.ViewHolder> {

    public ReportCompactAdapter() {
        super(DIFF);
    }

    private static final DiffUtil.ItemCallback<Report> DIFF = new DiffUtil.ItemCallback<Report>() {
        @Override public boolean areItemsTheSame(@NonNull Report a, @NonNull Report b) {
            return a.id.equals(b.id);
        }
        @Override public boolean areContentsTheSame(@NonNull Report a, @NonNull Report b) {
            return a.status.equals(b.status);
        }
    };

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_report_compact, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTargetType;
        private final TextView tvReportStatus;
        private final TextView tvDescription;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTargetType   = itemView.findViewById(R.id.tv_target_type);
            tvReportStatus = itemView.findViewById(R.id.tv_report_status);
            tvDescription  = itemView.findViewById(R.id.tv_description);
        }

        void bind(Report report) {
            tvTargetType.setText(report.targetType);
            tvReportStatus.setText(report.status);
            tvDescription.setText(report.description);
        }
    }
}
