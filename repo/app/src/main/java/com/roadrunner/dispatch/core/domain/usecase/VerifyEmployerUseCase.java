package com.roadrunner.dispatch.core.domain.usecase;

import com.roadrunner.dispatch.core.domain.model.Employer;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.repository.EmployerRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public class VerifyEmployerUseCase {
    private static final Pattern EIN_PATTERN = Pattern.compile("^\\d{2}-\\d{7}$");
    private static final Pattern ZIP_PATTERN = Pattern.compile("^\\d{5}(-\\d{4})?$");
    /** US street address: starts with a house/building number followed by a street name. */
    private static final Pattern US_STREET_PATTERN = Pattern.compile("^\\d+\\s+.+");

    /** Valid US state, territory, and district codes (USPS standard). */
    private static final Set<String> VALID_US_STATES = new HashSet<>(Arrays.asList(
            "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA",
            "HI", "ID", "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD",
            "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ",
            "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC",
            "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY",
            "DC", "AS", "GU", "MP", "PR", "VI"
    ));

    private final EmployerRepository employerRepository;

    public VerifyEmployerUseCase(EmployerRepository employerRepository) {
        this.employerRepository = employerRepository;
    }

    /**
     * @param employer   Employer data to verify
     * @param actorRole  Role of the actor; must be "ADMIN" or "COMPLIANCE_REVIEWER"
     */
    public Result<Employer> execute(Employer employer, String actorRole) {
        if (!"ADMIN".equals(actorRole) && !"COMPLIANCE_REVIEWER".equals(actorRole)) {
            return Result.failure("Unauthorized: only admins or compliance reviewers can verify employers");
        }
        List<String> errors = new ArrayList<>();

        if (employer.legalName == null || employer.legalName.trim().isEmpty()) {
            errors.add("Legal name is required");
        }
        if (employer.ein == null || !EIN_PATTERN.matcher(employer.ein).matches()) {
            errors.add("EIN must match format XX-XXXXXXX (e.g., 12-3456789)");
        }
        if (employer.streetAddress == null || employer.streetAddress.trim().isEmpty()) {
            errors.add("Street address is required");
        } else if (!US_STREET_PATTERN.matcher(employer.streetAddress.trim()).matches()) {
            errors.add("Street address must be a valid US format (e.g., 123 Main St)");
        }
        if (employer.city == null || employer.city.trim().isEmpty()) {
            errors.add("City is required");
        }
        if (employer.state == null || !VALID_US_STATES.contains(employer.state)) {
            errors.add("State must be a valid US state or territory code (e.g., CA)");
        }
        if (employer.zipCode == null || !ZIP_PATTERN.matcher(employer.zipCode).matches()) {
            errors.add("ZIP must be 5 digits or 5+4 format (e.g., 12345 or 12345-6789)");
        }

        if (!errors.isEmpty()) return Result.failure(errors);

        if (employer.orgId == null || employer.orgId.isEmpty()) {
            return Result.failure("Organisation ID is required");
        }

        // Check duplicate EIN within the org (only for new employers, id == null or not found)
        if (employer.id == null || employer.id.isEmpty()) {
            Employer existing = employerRepository.getByEinScoped(employer.ein, employer.orgId);
            if (existing != null) {
                return Result.failure("Employer with this EIN already exists");
            }
        }

        // Determine status: preserve SUSPENDED/DEACTIVATED if enforcement is active;
        // otherwise mark as VERIFIED.
        String resolvedStatus;
        if ("SUSPENDED".equals(employer.status) && employer.suspendedUntil > System.currentTimeMillis()) {
            resolvedStatus = "SUSPENDED";
        } else if ("DEACTIVATED".equals(employer.status)) {
            resolvedStatus = "DEACTIVATED";
        } else {
            resolvedStatus = "VERIFIED";
        }

        // Create verified employer
        Employer verified = new Employer(
            employer.id != null ? employer.id : UUID.randomUUID().toString(),
            employer.orgId, employer.legalName.trim(), employer.ein,
            employer.streetAddress.trim(), employer.city.trim(),
            employer.state, employer.zipCode,
            resolvedStatus, employer.warningCount, employer.suspendedUntil, employer.throttled
        );

        if (employer.id == null || employer.id.isEmpty()) {
            employerRepository.insert(verified);
        } else {
            employerRepository.update(verified);
        }

        return Result.success(verified);
    }
}
