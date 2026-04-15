# RoadRunner Dispatch & Commerce Console

**Project Type:** Android (offline-first, single-APK)

## Overview

Standalone, offline-first Android application for field dispatch operations and commerce management. A single APK serves four user roles:

- **Administrator**: Configures products, orders, workforce rules, shipping templates, discount rules, matching weights, and user accounts. Includes data import/export via document picker.
- **Dispatcher**: Manages task creation, assignment, and worker coordination
- **Worker**: Accepts and completes assigned tasks, browses catalog
- **Compliance Reviewer**: Audits employer onboarding, reviews content, enforces violations

No server, no web UI, no remote database. All data persists locally using Room (SQLite) with encrypted-at-rest storage for sensitive data.

---

## Run, Access, Verify, and Test

### Prerequisites

- JDK 17–21
- Android SDK 34 (with `ANDROID_HOME` set)
- An Android emulator (API 26+) or physical device with USB debugging enabled
- `adb` on the host PATH (included with Android SDK Platform-Tools)

### Step 1: Build the APK

```bash
./gradlew assembleDebug
# APK output: app/build/outputs/apk/debug/app-debug.apk
```

### Step 2: Install and launch

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.roadrunner.dispatch/.MainActivity
```

### Step 3: Verify (manual)

After launch, the app opens to the **Login** screen. Use one of the seeded credentials to access each role-specific dashboard:

**Administrator flow:**
1. Log in as `admin` / `Admin12345678`
2. Confirm the Admin Dashboard appears
3. Navigate to Catalog — verify products are listed
4. Navigate to User Management — verify the seeded users appear

**Dispatcher flow:**
1. Log in as `dispatcher` / `Dispatcher1234`
2. Confirm the Dispatcher Dashboard appears
3. Navigate to Tasks — create a new task with a zone
4. Confirm the task appears in the task list

**Worker flow:**
1. Log in as `worker` / `Worker12345678`
2. Confirm the Worker Dashboard appears
3. Navigate to Catalog — add an item to cart
4. Proceed to Checkout and confirm order creation

**Compliance Reviewer flow:**
1. Log in as `reviewer` / `Reviewer1234`
2. Confirm the Compliance Dashboard appears
3. Navigate to Employers — verify seeded employer data
4. Open Cases — create a case and confirm it is listed

### Step 4: Run all tests

```bash
# Single command: JVM unit tests + lint + instrumented tests
./run_tests.sh
```

Or run each tier individually:

```bash
./gradlew test                              # JVM unit tests
./gradlew lint                              # Lint checks
./gradlew connectedDebugAndroidTest         # Instrumented tests (device/emulator)
```

Use `--jvm-only` to skip the device-dependent instrumented tier:

```bash
./run_tests.sh --jvm-only
```

By default, `./run_tests.sh` exits non-zero if no device/emulator is connected. Use `--jvm-only` to explicitly skip instrumented tests when no device is available.

### What runs where

| Scope | Environment | Command |
|-------|------------|---------|
| APK build | Host | `./gradlew assembleDebug` |
| JVM unit tests (354 cases) | Host (JVM) | `./gradlew test` |
| Lint | Host (JVM) | `./gradlew lint` |
| Instrumented tests (335 cases) | Device/emulator | `./gradlew connectedDebugAndroidTest` |

---

## Testing

### Test Inventory

25 JVM test files (**354 test cases**) + 44 androidTest files (**335 instrumented tests**).

> **Maintenance note:** Run `./count_tests.sh` to regenerate current counts from source. This script scans `@Test` annotations and is the single source of truth for the numbers above.

#### JVM Unit Tests

| Test Class | Cases | Coverage |
|------------|-------|----------|
| ValidateDiscountsUseCaseTest | 13 | Max 3, ≤40% cumulative, mixed percent+flat, boundary |
| ComputeOrderTotalsUseCaseTest | 13 | Rounding, multi-item, proportional discount, tax post-discount, consistency ±$0.01 |
| AddToCartUseCaseTest | 9 | Cart merge (customer+store), price conflict detection, quantity merge, resolution |
| LoginUseCaseTest | 13 | 5-attempt lockout, 15-min expiry, credential safety, deactivation |
| VerifyEmployerUseCaseTest | 18 | EIN XX-XXXXXXX format, state code, ZIP, role validation |
| ScanContentUseCaseTest | 15 | Sensitive words, zero-tolerance priority, whole-word \\b boundary, case insensitivity |
| EnforceViolationUseCaseTest | 24 | 2-warning policy, zero-tolerance bypass, all actions, audit log, role rejection |
| AcceptTaskUseCaseTest | 16 | Mutex, duplicate prevention, status checks, workload, mode guards, role rejection, cross-org |
| CreateTaskUseCaseTest | 20 | Validation, zone existence, mode validation, multi-error, content scan, role rejection |
| FinalizeCheckoutUseCaseTest | 20 | DRAFT check, staleness, regulated notes, content scan, discount propagation, consistency, role rejection |
| MatchTasksUseCaseTest | 10 | Weighted scoring, zone proximity, GRAB_ORDER + ASSIGNED modes |
| PasswordHasherTest | 16 | PBKDF2 correctness, salt uniqueness, length validation, case sensitivity |
| CompleteTaskUseCaseTest | 11 | Status transition, workload decrement, reputation event, fresh score computation, role rejection |
| CreateOrderFromCartUseCaseTest | 12 | Conflict resolution, item enrichment, empty cart rejection |
| RegisterUserUseCaseTest | 7 | Hash verification, duplicate checking, validation |
| ResolveCartConflictUseCaseTest | 6 | Conflict flag clearing, price selection, arbitrary price rejection |
| FileReportUseCaseTest | 19 | Evidence URI/hash storage, role validation, org isolation |
| OpenCaseUseCaseTest | 11 | Case creation, audit logging, employer validation, role rejection |
| AppLoggerMaskTest | 10 | ID masking, null/short/UUID truncation, no PII leakage |
| ImportValidationTest | 14 | SHA-256 fingerprint, employer EIN/state/ZIP validation, duplicate detection, role rejection |
| RouteAuthorizationTest | 21 | Fragment-level role matrix for all 16 routes — delegates to production RoleGuard.matchesRole() |
| CreateDiscountRuleUseCaseTest | 9 | Rule creation, validation, role enforcement |
| CreateShippingTemplateUseCaseTest | 8 | Template creation, validation, role enforcement |
| CreateZoneUseCaseTest | 8 | Zone creation, duplicate names, role enforcement |
| CreateProductUseCaseTest | 12 | Product creation, validation (name, price, tax rate, status), role enforcement, regulated products |

#### Instrumented Android Tests

| Test Class | Cases | Coverage |
|------------|-------|----------|
| SessionManagerTest | 11 | EncryptedSharedPreferences session lifecycle, all 4 roles, field persistence |
| NavigationArgumentTest | 8 | Bundle key propagation across all nav flows (employer, case, cart, checkout, task) |
| TaskRepositoryImplTest | 8 | Atomic claim/complete with side effects, rollback on conflict, org scoping |
| OrderRepositoryImplTest | 6 | Finalize atomicity, cart-to-order pipeline, org isolation |
| ComplianceCaseRepositoryImplTest | 6 | Insert + audit log atomicity, duplicate rejection, status update |
| EmployerRepositoryImplTest | 9 | Update + audit log atomicity, suspension, throttle toggle, EIN + org scoping |
| RoleGuardTest | 14 | Production RoleGuard.hasRole() with real SessionManager for all 4 roles + edge cases |
| UserRepositoryImplTest | 7 | Insert, find by username/id, auth info, lockout, duplicate prevention |
| WorkerRepositoryImplTest | 9 | Insert, org scoping, workload adjust, reputation score, status filter, update |
| CartRepositoryImplTest | 11 | Cart creation, active cart lookup, item CRUD, conflict count, delete |
| ProductRepositoryImplTest | 5 | Insert, org scoping, active filter, update, regulated flag |
| ReportRepositoryImplTest | 6 | File report, org scoping, status update, optional fields, evidence preservation |
| ZoneRepositoryImplTest | 6 | Insert, org scoping, list filter, update, null description |
| AuditLogRepositoryImplTest | 5 | Insert-only logging, multiple entries, duplicate rejection, null caseId |
| SensitiveWordRepositoryImplTest | 6 | Add/list, zero-tolerance filter, remove, empty repo |
| LoginViewModelTest | 7 | Session state checks, logout, invalid credential error, ViewModel wiring |
| CheckoutViewModelTest | 4 | Create order from cart, order items loading, empty cart error, stale warning |
| LoginFlowIntegrationTest | 8 | Full register → login → session per role, invalid creds, lockout, logout |
| CommerceFlowIntegrationTest | 4 | Catalog → cart → checkout → finalize, cart isolation, cart merging, org isolation |
| DispatchFlowIntegrationTest | 5 | Full create → claim → complete, role checks, org boundary, duplicate prevention |
| TaskListViewModelTest | 7 | Task creation via ViewModel, content scanning (clean + flagged), my-tasks loading, LiveData accessors |
| ReportViewModelTest | 6 | Report filing, case linking, unauthorized role rejection, worker filing, LiveData accessors |
| ComplianceFlowIntegrationTest | 10 | Employer verify, case creation, report filing, enforcement, full compliance flow |
| DiscountRuleDaoTest | 7 | Insert, find by ID+org, update, active filter, batch find, org isolation, duplicate rejection |
| ShippingTemplateDaoTest | 6 | Insert, list by org, find by ID, org isolation, upsert (REPLACE), pickup flag |
| ReputationEventDaoTest | 7 | Insert, recent events, average score, limit, worker filter, duplicate rejection, nullable fields |
| TaskAcceptanceDaoTest | 6 | Insert, get by task, find by task+worker, multiple acceptances, duplicate rejection, empty result |
| OrderDiscountDaoTest | 5 | Insert, get by order, multiple discounts, delete by order, duplicate composite key |
| OrderItemDaoTest | 6 | Insert, insertAll, delete by order, empty result, regulated flag, duplicate rejection |
| CartItemDaoTest | 9 | Insert, find by cart+product, update, delete, conflict count, empty cart, duplicate rejection |
| CatalogViewModelTest | 6 | Product loading, lazy init, search, null/empty query reset, error LiveData |
| CartViewModelTest | 6 | Add to cart, invalid product error, cart items, conflicts, loaded cart, error LiveData |
| UserManagementViewModelTest | 5 | Registration success, short password, duplicate username, zone ID, LiveData accessor |
| ZoneViewModelTest | 7 | Create zone, empty name error, invalid score, score too high, update, empty name update, LiveData |
| TaskDetailViewModelTest | 7 | Load task, nonexistent task error, accept task, non-worker start error, LiveData accessors |
| EmployerViewModelTest | 7 | Verify employer, invalid EIN error, wrong role, clean scan, flagged scan, LiveData accessors |
| ComplianceCaseViewModelTest | 8 | Open case, wrong role error, enforce warning, wrong role enforce, LiveData accessors |
| AppBootstrapTest | 18 | ServiceLocator init, session manager, database, all 12 repositories, all 21 use cases, singleton, DB open, RoadRunnerApp singleton, MainActivity launch, seed data verification (4 users, templates, zones, products, discount rule) |
| LoginFragmentTest | 3 | Login form display, empty-field error, text input acceptance |
| AdminFragmentTest | 10 | AdminDashboard cards + denial, AdminConfig sections + denial, ImportExport buttons + denial, UserManagement list + denial, OrderList recycler + denial |
| CommerceFragmentTest | 9 | Catalog product list + search + denial, Cart layout + denial, Checkout buttons + denial, Invoice layout + denial |
| DispatchFragmentTest | 10 | DispatcherDashboard cards + dual-role + denial, WorkerDashboard cards + denial, TaskList dispatcher + worker, TaskDetail layout, Zone list + denial |
| ComplianceFragmentTest | 15 | ComplianceDashboard cards + admin + denial, EmployerList + denial, EmployerDetail form + denial, CaseList + admin-denied + worker-denied, CaseDetail + denial, Report form + worker-allowed + denial |
| UiJourneyTest | 9 | Admin login→dashboard→catalog, admin→users, dispatcher→tasks, dispatcher→zones, worker→tasks, compliance→employers, compliance→cases, invalid credentials error, empty credentials error |

---

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
| Use Cases | `core/domain/usecase/` | 21 business logic classes |
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
- Build: JDK 17–21, Android SDK 34
- Runtime: Android device/emulator with API 26+, `adb` on the host PATH
