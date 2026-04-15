# RoadRunner Dispatch & Commerce Console

## Overview

Standalone, offline-first Android application for field dispatch operations and commerce management. A single APK serves four user roles:

- **Administrator**: Configures products, orders, workforce rules, shipping templates, discount rules, matching weights, and user accounts. Includes data import/export via document picker.
- **Dispatcher**: Manages task creation, assignment, and worker coordination
- **Worker**: Accepts and completes assigned tasks, browses catalog
- **Compliance Reviewer**: Audits employer onboarding, reviews content, enforces violations

No server, no web UI, no remote database. All data persists locally using Room (SQLite) with encrypted-at-rest storage for sensitive data.

## Prerequisites

- Android Studio + JDK 17–21 + Android SDK 34

## Quick Start

```bash
./gradlew assembleDebug
# APK output: app/build/outputs/apk/debug/app-debug.apk
```

## Testing

```bash
./run_tests.sh
```

This runs:
1. JVM unit tests (`./gradlew test`) — covers domain logic, validation rules, business invariants
2. Lint checks (`./gradlew lint`)

Instrumented tests (Room integration + auth + navigation) require a connected device or emulator:
```bash
./gradlew connectedDebugAndroidTest
```

### Test Coverage

21 JVM unit test files (**298 test cases**) + 6 androidTest files (**48 instrumented tests**).

| Test Class | Cases | Coverage |
|------------|-------|----------|
| ValidateDiscountsUseCaseTest | 13 | Max 3, ≤40% cumulative, mixed percent+flat, boundary |
| ComputeOrderTotalsUseCaseTest | 13 | Rounding, multi-item, proportional discount, tax post-discount, consistency ±$0.01 |
| AddToCartUseCaseTest | 9 | Cart merge (customer+store), price conflict detection, quantity merge, resolution |
| LoginUseCaseTest | 13 | 5-attempt lockout, 15-min expiry, credential safety, deactivation |
| VerifyEmployerUseCaseTest | 18 | EIN XX-XXXXXXX format, state code, ZIP, role validation |
| ScanContentUseCaseTest | 15 | Sensitive words, zero-tolerance priority, whole-word \\b boundary, case insensitivity |
| EnforceViolationUseCaseTest | 24 | 2-warning policy, zero-tolerance bypass, all actions, audit log, **role rejection** |
| AcceptTaskUseCaseTest | 16 | Mutex, duplicate prevention, status checks, workload, mode guards, **role rejection**, cross-org rejection |
| CreateTaskUseCaseTest | 20 | Validation, zone existence, mode validation, multi-error, content scan (ZERO_TOLERANCE + FLAGGED), **role rejection** |
| FinalizeCheckoutUseCaseTest | 20 | DRAFT check, staleness, regulated notes, content scan (ZERO_TOLERANCE + FLAGGED), discount propagation, consistency, **role rejection** |
| MatchTasksUseCaseTest | 10 | Weighted scoring, zone proximity, GRAB_ORDER + ASSIGNED modes |
| PasswordHasherTest | 16 | PBKDF2 correctness, salt uniqueness, length validation, case sensitivity |
| CompleteTaskUseCaseTest | 11 | Status transition, workload decrement, reputation event, fresh score computation, **role rejection** |
| CreateOrderFromCartUseCaseTest | 12 | Conflict resolution, item enrichment, empty cart rejection |
| RegisterUserUseCaseTest | 7 | Hash verification, duplicate checking, validation |
| ResolveCartConflictUseCaseTest | 6 | Conflict flag clearing, price selection, arbitrary price rejection |
| FileReportUseCaseTest | 19 | Evidence URI/hash storage, role validation, org isolation |
| OpenCaseUseCaseTest | 11 | Case creation, audit logging, employer validation, **role rejection** (ADMIN + WORKER + DISPATCHER) |
| AppLoggerMaskTest | 10 | ID masking, null/short/UUID truncation, no PII leakage |
| ImportValidationTest | 14 | SHA-256 fingerprint, employer EIN/state/ZIP validation, duplicate detection, **role rejection** |
| RouteAuthorizationTest | 21 | Fragment-level role matrix for all 16 routes, null/empty/unknown role denial |

## Architecture

Clean Architecture (Onion) with three layers:

```
┌─────────────────────────────────────────────────┐
│              Presentation Layer                  │
│   Fragments, ViewModels, Adapters, Layouts       │
├─────────────────────────────────────────────────┤
│                Domain Layer                      │
│   UseCases, Models, Repository Interfaces        │
│   (Pure Java — zero Android imports)             │
├─────────────────────────────────────────────────┤
│             Infrastructure Layer                 │
│   Room DAOs, Repository Impls, Security          │
└─────────────────────────────────────────────────┘
```

## Module Map

| Module | Path | Purpose |
|--------|------|---------|
| Domain Models | `core/domain/model/` | 23 pure Java POJOs (no Android deps) |
| Repository Interfaces | `core/domain/repository/` | 12 port interfaces |
| Use Cases | `core/domain/usecase/` | 20 business logic classes |
| Validation | `core/util/` | PasswordHasher, AppLogger |
| Room Entities | `infrastructure/db/entity/` | 19 @Entity classes with indexes |
| Room DAOs | `infrastructure/db/dao/` | 19 @Dao interfaces |
| Repository Impls | `infrastructure/repository/` | 12 implementations |
| Security | `infrastructure/security/` | SessionManager (EncryptedSharedPreferences) |
| DI | `di/` | ServiceLocator (manual injection) |
| Auth UI | `presentation/auth/` | Login screen, role routing |
| Commerce UI | `presentation/commerce/` | Catalog, cart, checkout, invoice |
| Dispatch UI | `presentation/dispatch/` | Tasks, zones, workers |
| Compliance UI | `presentation/compliance/` | Employers, cases, reports |

## Key Flows

### Commerce: Catalog → Cart → Checkout → Invoice
1. Browse product catalog (search by brand/series/model)
2. Add to cart (auto-merge by customer+store; price conflicts flagged)
3. Select shipping (dynamically loaded from shipping templates)
4. Apply discounts (max 3, ≤40% cumulative percent-off; tax always computed on pre-discount amounts)
5. Compute totals (consistency check ±$0.01)
6. Finalize (transactional, checks regulated item notes)
7. View invoice

### Dispatch: Create → Match → Assign/Claim → Complete
1. Dispatcher creates task with zone, time window, mode
2. System computes weighted match scores (time window, workload, reputation, zone)
3. Assigned mode: dispatcher picks from ranked workers
4. Grab-order mode: worker claims from ranked tasks
5. Duplicate acceptance prevented by unique constraint + 3s mutex
6. Completion updates reputation score

### Compliance: Onboard → Monitor → Report → Enforce
1. Employer verified (legal name, EIN XX-XXXXXXX, US address)
2. Content scanned for sensitive words
3. Reports filed with evidence (SHA-256 fingerprinted)
4. Violations enforced: takedown, suspension (7/30/365d), throttle
5. Anti-harassment: 2 warnings before suspension (unless zero-tolerance)
6. All actions logged to immutable audit trail

## Security

| Feature | Implementation |
|---------|---------------|
| Password hashing | PBKDF2WithHmacSHA256, 120K iterations, 32-byte salt |
| Credential storage | EncryptedSharedPreferences (AES256-GCM) |
| Login lockout | 5 failed attempts → 15-minute lock |
| Password policy | Minimum 12 characters |
| Data isolation | All queries scoped by orgId |
| Evidence integrity | SHA-256 fingerprints on all attachments |
| Audit trail | Insert-only AuditLog entity |

## Default Seed Data

On first launch:
- Admin user: `admin` / `Admin12345678`
- Dispatcher user: `dispatcher` / `Dispatcher1234`
- Worker user: `worker` / `Worker12345678`
- Reviewer user: `reviewer` / `Reviewer1234`
- 3 shipping templates (Standard $5.99, Expedited $14.99, Local Pickup free)
- 5 zones (A through E, score 1-5)
- 16 sensitive words (10 standard + 6 zero-tolerance)
- 5 products (Dispatch Vest, Safety Helmet, Regulated Chemical Kit, Work Gloves, High-Vis Jacket)
- 1 discount rule (Employee Discount, 10% off)

> **Warning**: Seed credentials are for development only. Delete and re-seed with new credentials before any production deployment.

## Performance

- RecyclerView + DiffUtil on all lists (60fps target with 10K rows)
- Catalog images: downsampled + LRU cache (20MB cap)
- All DB/media IO off main thread (ViewModel + ExecutorService)
- Composite indexes on (orgId, status, updatedAt) and (brand, series, model)

## Environment

- compileSdk: 34, minSdk: 26, targetSdk: 34
- Language: Java 17
- Database: Room 2.6.0 (SQLite)
- UI: Material Design 3, ConstraintLayout, Navigation Component
- Security: AndroidX Security Crypto 1.1.0-alpha06
