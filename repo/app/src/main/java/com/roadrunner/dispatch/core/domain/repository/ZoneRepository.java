package com.roadrunner.dispatch.core.domain.repository;

import androidx.lifecycle.LiveData;
import com.roadrunner.dispatch.core.domain.model.Zone;
import java.util.List;

public interface ZoneRepository {
    List<Zone> getZones(String orgId);
    LiveData<List<Zone>> getZonesLive(String orgId);
    Zone getByIdScoped(String id, String orgId);
    void insert(Zone zone);
    void update(Zone zone);
}
