# Test Coverage Report

## Summary

- **JVM unit test files**: 21
- **JVM unit test cases**: 298
- **Instrumented test files**: 6 (androidTest)
- **Instrumented test cases**: 48
- **Test type**: JVM unit tests + Room-backed integration tests + navigation/auth tests
- **Framework**: JUnit 4 with inline stub repositories (unit), Room in-memory DB (integration)
- **Verified**: `./gradlew assembleDebug` passes, JVM tests run via `./gradlew test`.

## Verification Method

The domain layer has zero Android dependencies. Tests can be verified by:
1. Compiling all domain source files with `javac --release 17`
2. Compiling all test files against domain classes + JUnit 4
3. Executing via `./gradlew test`

LiveData references in repository interfaces required a trivial stub (empty abstract class) since they appear only in method signatures unused by tests.

## Coverage by Use Case

| Use Case | Test File | Cases | Happy | Failure | Edge | Role Auth |
|----------|-----------|-------|-------|---------|------|-----------|
| ValidateDiscounts | ValidateDiscountsUseCaseTest | 13 | 3 | 4 | 6 | N/A (pure validation) |
| ComputeOrderTotals | ComputeOrderTotalsUseCaseTest | 13 | 4 | 2 | 7 | N/A (pure computation) |
| AddToCart | AddToCartUseCaseTest | 9 | 3 | 3 | 3 | N/A |
| Login | LoginUseCaseTest | 13 | 3 | 5 | 5 | Implicit (credential) |
| VerifyEmployer | VerifyEmployerUseCaseTest | 18 | 3 | 8 | 7 | Yes |
| ScanContent | ScanContentUseCaseTest | 15 | 3 | 0 | 12 | N/A (pure scan) |
| EnforceViolation | EnforceViolationUseCaseTest | 24 | 4 | 6 | 10 | Yes (4 tests) |
| AcceptTask | AcceptTaskUseCaseTest | 16 | 3 | 7 | 3 | Yes (3 tests) |
| CreateTask | CreateTaskUseCaseTest | 20 | 2 | 8 | 7 | Yes (3 tests) |
| FinalizeCheckout | FinalizeCheckoutUseCaseTest | 20 | 2 | 10 | 6 | Yes (2 tests) |
| MatchTasks | MatchTasksUseCaseTest | 10 | 4 | 1 | 5 | N/A (pure scoring) |
| PasswordHasher | PasswordHasherTest | 16 | 4 | 3 | 9 | N/A (utility) |
| CompleteTask | CompleteTaskUseCaseTest | 11 | 2 | 5 | 4 | Yes (2 tests) |
| CreateOrderFromCart | CreateOrderFromCartUseCaseTest | 12 | 3 | 5 | 4 | N/A |
| RegisterUser | RegisterUserUseCaseTest | 7 | 2 | 3 | 2 | N/A |
| ResolveCartConflict | ResolveCartConflictUseCaseTest | 6 | 3 | 2 | 1 | N/A |
| FileReport | FileReportUseCaseTest | 19 | 4 | 8 | 4 | Yes (3 tests) |
| OpenCase | OpenCaseUseCaseTest | 11 | 3 | 4 | 2 | Yes (3 tests) |
| AppLoggerMask | AppLoggerMaskTest | 10 | 2 | 1 | 7 | N/A (utility) |
| ImportValidation | ImportValidationTest | 14 | 4 | 5 | 5 | Yes (2 tests) |
| RouteAuthorization | RouteAuthorizationTest | 21 | 15 | 0 | 6 | Yes (all tests) |

## Coverage by Requirement Area

### Commerce
- **Cart merge**: Same customer+store reuse, quantity addition, price conflict detection/flagging
- **Discount validation**: Max 3 cap, cumulative ≤40% percent-off, mixed percent+flat
- **Order totals**: Discount allocation (capped at subtotal), tax on pre-discount amounts, ±$0.01 consistency
- **Checkout finalization**: DRAFT status guard, staleness guard, regulated item notes, re-computation

### Dispatch
- **Task creation**: Title/mode/zone validation, time window checks, multi-error collection
- **Task matching**: Weighted scoring formula, zone proximity, GRAB_ORDER and ASSIGNED modes
- **Task acceptance**: OPEN status guard, duplicate prevention, workload increment
- **Task completion**: Status transition, workload decrement, reputation event recording

### Compliance
- **Employer verification**: EIN (XX-XXXXXXX), state (2 uppercase), ZIP (5 or 5+4)
- **Content scanning**: Whole-word boundary, zero-tolerance priority, case insensitivity
- **Enforcement**: 2-warning escalation, zero-tolerance bypass, all action types, audit logging

### Security
- **Password hashing**: PBKDF2 120K iterations, salt uniqueness, deterministic verify
- **Login lockout**: 5 attempts, 15-minute window, reset on success
- **Role-based access**: Unauthorized roles rejected at use case layer (10 dedicated tests)
- **Log hygiene**: AppLogger.mask() truncates IDs to 4 chars, null-safe, prevents PII leakage (10 tests)
- **Route authorization**: Fragment-level role matrix verified for all 15 routes across 4 roles (19 tests)

### Import/Export
- **SHA-256 fingerprinting**: Deterministic hashing, tamper detection, hex format validation
- **Employer import validation**: EIN/state/ZIP format enforcement, duplicate EIN rejection, role gating
- **Skip counting**: Invalid records counted and reported to user

## Instrumented Tests (androidTest)

Six instrumented test files verify transactional atomicity and on-device behavior:

| Test File | Cases | Coverage |
|-----------|-------|----------|
| TaskRepositoryImplTest | 8 | `claimTaskWithSideEffects` atomicity, rollback on conflict, `completeTaskWithSideEffects` atomicity, org-scoped lookups |
| ComplianceCaseRepositoryImplTest | 6 | `insertWithAuditLog` atomicity, duplicate PK rejection, org-scoped lookups |
| EmployerRepositoryImplTest | 9 | `updateWithAuditLog` (warning/suspend/throttle/unthrottle), EIN-scoped lookup, org isolation |
| OrderRepositoryImplTest | 6 | `finalizeOrder` transactional update + audit, `createOrderFromCart` atomicity, org-scoped lookups |
| SessionManagerTest | 11 | EncryptedSharedPreferences session lifecycle, role storage for all 4 roles, data integrity, overwrite, clear |
| NavigationArgumentTest | 8 | Bundle key consistency for employer/case/task/cart/checkout/invoice routes |

All instrumented tests use `Room.inMemoryDatabaseBuilder()` with `allowMainThreadQueries()`, bypassing SQLCipher encryption for test isolation.

## Gaps & Limitations

- **FileReportUseCase**: Role validation is enforced (COMPLIANCE_REVIEWER or WORKER); tested via `workerRole_accepted` and `dispatcherRole_rejected` in `FileReportUseCaseTest`
- **LiveData observers**: Not tested (presentation layer, requires Android lifecycle)
- **FinalizeCheckoutUseCaseTest**: Uses a stub `OrderRepository.finalizeOrder()` that skips the transactional behavior — the real transactional path is covered by `OrderRepositoryImplTest` (androidTest)
- **Manual-only items**: Tablet rendering, 10K-row list performance, image memory, ANR/main-thread, on-device SQLCipher encryption — require manual device testing

### Static vs. Runtime Verification

The figures above (test case counts, happy/failure/edge breakdowns) reflect what the JVM unit tests and instrumented tests exercise statically. They do **not** imply runtime verification of the following:

- **SQLCipher encryption at rest**: instrumented tests use `Room.inMemoryDatabaseBuilder()` which bypasses SQLCipher. Actual on-device encryption requires manual verification with a real device build.
- **EncryptedSharedPreferences key rotation**: `SessionManagerTest` verifies read/write lifecycle but cannot confirm that Android Keystore-backed key rotation behaves correctly across OS upgrades without device testing.
- **Content moderation word lists**: `ScanContentUseCaseTest` exercises scanning logic against an in-memory stub word list. Production behavior depends on the word list stored in the live database, which is not statically verifiable.
- **SHA-256 fingerprint verification during import**: The import hash-check logic is unit-testable in isolation, but correct end-to-end behaviour (export → file → import → verify) requires integration testing with real file I/O.

Any claim of "verified" in this document refers to test execution passing under the described test setup, not to production runtime correctness.
