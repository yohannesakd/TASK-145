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
import com.roadrunner.dispatch.core.domain.model.AuditLogEntry;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Adapter for the audit log timeline in the case detail screen.
 */
public class AuditLogAdapter extends ListAdapter<AuditLogEntry, AuditLogAdapter.ViewHolder> {

    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault());

    public AuditLogAdapter() {
        super(DIFF);
    }

    private static final DiffUtil.ItemCallback<AuditLogEntry> DIFF =
            new DiffUtil.ItemCallback<AuditLogEntry>() {
                @Override public boolean areItemsTheSame(@NonNull AuditLogEntry a, @NonNull AuditLogEntry b) {
                    return a.id.equals(b.id);
                }
                @Override public boolean areContentsTheSame(@NonNull AuditLogEntry a, @NonNull AuditLogEntry b) {
                    return a.action.equals(b.action) && a.createdAt == b.createdAt;
                }
            };

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_audit_log, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvAction;
        private final TextView tvActor;
        private final TextView tvTimestamp;
        private final TextView tvDetails;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAction    = itemView.findViewById(R.id.tv_action);
            tvActor     = itemView.findViewById(R.id.tv_actor);
            tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
            tvDetails   = itemView.findViewById(R.id.tv_details);
        }

        void bind(AuditLogEntry entry) {
            tvAction.setText(entry.action);
            tvActor.setText(entry.actorId);
            tvTimestamp.setText(DATE_FMT.format(new Date(entry.createdAt)));
            if (entry.details != null && !entry.details.isEmpty()) {
                tvDetails.setVisibility(View.VISIBLE);
                tvDetails.setText(entry.details);
            } else {
                tvDetails.setVisibility(View.GONE);
            }
        }
    }
}
