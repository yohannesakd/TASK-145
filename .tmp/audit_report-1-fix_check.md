# Audit Report 1 Fix Status Refresh

## Verdict
- Overall status: Mostly Fixed
- Fixed: 6
- Partially Fixed: 0
- Not Fixed: 0

## Issue-by-Issue Report

### 1. `ComputeOrderTotalsUseCase` cannot detect mismatched displayed totals
- Previous severity: High
- Current status: Fixed
- What changed: `ComputeOrderTotalsUseCase` no longer exposes misleading internal `consistent` or `discrepancy` fields through `OrderTotals`. It now only computes monetary values, while checkout consistency is enforced where an independent persisted total exists during finalization.
- Evidence: `app/src/main/java/com/roadrunner/dispatch/core/domain/usecase/ComputeOrderTotalsUseCase.java:63-70`, `app/src/main/java/com/roadrunner/dispatch/core/domain/model/OrderTotals.java:3-17`, `app/src/main/java/com/roadrunner/dispatch/core/domain/usecase/FinalizeCheckoutUseCase.java:146-162`

### 2. Public data-access APIs still permit cross-org lookups
- Previous severity: High
- Current status: Fixed
- What changed: The previously flagged worker path is now fully org-scoped, and the DAO/repository interfaces cited in the earlier audit no longer expose the specific unscoped accessors that were part of that finding. `WorkerDashboardFragment` now uses `getByUserIdScoped(...)`.
- Evidence: `app/src/main/java/com/roadrunner/dispatch/core/domain/repository/WorkerRepository.java:8-20`, `app/src/main/java/com/roadrunner/dispatch/infrastructure/db/dao/WorkerDao.java:23-29`, `app/src/main/java/com/roadrunner/dispatch/presentation/dispatch/WorkerDashboardFragment.java:87-93`, `app/src/main/java/com/roadrunner/dispatch/infrastructure/db/dao/OrderDao.java:23-36`, `app/src/main/java/com/roadrunner/dispatch/infrastructure/db/dao/ProductDao.java:23-36`, `app/src/main/java/com/roadrunner/dispatch/infrastructure/db/dao/EmployerDao.java:24-38`

### 3. Worker tablet dashboard drops catalog and reporting actions
- Previous severity: Medium
- Current status: Fixed
- What changed: The tablet worker dashboard layout now includes `card_catalog` and `card_reports`, matching the fragment's expected IDs and behavior.
- Evidence: `app/src/main/res/layout-sw600dp/fragment_worker_dashboard.xml:161-230`, `app/src/main/java/com/roadrunner/dispatch/presentation/dispatch/WorkerDashboardFragment.java:151-170`

### 4. Several list screens bypass their own DiffUtil strategy
- Previous severity: Medium
- Current status: Fixed
- What changed: `OrderListFragment` now keeps a single `OrderAdapter` instance and submits new lists to it. `AdminConfigFragment` now also holds persistent adapter instances for products, shipping templates, and discount rules and reuses them.
- Evidence: `app/src/main/java/com/roadrunner/dispatch/presentation/admin/OrderListFragment.java:24-27`, `app/src/main/java/com/roadrunner/dispatch/presentation/admin/OrderListFragment.java:54-67`, `app/src/main/java/com/roadrunner/dispatch/presentation/admin/AdminConfigFragment.java:45-47`, `app/src/main/java/com/roadrunner/dispatch/presentation/admin/AdminConfigFragment.java:86-91`, `app/src/main/java/com/roadrunner/dispatch/presentation/admin/AdminConfigFragment.java:118-129`

### 5. APK-only delivery is diluted by Docker-centric repository artifacts
- Previous severity: Medium
- Current status: Fixed
- What changed: `Dockerfile` and `docker-compose.yml` are no longer present, and the Docker-oriented section/header previously cited in `PLAN.md` has been removed. The remaining plan content no longer presents Docker as part of the delivery path.
- Evidence: `PLAN.md:17-19`, `PLAN.md:40-51`, `PLAN.md:1125-1244`, repository check: no `Dockerfile`, no `docker-compose.yml`

### 6. Sensitive-data logging protection is advisory, not enforced
- Previous severity: Low
- Current status: Fixed
- What changed: `AppLogger` now explicitly requires all app logging to go through it, and the previously flagged direct logging call in `ReportFragment` now uses `AppLogger.error(...)`.
- Evidence: `app/src/main/java/com/roadrunner/dispatch/core/util/AppLogger.java:5-12`, `app/src/main/java/com/roadrunner/dispatch/core/util/AppLogger.java:18-41`, `app/src/main/java/com/roadrunner/dispatch/presentation/compliance/reports/ReportFragment.java:274-279`

## Final Summary
- All six issues previously listed in `.tmp/audit_report-1.md` are fixed in the current source tree based on static review.
- This does not prove runtime behavior; it confirms that the previously cited code/documentation defects are no longer present in the same form.
