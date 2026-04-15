package com.roadrunner.dispatch.presentation.dispatch.zone;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.roadrunner.dispatch.core.domain.model.Zone;
import com.roadrunner.dispatch.core.domain.repository.ZoneRepository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel for zone management.
 *
 * <p>Exposes the live zone list and provides create/update operations.
 * Zones are scoped to an organisation and scored 1-5 for proximity matching.
 */
public class ZoneViewModel extends ViewModel {

    private final ZoneRepository zoneRepository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /** Error from any write operation. */
    private final MutableLiveData<String> error = new MutableLiveData<>();

    /** Signals that a create/update completed successfully. */
    private final MutableLiveData<Zone> savedZone = new MutableLiveData<>();

    public ZoneViewModel(ZoneRepository zoneRepository) {
        this.zoneRepository = zoneRepository;
    }

    // ── Exposed LiveData ──────────────────────────────────────────────────────

    /**
     * Live list of zones for the given org. Backed by Room; auto-refreshes.
     */
    public LiveData<List<Zone>> getZones(String orgId) {
        return zoneRepository.getZonesLive(orgId);
    }

    public LiveData<String> getError() { return error; }
    public LiveData<Zone> getSavedZone() { return savedZone; }

    // ── Actions ───────────────────────────────────────────────────────────────

    /**
     * Create a new zone and post it via {@link #getSavedZone()}.
     *
     * @param orgId       the organisation the zone belongs to
     * @param name        display name (required)
     * @param score       proximity score 1-5 (1 = outermost, 5 = innermost)
     * @param description optional longer description
     */
    public void createZone(String orgId, String name, int score, String description) {
        if (name == null || name.trim().isEmpty()) {
            error.setValue("Zone name is required");
            return;
        }
        if (score < 1 || score > 5) {
            error.setValue("Score must be between 1 and 5");
            return;
        }
        executor.execute(() -> {
            Zone zone = new Zone(UUID.randomUUID().toString(), orgId, name.trim(), score, description);
            zoneRepository.insert(zone);
            savedZone.postValue(zone);
        });
    }

    /**
     * Update an existing zone.
     */
    public void updateZone(Zone zone) {
        if (zone.name == null || zone.name.trim().isEmpty()) {
            error.setValue("Zone name is required");
            return;
        }
        executor.execute(() -> {
            zoneRepository.update(zone);
            savedZone.postValue(zone);
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
