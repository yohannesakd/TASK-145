package com.roadrunner.dispatch.presentation.compliance.cases;

import android.content.res.ColorStateList;
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
import com.roadrunner.dispatch.core.domain.model.ComplianceCase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * RecyclerView adapter for the compliance case list.
 *
 * <p>Case type chip color is derived from severity for visual emphasis.
 */
public class CaseAdapter extends ListAdapter<ComplianceCase, CaseAdapter.CaseViewHolder> {

    // Severity-based chip colors
    private static final int COLOR_LOW      = 0xFF388E3C;
    private static final int COLOR_MEDIUM   = 0xFFFF6F00;
    private static final int COLOR_HIGH     = 0xFFD32F2F;
    private static final int COLOR_CRITICAL = 0xFF880E4F;
    private static final int COLOR_DEFAULT  = 0xFF1565C0;
    private static final int COLOR_TEXT     = 0xFFFFFFFF;

    public interface OnCaseClickListener {
        void onCaseClick(ComplianceCase complianceCase);
    }

    private final OnCaseClickListener listener;
    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

    public CaseAdapter(OnCaseClickListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<ComplianceCase> DIFF =
            new DiffUtil.ItemCallback<ComplianceCase>() {
                @Override
                public boolean areItemsTheSame(@NonNull ComplianceCase a, @NonNull ComplianceCase b) {
                    return a.id.equals(b.id);
                }

                @Override
                public boolean areContentsTheSame(@NonNull ComplianceCase a, @NonNull ComplianceCase b) {
                    return a.status.equals(b.status) && a.severity.equals(b.severity);
                }
            };

    @NonNull
    @Override
    public CaseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_case, parent, false);
        return new CaseViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull CaseViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class CaseViewHolder extends RecyclerView.ViewHolder {
        private final Chip chipCaseType;
        private final Chip chipStatus;
        private final TextView tvDescriptionPreview;
        private final TextView tvCreatedDate;
        private final TextView tvAssignedTo;

        CaseViewHolder(@NonNull View itemView) {
            super(itemView);
            chipCaseType         = itemView.findViewById(R.id.chip_case_type);
            chipStatus           = itemView.findViewById(R.id.chip_status);
            tvDescriptionPreview = itemView.findViewById(R.id.tv_description_preview);
            tvCreatedDate        = itemView.findViewById(R.id.tv_created_date);
            tvAssignedTo         = itemView.findViewById(R.id.tv_assigned_to);
        }

        void bind(ComplianceCase item, OnCaseClickListener listener) {
            chipCaseType.setText(item.caseType);
            int severityColor = colorForSeverity(item.severity);
            chipCaseType.setChipBackgroundColor(ColorStateList.valueOf(severityColor));
            chipCaseType.setTextColor(COLOR_TEXT);

            chipStatus.setText(item.status);

            tvDescriptionPreview.setText(item.description);

            if (item.assignedTo != null && !item.assignedTo.isEmpty()) {
                tvAssignedTo.setVisibility(View.VISIBLE);
                tvAssignedTo.setText(item.assignedTo);
            } else {
                tvAssignedTo.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> listener.onCaseClick(item));
        }

        private static int colorForSeverity(String severity) {
            if (severity == null) return COLOR_DEFAULT;
            switch (severity) {
                case "LOW":      return COLOR_LOW;
                case "MEDIUM":   return COLOR_MEDIUM;
                case "HIGH":     return COLOR_HIGH;
                case "CRITICAL": return COLOR_CRITICAL;
                default:         return COLOR_DEFAULT;
            }
        }
    }
}
