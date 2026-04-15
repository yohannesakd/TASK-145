# Design Document

## Architecture

Clean Architecture (Onion) with three layers:

```
Presentation → Domain → Infrastructure
```

- **Presentation**: Android Fragments, ViewModels, Adapters, Layouts. Framework-dependent.
- **Domain**: Pure Java use cases, models, repository interfaces. Zero Android imports.
- **Infrastructure**: Room DAOs, repository implementations, security (EncryptedSharedPreferences), DB seeding.

### Why Clean Architecture
1. Domain layer is pure Java — tests run on JVM without emulator
2. Repository interfaces decouple business logic from persistence
3. Use cases enforce all business invariants independent of UI

## Data Model

19 Room entities, 19 DAOs, single SQLite database (`roadrunner_db`).

### Entity Relationships

```
User ──< Worker ──< TaskAcceptance >── Task
                                       │
Task ──< Zone (via zoneId)             │
                                       │
Cart ──< CartItem >── Product          │
  │                                    │
  └── Order ──< OrderItem             │
         │                             │
         ├──< OrderDiscount >── DiscountRule
         └── ShippingTemplate

Employer ──< ComplianceCase ──< Report
                                  │
                              AuditLog

SensitiveWord (standalone lookup)
ReputationEvent ──> Worker
```

### Key Indexes
- `(orgId, status, updatedAt)` on Task, Order, Product, ComplianceCase
- `(orgId, status)` and `(orgId, ein)` UNIQUE on Employer
- `(brand, series, model)` on Product
- `(customerId, storeId)` on Cart (for merge lookup)
- `(taskId, acceptedBy)` UNIQUE on TaskAcceptance (duplicate prevention)

## Domain Use Cases (20)

| Use Case | Input | Output | Key Invariants |
|----------|-------|--------|----------------|
| AddToCart | customerId, storeId, productId, qty | CartItem | Merge by customer+store; flag price conflicts |
| ComputeOrderTotals | items, discounts, shipping | OrderTotals | Tax on pre-discount amounts; ±$0.01 consistency |
| ValidateDiscounts | discount list | validated list | Max 3; cumulative ≤40% |
| FinalizeCheckout | orderId, actorId, actorRole, orgId | Order | DRAFT guard, staleness, regulated notes, re-compute |
| CreateOrderFromCart | cartId, userId, actorRole | orderId | No conflicts; transactional insert |
| ResolveCartConflict | cartId, productId, price | CartItem | Clear conflict flag |
| AcceptTask | taskId, workerId, actorRole, orgId | Task | OPEN status, no duplicate, WORKER/DISPATCHER |
| CreateTask | orgId, title, description, mode, priority, zoneId, windowStart, windowEnd, creatorId, actorRole | Task | DISPATCHER/ADMIN only, zone exists |
| CompleteTask | taskId, workerId, actorRole, orgId | Task | Status transition, workload, reputation |
| MatchTasks | task/worker, weights, orgId | ScoredWorker[] | Weighted: time, workload, reputation, zone |
| VerifyEmployer | Employer, actorRole | Employer | EIN, state, ZIP regex validation |
| ScanContent | content | ContentScanResult | Whole-word \\b boundary, zero-tolerance first |
| EnforceViolation | employerId, action, actorId, caseId, orgId, isZeroTolerance, actorRole | Employer | 2 warnings before suspend; audit log |
| FileReport | orgId, reportedBy, targetType, targetId, description, evidenceUri, evidenceHash, actorRole | Report | SHA-256 evidence fingerprint; role: COMPLIANCE_REVIEWER or WORKER |
| OpenCase | orgId, employerId, caseType, severity, description, createdBy, actorRole | ComplianceCase | COMPLIANCE_REVIEWER only; audit log entry |
| Login | username, password | Session | PBKDF2 verify, 5-attempt lockout |
| RegisterUser | orgId, username, password, role | User | Hash + salt, duplicate check, auto-creates Worker profile |
| CreateDiscountRuleUseCase | DiscountRule, actorRole | DiscountRule | ADMIN only; validates rule fields |
| CreateShippingTemplateUseCase | ShippingTemplate, actorRole | ShippingTemplate | ADMIN only; validates template fields |
| CreateZoneUseCase | Zone, actorRole | Zone | ADMIN/DISPATCHER only; validates zone fields |

## Security Design

### Authentication
- PBKDF2WithHmacSHA256 with 120,000 iterations and 32-byte SecureRandom salt
- 12-character minimum password length
- 5-attempt lockout with 15-minute window

### Session Storage
- EncryptedSharedPreferences with AES256-GCM (value) and AES256-SIV (key)
- No plaintext fallback — RuntimeException on encryption failure

### Authorization
- Role checks at use case layer (not just UI)
- orgId-scoped queries at DAO layer (findByIdAndOrg methods)
- Insert-only AuditLogDao (no update/delete)

### Data Isolation
- All multi-tenant entities carry orgId
- Repository interfaces expose `getByIdScoped(id, orgId)` methods
- DAO queries filter by orgId

## Navigation

Single Activity (`MainActivity`) with Navigation Component. 21 fragment destinations.

```
Login ──┬── AdminDashboard ──── Catalog, Employers, Orders, Config, Import/Export, Users
        ├── DispatcherDashboard ── Tasks, Zones
        ├── WorkerDashboard ──── Tasks, Catalog, Reports
        └── ComplianceDashboard ── Cases, Employers, Reports, Tasks, Orders

Catalog → Cart → Checkout → Invoice (popUpTo Catalog)
Tasks → TaskDetail
Employers → EmployerDetail
Cases → CaseDetail → Report
```

## Dependency Injection

Manual ServiceLocator (no Dagger/Hilt). Lazy-initialized repositories, fresh-per-call use cases.

## Performance

- RecyclerView + ListAdapter + DiffUtil.ItemCallback for 60fps lists
- LRU image cache (20MB cap, min of maxMemory/8)
- Background IO via ViewModel + ExecutorService
- Composite indexes on high-frequency query patterns
