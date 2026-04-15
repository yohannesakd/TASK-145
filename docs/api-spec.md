# Internal API Specification

This application has no network API. The "API" is the domain use case layer — the internal contract between presentation and business logic.

## Use Case Contracts

All use cases return `Result<T>` which wraps either success data or a list of error strings.

### Commerce

#### AddToCartUseCase.execute
```java
Result<CartItem> execute(String customerId, String storeId, String productId, int quantity, String orgId, String userId)
```
- **Preconditions**: quantity > 0, product exists and ACTIVE
- **Behavior**: Finds or creates cart for (customerId, storeId). Merges quantity if product already in cart. Detects price conflicts.
- **Errors**: "Quantity must be positive", "Product not found or inactive"

#### ComputeOrderTotalsUseCase.execute
```java
Result<OrderTotals> execute(List<OrderItem> items, List<DiscountRule> discounts, ShippingTemplate shipping)
```
- **Preconditions**: items non-empty
- **Behavior**: Computes subtotal, applies discounts (capped at subtotal), calculates tax on pre-discount amounts, adds shipping, checks ±$0.01 consistency.
- **Returns**: OrderTotals with subtotalCents, discountCents, taxCents, shippingCents, totalCents, consistent flag

#### ValidateDiscountsUseCase.execute
```java
Result<List<DiscountRule>> execute(List<DiscountRule> discounts)
```
- **Constraints**: Max 3 discounts, cumulative PERCENT_OFF ≤ 40%
- **Errors**: "Maximum 3 discounts allowed", "Cumulative percent-off exceeds 40%"

#### FinalizeCheckoutUseCase.execute
```java
Result<Order> execute(String orderId, String actorId, String actorRole, String orgId)
```
- **Allowed roles**: ADMIN, DISPATCHER, WORKER
- **Guards**: DRAFT status, totalsStale=false, totalsComputedAt≠0, has items, regulated items require notes, discount validation, consistency re-check
- **Side effect**: Finalizes order + audit log atomically via `finalizeOrder` (single transaction)

#### CreateOrderFromCartUseCase.execute
```java
Result<String> execute(String cartId, String userId, String actorRole)
```
- **Allowed roles**: ADMIN, DISPATCHER, WORKER
- **Preconditions**: Cart exists, has items, no price conflicts
- **Behavior**: Creates DRAFT order with items (transactional)
- **Returns**: orderId

#### ResolveCartConflictUseCase.execute
```java
Result<CartItem> execute(String cartId, String productId, long chosenPriceCents)
```
- **Behavior**: Sets chosen price, clears conflictFlag

### Dispatch

#### CreateTaskUseCase.execute
```java
Result<Task> execute(String orgId, String title, String description, String mode, int priority, String zoneId, long windowStart, long windowEnd, String creatorId, String actorRole)
```
- **Allowed roles**: DISPATCHER, ADMIN
- **Validation**: Title non-empty, mode in (GRAB_ORDER, ASSIGNED), zone exists, windowEnd > windowStart, content scan (ZERO_TOLERANCE rejected)
- **Side effect**: Inserts task with OPEN status

#### AcceptTaskUseCase.execute
```java
Result<Task> execute(String taskId, String workerId, String actorRole, String orgId)
```
- **Allowed roles**: WORKER (grab-order self-claim), DISPATCHER (ASSIGNED-mode direct assignment)
- **Guards**: Task OPEN, orgId match; WORKER additionally: worker must exist in org (getByIdScoped), no prior acceptance by same worker (hasAcceptance check + UNIQUE constraint)
- **Side effects**: Task→ASSIGNED, workload+1, acceptance record, audit log — all via `claimTaskWithSideEffects` (atomic transaction)
- **Concurrency**: 3-second ConcurrentHashMap mutex per taskId (WORKER path only; DISPATCHER bypasses mutex)

#### MatchTasksUseCase.rankWorkersForTask / rankTasksForWorker
```java
List<ScoredWorker> rankWorkersForTask(Task task, MatchingWeights weights, String orgId)
List<ScoredTask> rankTasksForWorker(Worker worker, MatchingWeights weights, String orgId)
```
- **Formula**: `score = w1*timeWindow + w2*workload + w3*reputation - w4*zoneDistance`
- **Zone scoring**: A=1, B=2, C=3, D=4, E=5. Distance = |taskZoneScore - workerZoneScore| / 4.0 (normalized)

#### CompleteTaskUseCase.execute
```java
Result<Task> execute(String taskId, String workerId, String actorRole, String orgId)
```
- **Allowed roles**: WORKER
- **Guards**: Task IN_PROGRESS or ASSIGNED, assigned to this worker
- **Side effects**: Task→COMPLETED (atomic transaction: workload-1, reputation event, rep score update)

### Compliance

#### VerifyEmployerUseCase.execute
```java
Result<Employer> execute(Employer employer, String actorRole)
```
- **Allowed roles**: ADMIN, COMPLIANCE_REVIEWER
- **Validation**: EIN `^\d{2}-\d{7}$`, State must be a valid US state/territory code, ZIP `^\d{5}(-\d{4})?$`, street address must start with a house number

#### ScanContentUseCase.execute
```java
ContentScanResult execute(String content)
```
- **Behavior**: Loads sensitive words from repository, checks zero-tolerance words first, then all sensitive words. Uses `\b` + Pattern.quote + `\b` for whole-word matching.
- **Returns**: CLEAN, FLAGGED (with matched words), or ZERO_TOLERANCE

#### EnforceViolationUseCase.execute
```java
Result<Employer> execute(String employerId, String action, String actorId, String caseId, String orgId, boolean isZeroTolerance, String actorRole)
```
- **Allowed roles**: COMPLIANCE_REVIEWER
- **Actions**: WARN, TAKEDOWN, SUSPEND_7, SUSPEND_30, SUSPEND_365, THROTTLE
- **Anti-harassment**: If not zero-tolerance and warningCount < 2, issues warning instead of suspension
- **Side effect**: Updates employer + audit log atomically via `updateWithAuditLog` (single transaction)

#### FileReportUseCase.execute
```java
Result<Report> execute(String orgId, String reportedBy, String targetType, String targetId, String description, String evidenceUri, String evidenceHash, String actorRole)
```
- **Allowed roles**: COMPLIANCE_REVIEWER, WORKER
- **Validation**: Target entity validated org-scoped for EMPLOYER, ORDER, and TASK target types
- **Behavior**: Creates report with SHA-256 evidence fingerprint

#### OpenCaseUseCase.execute
```java
Result<ComplianceCase> execute(String orgId, String employerId, String caseType, String severity, String description, String createdBy, String actorRole)
```
- **Allowed roles**: COMPLIANCE_REVIEWER (ADMIN excluded — case management is a compliance-only function)
- **Validation**: Non-blank description required; if employerId is provided, it must exist in the employer repository
- **Behavior**: Creates case + audit log atomically via `insertWithAuditLog`

### Auth

#### LoginUseCase.execute
```java
Result<Session> execute(String username, String password)
```
- **Lockout**: 5 failed attempts → 15-minute lock
- **Behavior**: PBKDF2 password verification, resets failed count on success

#### RegisterUserUseCase.execute
```java
Result<User> execute(String orgId, String username, String password, String role)
Result<User> execute(String orgId, String username, String password, String role, String zoneId)
```
- **Validation**: Password ≥ 12 chars, username unique, valid role
- **Behavior**: Generates salt, hashes password with PBKDF2. Auto-creates paired Worker profile when role is WORKER (uses zoneId if provided).

### Admin Configuration

#### CreateDiscountRuleUseCase.execute
```java
Result<DiscountRule> execute(DiscountRule rule, String actorRole)
```
- **Allowed roles**: ADMIN
- **Validation**: Name required, type must be PERCENT_OFF or FLAT_OFF, value non-negative, percentage ≤100
- **Behavior**: Inserts discount rule into the order repository

#### CreateShippingTemplateUseCase.execute
```java
Result<ShippingTemplate> execute(ShippingTemplate template, String actorRole)
```
- **Allowed roles**: ADMIN
- **Validation**: Name required, cost non-negative, minDays/maxDays non-negative, maxDays ≥ minDays
- **Behavior**: Inserts shipping template into the order repository

#### CreateZoneUseCase.execute
```java
Result<Zone> execute(Zone zone, String actorRole)
```
- **Allowed roles**: ADMIN
- **Validation**: Name required, score between 1 and 5
- **Behavior**: Inserts zone into the zone repository

## Repository Interfaces (12)

All entity-bearing repositories expose:
- `getById(String id)` — unscopedlookup
- `getByIdScoped(String id, String orgId)` — tenant-isolated lookup
- Standard CRUD: `insert()`, `update()`

## Data Types

### Result<T>
```java
boolean isSuccess()
T getData()           // null on failure
List<String> getErrors()  // empty on success
String getFirstError()
```

### OrderTotals
```java
long subtotalCents, discountCents, taxCents, shippingCents, totalCents
boolean consistent   // |expected - actual| ≤ 1
long discrepancy     // absolute difference in cents
```

### ContentScanResult
```java
enum Status { CLEAN, FLAGGED, ZERO_TOLERANCE }
List<String> matchedWords
```

### ScoredWorker
```java
Worker worker
double score
Map<String, Double> breakdown  // component scores
```
