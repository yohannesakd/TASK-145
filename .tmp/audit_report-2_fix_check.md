# Re-Check Report 4: Prior Audit Issues

Source issues reviewed: `.tmp/static-audit-report.md:74-114`

Boundary: static-only verification. No runtime execution, tests, Docker, or manual interaction performed.

## Verdict

- Fully fixed: 6
- Partially fixed: 0
- Unfixed: 0

## Issue-by-Issue Status

### 1. Employer verification omits the required US address-format check
- Status: Fixed
- Reasoning: `VerifyEmployerUseCase` now enforces a US-style street-address pattern instead of allowing any non-empty string.
- Evidence:
  - `app/src/main/java/com/roadrunner/dispatch/core/domain/usecase/VerifyEmployerUseCase.java:17-19`
  - `app/src/main/java/com/roadrunner/dispatch/core/domain/usecase/VerifyEmployerUseCase.java:52-56`

### 2. Import path accepts unsigned JSON and only verifies SHA-256 when a hash is present
- Status: Fixed
- Reasoning: Import now requires `sha256` and rejects missing, unverifiable, or mismatched fingerprints.
- Evidence:
  - `app/src/main/java/com/roadrunner/dispatch/presentation/admin/ImportExportFragment.java:314-332`

### 3. Export writes employer identity data to plaintext JSON outside the encrypted store
- Status: Fixed
- Reasoning: Employer export no longer includes legal name, EIN, or address data; only non-sensitive status/reference fields remain.
- Evidence:
  - `app/src/main/java/com/roadrunner/dispatch/presentation/admin/ImportExportFragment.java:245-263`

### 4. Import validation is partial for products, shipping templates, discount rules, and zones
- Status: Fixed
- Reasoning: Import now validates products, shipping templates, discount rules, and zones, including zone score bounds of `1..5`.
- Evidence:
  - Products: `app/src/main/java/com/roadrunner/dispatch/presentation/admin/ImportExportFragment.java:342-379`
  - Shipping templates: `app/src/main/java/com/roadrunner/dispatch/presentation/admin/ImportExportFragment.java:381-408`
  - Discount rules: `app/src/main/java/com/roadrunner/dispatch/presentation/admin/ImportExportFragment.java:410-435`
  - Zones: `app/src/main/java/com/roadrunner/dispatch/presentation/admin/ImportExportFragment.java:438-462`
  - Zone use case validation: `app/src/main/java/com/roadrunner/dispatch/core/domain/usecase/CreateZoneUseCase.java:22-39`

### 5. Some privileged admin/import flows bypass domain-layer authorization and validation
- Status: Fixed
- Reasoning: The previously cited privileged flows now route through domain use cases instead of direct DAO/repository inserts from fragments. Shipping template, discount rule, and zone creation/import are all mediated by role-aware use cases.
- Evidence:
  - Admin shipping via use case: `app/src/main/java/com/roadrunner/dispatch/presentation/admin/AdminConfigFragment.java:299-317`
  - Admin discount via use case: `app/src/main/java/com/roadrunner/dispatch/presentation/admin/AdminConfigFragment.java:343-359`
  - Import shipping via use case: `app/src/main/java/com/roadrunner/dispatch/presentation/admin/ImportExportFragment.java:382-408`
  - Import discount via use case: `app/src/main/java/com/roadrunner/dispatch/presentation/admin/ImportExportFragment.java:411-435`
  - Import zone via use case: `app/src/main/java/com/roadrunner/dispatch/presentation/admin/ImportExportFragment.java:438-462`
  - Use case wiring: `app/src/main/java/com/roadrunner/dispatch/di/ServiceLocator.java:270-279`

### 6. Repository ships Docker/web-serving artifacts that contradict the standalone APK delivery constraint
- Status: Fixed
- Reasoning: The previously cited artifacts are no longer present in the repository.
- Evidence:
  - No `Dockerfile` found under repo
  - No `docker-compose.yml` found under repo
  - No `container-build-and-serve.sh` found under repo

## Final Conclusion

All issues previously listed in `.tmp/static-audit-report.md` are now fixed by static evidence in the current repository state.
