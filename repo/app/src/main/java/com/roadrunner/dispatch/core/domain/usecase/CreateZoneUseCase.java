package com.roadrunner.dispatch.core.domain.usecase;

import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.model.Zone;
import com.roadrunner.dispatch.core.domain.repository.ZoneRepository;

import java.util.ArrayList;
import java.util.List;

public class CreateZoneUseCase {

    private final ZoneRepository zoneRepository;

    public CreateZoneUseCase(ZoneRepository zoneRepository) {
        this.zoneRepository = zoneRepository;
    }

    /**
     * @param zone      Zone data to create
     * @param actorRole Role of the actor; must be "ADMIN"
     */
    public Result<Zone> execute(Zone zone, String actorRole) {
        if (!"ADMIN".equals(actorRole)) {
            return Result.failure("Unauthorized: only admins can create zones");
        }

        List<String> errors = new ArrayList<>();

        if (zone.name == null || zone.name.trim().isEmpty()) {
            errors.add("Name is required");
        }
        if (zone.score < 1 || zone.score > 5) {
            errors.add("Score must be between 1 and 5");
        }

        if (!errors.isEmpty()) return Result.failure(errors);

        zoneRepository.insert(zone);
        return Result.success(zone);
    }
}
