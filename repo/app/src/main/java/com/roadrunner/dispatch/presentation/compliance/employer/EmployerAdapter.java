package com.roadrunner.dispatch.presentation.compliance.employer;

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
import com.roadrunner.dispatch.core.domain.model.Employer;

/**
 * RecyclerView adapter for the employer list screen.
 *
 * <p>Status chip colors: PENDING=amber, VERIFIED=green, SUSPENDED=red, DEACTIVATED=grey.
 */
public class EmployerAdapter extends ListAdapter<Employer, EmployerAdapter.EmployerViewHolder> {

    // Status chip colors
    private static final int COLOR_PENDING     = 0xFFFF6F00;
    private static final int COLOR_VERIFIED    = 0xFF388E3C;
    private static final int COLOR_SUSPENDED   = 0xFFD32F2F;
    private static final int COLOR_DEACTIVATED = 0xFF757575;
    private static final int COLOR_TEXT        = 0xFFFFFFFF;

    public interface OnEmployerClickListener {
        void onEmployerClick(Employer employer);
    }

    private final OnEmployerClickListener listener;

    public EmployerAdapter(OnEmployerClickListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<Employer> DIFF =
            new DiffUtil.ItemCallback<Employer>() {
                @Override
                public boolean areItemsTheSame(@NonNull Employer a, @NonNull Employer b) {
                    return a.id.equals(b.id);
                }

                @Override
                public boolean areContentsTheSame(@NonNull Employer a, @NonNull Employer b) {
                    return a.status.equals(b.status)
                            && a.warningCount == b.warningCount
                            && a.throttled == b.throttled;
                }
            };

    @NonNull
    @Override
    public EmployerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_employer, parent, false);
        return new EmployerViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull EmployerViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class EmployerViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvLegalName;
        private final Chip chipStatus;
        private final TextView tvEin;
        private final TextView tvLocation;
        private final TextView tvWarningBadge;
        private final TextView tvThrottled;

        EmployerViewHolder(@NonNull View itemView) {
            super(itemView);
            tvLegalName    = itemView.findViewById(R.id.tv_legal_name);
            chipStatus     = itemView.findViewById(R.id.chip_status);
            tvEin          = itemView.findViewById(R.id.tv_ein);
            tvLocation     = itemView.findViewById(R.id.tv_location);
            tvWarningBadge = itemView.findViewById(R.id.tv_warning_badge);
            tvThrottled    = itemView.findViewById(R.id.tv_throttled);
        }

        void bind(Employer employer, OnEmployerClickListener listener) {
            tvLegalName.setText(employer.legalName);

            // Masked EIN
            String ein = employer.ein != null ? maskEin(employer.ein) : "";
            tvEin.setText(ein);

            // Location
            String location = (employer.city != null ? employer.city : "")
                    + (employer.state != null ? ", " + employer.state : "");
            tvLocation.setText(location);

            // Status chip
            chipStatus.setText(employer.status);
            int color = colorForStatus(employer.status);
            chipStatus.setChipBackgroundColor(ColorStateList.valueOf(color));
            chipStatus.setTextColor(COLOR_TEXT);

            // Warning badge
            if (employer.warningCount > 0) {
                tvWarningBadge.setVisibility(View.VISIBLE);
                tvWarningBadge.setText(itemView.getContext()
                        .getString(R.string.label_warnings) + ": " + employer.warningCount);
            } else {
                tvWarningBadge.setVisibility(View.GONE);
            }

            // Throttled
            tvThrottled.setVisibility(employer.throttled ? View.VISIBLE : View.GONE);

            itemView.setOnClickListener(v -> listener.onEmployerClick(employer));
        }

        private static String maskEin(String ein) {
            // Show **-***XXXX format
            if (ein.length() >= 4) {
                return "**-***" + ein.substring(ein.length() - 4);
            }
            return "**-*****";
        }

        private static int colorForStatus(String status) {
            if (status == null) return COLOR_DEACTIVATED;
            switch (status) {
                case "PENDING":     return COLOR_PENDING;
                case "VERIFIED":    return COLOR_VERIFIED;
                case "SUSPENDED":   return COLOR_SUSPENDED;
                case "DEACTIVATED":
                default:            return COLOR_DEACTIVATED;
            }
        }
    }
}
