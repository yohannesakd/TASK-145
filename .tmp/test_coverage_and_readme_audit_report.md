# Test Coverage Audit

## Backend Endpoint Inventory

No backend HTTP endpoints were found.

Evidence:

- `README.md:14` states there is no server and no web UI.
- `app/src/main/AndroidManifest.xml:13-21` declares only `MainActivity`.
- `app/src/main/java/com/roadrunner/dispatch/MainActivity.java:9-15` describes a single-activity Android host.
- `app/src/main/res/navigation/nav_graph.xml:9-301` defines Android fragment destinations, not HTTP routes.

Resolved endpoint total:

- Total endpoints: `0`

## API Test Mapping Table

| Endpoint                                                             | Covered | Test Type | Test Files | Evidence                                                                                     |
| -------------------------------------------------------------------- | ------- | --------- | ---------- | -------------------------------------------------------------------------------------------- |
| None. No backend `METHOD + PATH` endpoints exist in this repository. | N/A     | N/A       | None       | `README.md:14`; `AndroidManifest.xml:13-21`; `MainActivity.java:9-15`; `nav_graph.xml:9-301` |

## Coverage Summary

- Total endpoints: `0`
- Endpoints with HTTP tests: `0`
- Endpoints with true no-mock HTTP tests: `0`
- HTTP coverage: `N/A`
- True API coverage: `N/A`

## Unit Test Summary

Confirmed directly covered areas:

- all 20 domain use cases have dedicated JVM tests under `app/src/test/java/com/roadrunner/dispatch`
- all 12 repository implementations have dedicated instrumented tests under `app/src/androidTest/java/com/roadrunner/dispatch`
- bootstrap and wiring are covered by `app/src/androidTest/java/com/roadrunner/dispatch/AppBootstrapTest.java:20-218`
- fragment UI coverage exists in:
    - `app/src/androidTest/java/com/roadrunner/dispatch/ui/LoginFragmentTest.java:26-71`
    - `app/src/androidTest/java/com/roadrunner/dispatch/ui/AdminFragmentTest.java:29-164`
    - `app/src/androidTest/java/com/roadrunner/dispatch/ui/CommerceFragmentTest.java:29-149`
    - `app/src/androidTest/java/com/roadrunner/dispatch/ui/DispatchFragmentTest.java`
    - `app/src/androidTest/java/com/roadrunner/dispatch/ui/ComplianceFragmentTest.java`
- direct view-model coverage exists for all previously missing view models, including:
    - `CatalogViewModelTest.java`
    - `CartViewModelTest.java`
    - `UserManagementViewModelTest.java`
    - `ZoneViewModelTest.java`
    - `TaskDetailViewModelTest.java`
    - `EmployerViewModelTest.java`
    - `ComplianceCaseViewModelTest.java`
- direct DAO coverage exists for:
    - `DiscountRuleDaoTest.java`
    - `ShippingTemplateDaoTest.java`
    - `ReputationEventDaoTest.java`
    - `TaskAcceptanceDaoTest.java`
    - `OrderDiscountDaoTest.java`
    - `OrderItemDaoTest.java`
    - `CartItemDaoTest.java`
- multi-screen UI journey coverage exists in `app/src/androidTest/java/com/roadrunner/dispatch/ui/UiJourneyTest.java:28-242`

## Tests Check

### Success Paths

Strong.

Evidence:

- `LoginFlowIntegrationTest.java`
- `CommerceFlowIntegrationTest.java`
- `DispatchFlowIntegrationTest.java`
- `ComplianceFlowIntegrationTest.java`
- `UiJourneyTest.java:67-210`

### Failure Cases

Present.

Evidence:

- `LoginFlowIntegrationTest.java:108-149`
- `CheckoutViewModelTest.java:137-149`
- `UiJourneyTest.java:215-241`

### API Observability Check

Not applicable because no backend API exists.

### Remaining Confirmed Test Issue

`run_tests.sh` does not guarantee that instrumented tests run in default mode.

Observed behavior:

- JVM tests and lint always run through Docker
- instrumented tests run only when a device/emulator is connected
- otherwise they are skipped in default mode
- `--full` converts missing device/emulator into a failure

Evidence:

- `run_tests.sh:19-67`

Impact:

- a default `./run_tests.sh` success does not prove the instrumented suite ran

## Test Coverage Score (0-100)

`92/100`

## Score Rationale

- broad use-case coverage
- broad repository coverage
- bootstrap coverage present
- fragment UI coverage present
- multi-screen UI journey coverage present
- DAO coverage present
- score reduced only because default runner mode can pass without running instrumented tests

## Key Gaps

- no backend/API surface exists, so API-specific coverage is non-applicable rather than satisfied
- default `run_tests.sh` mode can skip instrumented tests when no device/emulator is connected

## Confidence And Assumptions

- confidence is high that this is an Android-only repository with no backend HTTP layer
- confidence is high that the current test suite is substantially broader than earlier versions because the test tree now includes `AppBootstrapTest`, multiple UI fragment tests, DAO tests, view-model tests, and `UiJourneyTest`

# README Audit

## High Priority Issues

- The README still fails the strict Docker-contained environment rule.

Evidence:

- `README.md:20` states Docker handles build/JVM testing, while device/emulator handles install, manual verification, and instrumented tests.
- `README.md:24-28` requires host emulator/device and host `adb`, and documents an optional fully local workflow.
- `README.md:98-102` states instrumented tests require a real Android runtime and cannot run inside Docker.

Impact:

- under the strict rubric, the workflow is not fully Docker-contained

## Medium Priority Issues

- None confirmed.

## Low Priority Issues

- None confirmed.

## Hard Gate Failures

- `Environment Rules (STRICT)` failed because instrumented tests and runtime verification still require host Android runtime access and host `adb`.

## README Verdict

`FAIL`
