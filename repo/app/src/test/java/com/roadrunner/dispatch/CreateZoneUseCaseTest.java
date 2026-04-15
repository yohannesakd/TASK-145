package com.roadrunner.dispatch;

import androidx.lifecycle.LiveData;

import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.model.Zone;
import com.roadrunner.dispatch.core.domain.repository.ZoneRepository;
import com.roadrunner.dispatch.core.domain.usecase.CreateZoneUseCase;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class CreateZoneUseCaseTest {

    private StubZoneRepository zoneRepo;
    private CreateZoneUseCase useCase;

    @Before
    public void setUp() {
        zoneRepo = new StubZoneRepository();
        useCase = new CreateZoneUseCase(zoneRepo);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Zone validZone(int score) {
        return new Zone("z1", "org1", "Downtown", score, "Main zone");
    }

    // -----------------------------------------------------------------------
    // Role checks
    // -----------------------------------------------------------------------

    @Test
    public void workerRole_rejected() {
        Result<Zone> result = useCase.execute(validZone(3), "WORKER");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("role")
                || result.getFirstError().toLowerCase().contains("admin")
                || result.getFirstError().toLowerCase().contains("unauthorized"));
    }

    @Test
    public void dispatcherRole_rejected() {
        Result<Zone> result = useCase.execute(validZone(3), "DISPATCHER");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("role")
                || result.getFirstError().toLowerCase().contains("admin")
                || result.getFirstError().toLowerCase().contains("unauthorized"));
    }

    // -----------------------------------------------------------------------
    // Happy path
    // -----------------------------------------------------------------------

    @Test
    public void adminRole_success() {
        Zone zone = validZone(3);
        Result<Zone> result = useCase.execute(zone, "ADMIN");
        assertTrue(result.isSuccess());
        assertEquals(1, zoneRepo.insertedZones.size());
        assertSame(zone, zoneRepo.insertedZones.get(0));
    }

    // -----------------------------------------------------------------------
    // Validation
    // -----------------------------------------------------------------------

    @Test
    public void blankName_failure() {
        Zone zone = new Zone("z1", "org1", "", 3, "desc");
        Result<Zone> result = useCase.execute(zone, "ADMIN");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("name"));
    }

    @Test
    public void scoreTooLow_failure() {
        Zone zone = new Zone("z1", "org1", "Zone", 0, "desc");
        Result<Zone> result = useCase.execute(zone, "ADMIN");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("score"));
    }

    @Test
    public void scoreTooHigh_failure() {
        Zone zone = new Zone("z1", "org1", "Zone", 6, "desc");
        Result<Zone> result = useCase.execute(zone, "ADMIN");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("score"));
    }

    @Test
    public void scoreMin_success() {
        Result<Zone> result = useCase.execute(validZone(1), "ADMIN");
        assertTrue(result.isSuccess());
    }

    @Test
    public void scoreMax_success() {
        Result<Zone> result = useCase.execute(validZone(5), "ADMIN");
        assertTrue(result.isSuccess());
    }

    // -----------------------------------------------------------------------
    // Stub implementations
    // -----------------------------------------------------------------------

    private static class StubZoneRepository implements ZoneRepository {
        final List<Zone> insertedZones = new ArrayList<>();
        private final Map<String, Zone> zones = new HashMap<>();

        @Override public void insert(Zone zone) {
            insertedZones.add(zone);
            zones.put(zone.id, zone);
        }

        @Override public void update(Zone zone) { zones.put(zone.id, zone); }

        @Override public Zone getByIdScoped(String id, String orgId) {
            Zone z = zones.get(id);
            if (z != null && z.orgId != null && !z.orgId.equals(orgId)) return null;
            return z;
        }

        @Override public List<Zone> getZones(String orgId) {
            List<Zone> result = new ArrayList<>();
            for (Zone z : zones.values()) if (z.orgId.equals(orgId)) result.add(z);
            return result;
        }

        @Override public LiveData<List<Zone>> getZonesLive(String orgId) { return null; }
    }
}
