package com.roadrunner.dispatch.presentation.dispatch.taskdetail;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.roadrunner.dispatch.R;
import com.roadrunner.dispatch.core.domain.model.ScoredWorker;

import java.util.Locale;

/**
 * Adapter for the ranked-workers list shown in the dispatcher task-detail view.
 */
public class ScoredWorkerAdapter extends ListAdapter<ScoredWorker, ScoredWorkerAdapter.ViewHolder> {

    public interface OnWorkerClickListener {
        void onWorkerClick(ScoredWorker scoredWorker);
    }

    private final OnWorkerClickListener listener;

    public ScoredWorkerAdapter(OnWorkerClickListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<ScoredWorker> DIFF =
            new DiffUtil.ItemCallback<ScoredWorker>() {
                @Override
                public boolean areItemsTheSame(@NonNull ScoredWorker a, @NonNull ScoredWorker b) {
                    return a.worker.id.equals(b.worker.id);
                }

                @Override
                public boolean areContentsTheSame(@NonNull ScoredWorker a, @NonNull ScoredWorker b) {
                    return Double.compare(a.score, b.score) == 0;
                }
            };

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_scored_worker, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvWorkerName;
        private final TextView tvMatchScore;
        private final ProgressBar barReputation;
        private final TextView tvReputationValue;
        private final TextView tvZone;
        private final TextView tvWorkload;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvWorkerName      = itemView.findViewById(R.id.tv_worker_name);
            tvMatchScore      = itemView.findViewById(R.id.tv_match_score);
            barReputation     = itemView.findViewById(R.id.bar_reputation);
            tvReputationValue = itemView.findViewById(R.id.tv_reputation_value);
            tvZone            = itemView.findViewById(R.id.tv_zone);
            tvWorkload        = itemView.findViewById(R.id.tv_workload);
        }

        void bind(ScoredWorker item, OnWorkerClickListener listener) {
            tvWorkerName.setText(item.worker.name);
            tvMatchScore.setText(String.format(Locale.getDefault(), "%.0f%%", item.score * 100));

            // Reputation bar (0-5 scale, display as 0-100)
            int repPct = (int) Math.round(item.worker.reputationScore / 5.0 * 100);
            barReputation.setProgress(repPct);
            tvReputationValue.setText(String.format(Locale.getDefault(), "%.1f",
                    item.worker.reputationScore));

            tvZone.setText(item.worker.zoneId != null ? item.worker.zoneId : "");
            tvWorkload.setText(itemView.getContext().getString(R.string.label_workload)
                    + ": " + item.worker.currentWorkload);

            itemView.setOnClickListener(v -> listener.onWorkerClick(item));
        }
    }
}
