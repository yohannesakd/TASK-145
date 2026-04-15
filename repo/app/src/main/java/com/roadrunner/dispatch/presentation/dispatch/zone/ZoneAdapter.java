package com.roadrunner.dispatch.presentation.dispatch.zone;

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
import com.roadrunner.dispatch.core.domain.model.Zone;

/**
 * RecyclerView adapter for the zone list screen.
 */
public class ZoneAdapter extends ListAdapter<Zone, ZoneAdapter.ZoneViewHolder> {

    public ZoneAdapter() {
        super(DIFF);
    }

    private static final DiffUtil.ItemCallback<Zone> DIFF = new DiffUtil.ItemCallback<Zone>() {
        @Override
        public boolean areItemsTheSame(@NonNull Zone a, @NonNull Zone b) {
            return a.id.equals(b.id);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Zone a, @NonNull Zone b) {
            return a.name.equals(b.name) && a.score == b.score;
        }
    };

    @NonNull
    @Override
    public ZoneViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_zone, parent, false);
        return new ZoneViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ZoneViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class ZoneViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvZoneName;
        private final Chip chipScore;
        private final TextView tvZoneDescription;

        ZoneViewHolder(@NonNull View itemView) {
            super(itemView);
            tvZoneName        = itemView.findViewById(R.id.tv_zone_name);
            chipScore         = itemView.findViewById(R.id.chip_score);
            tvZoneDescription = itemView.findViewById(R.id.tv_zone_description);
        }

        void bind(Zone zone) {
            tvZoneName.setText(zone.name);
            chipScore.setText(itemView.getContext().getString(R.string.label_score) + " " + zone.score);

            if (zone.description != null && !zone.description.isEmpty()) {
                tvZoneDescription.setVisibility(View.VISIBLE);
                tvZoneDescription.setText(zone.description);
            } else {
                tvZoneDescription.setVisibility(View.GONE);
            }
        }
    }
}
