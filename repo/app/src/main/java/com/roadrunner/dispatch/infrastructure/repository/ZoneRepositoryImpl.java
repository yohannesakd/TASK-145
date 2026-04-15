package com.roadrunner.dispatch.infrastructure.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.roadrunner.dispatch.core.domain.model.Zone;
import com.roadrunner.dispatch.core.domain.repository.ZoneRepository;
import com.roadrunner.dispatch.infrastructure.db.dao.ZoneDao;
import com.roadrunner.dispatch.infrastructure.db.entity.ZoneEntity;

import java.util.ArrayList;
import java.util.List;

public class ZoneRepositoryImpl implements ZoneRepository {

    private final ZoneDao zoneDao;

    public ZoneRepositoryImpl(ZoneDao zoneDao) {
        this.zoneDao = zoneDao;
    }

    @Override
    public List<Zone> getZones(String orgId) {
        List<ZoneEntity> entities = zoneDao.getZones(orgId);
        return mapList(entities);
    }

    @Override
    public LiveData<List<Zone>> getZonesLive(String orgId) {
        return Transformations.map(zoneDao.getZonesLive(orgId), this::mapList);
    }

    @Override
    public Zone getByIdScoped(String id, String orgId) {
        ZoneEntity entity = zoneDao.findByIdAndOrg(id, orgId);
        return entity != null ? mapToDomain(entity) : null;
    }

    @Override
    public void insert(Zone zone) {
        zoneDao.insert(mapToEntity(zone));
    }

    @Override
    public void update(Zone zone) {
        zoneDao.update(mapToEntity(zone));
    }

    // --- Mapping helpers ---

    private Zone mapToDomain(ZoneEntity e) {
        return new Zone(e.id, e.orgId, e.name, e.score, e.description);
    }

    private ZoneEntity mapToEntity(Zone z) {
        return new ZoneEntity(z.id, z.orgId, z.name, z.score, z.description);
    }

    private List<Zone> mapList(List<ZoneEntity> entities) {
        if (entities == null) return new ArrayList<>();
        List<Zone> result = new ArrayList<>(entities.size());
        for (ZoneEntity e : entities) {
            result.add(mapToDomain(e));
        }
        return result;
    }
}
