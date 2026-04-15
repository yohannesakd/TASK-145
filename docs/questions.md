# RoadRunner Dispatch & Commerce Console
## Clarification Questions, Assumptions, and Solutions

This document resolves as many implementation ambiguities as possible while staying aligned with the original prompt. The prompt is treated as the source of truth. Where the prompt is silent, the narrowest assumption is chosen to avoid drifting from the intended scope.

---

### Question: Is this one Android app or multiple apps?
**Assumption:** It is one standalone Android app containing all roles and modules.

**Solution:** Build a single Java Android APK with role-based access to Administrator, Dispatcher, Worker, and Compliance Reviewer areas inside the same app.

### Question: Is there any backend, web panel, or remote database?
**Assumption:** No. The prompt explicitly forbids that despite legacy references.

**Solution:** All features must run locally on-device using Room/SQLite and Android-native storage only. No Spring Boot, Thymeleaf, MySQL, REST API, cloud sync, or web UI.

### Question: What does offline-first mean in this project?
**Assumption:** The app must be fully operational with no internet connection and no dependency on online services.

**Solution:** Treat offline as the default execution mode. All business rules, checkout, compliance, dispatch scoring, authentication, and evidence handling run entirely on-device.

### Question: Is multi-device live synchronization required?
**Assumption:** No, because the prompt forbids a server and does not define peer-to-peer sync.

**Solution:** Scope v1 as single-device source of truth. Any imports/exports are manual file-based transfers through the Android document picker, not live sync.

### Question: Can one user have multiple roles?
**Assumption:** No. The current implementation stores exactly one role per user and one current role in session.

**Solution:** Support role-based access with one local account holding exactly one role: `ADMIN`, `DISPATCHER`, `WORKER`, or `COMPLIANCE_REVIEWER`. The UI only exposes modules allowed by that single assigned role.

### Question: Should there be a debug/demo role switcher?
**Assumption:** Not implemented in the current app.

**Solution:** Do not assume any debug-only role switcher. Role changes happen through normal persisted user/session state only.

### Question: What is the authentication model?
**Assumption:** Authentication is purely local because there is no server.

**Solution:** Implement local username/password login with password policy enforcement, local lockout tracking, and role checks from the local database.

### Question: Where should credentials be stored?
**Assumption:** Credentials are sensitive and must be protected beyond plain Room storage.

**Solution:** Store salted password hashes and lockout metadata in the local encrypted Room database, and store active session fields in `EncryptedSharedPreferences` backed by Android Keystore-managed keys.

### Question: What is the minimum password policy?
**Assumption:** The prompt already defines minimums and they are mandatory.

**Solution:** Enforce minimum 12-character passwords, salted and hashed, with lockout after 5 failed attempts for 15 minutes.

### Question: What happens if a user forgets their password?
**Assumption:** There is no remote recovery flow.

**Solution:** No password-reset flow is implemented in the current repo. Recovery is out of scope for the current app behavior.

### Question: What is the scope of encrypted-at-rest storage?
**Assumption:** The current implementation protects sensitive data through an encrypted Room database, encrypted session storage, and encrypted evidence files.

**Solution:** Persist app data in the encrypted Room database, store session data in `EncryptedSharedPreferences`, and store attached report evidence as AES-GCM encrypted files in app-private storage.

### Question: Are Product, Cart, Order, ShippingTemplate, DiscountRule, Employer, ComplianceCase/AuditLog, Worker, Task, Zone, and ReputationEvent the canonical core entities?
**Assumption:** Yes.

**Solution:** Use these as the base Room entities and derive any support entities only where necessary, such as CartLine, OrderLine, UserAccount, WarningEvent, or TaskAssignmentAttempt.

### Question: Should Order and Task be separate entities?
**Assumption:** Yes, because the prompt lists both independently.

**Solution:** Keep commerce orders separate from dispatch tasks. Do not assume any automatic order-to-task linkage in the current implementation.

### Question: Does every order automatically create a task?
**Assumption:** No. The current implementation does not create dispatch tasks from orders automatically.

**Solution:** Treat order creation and task creation as separate workflows. `CreateOrderFromCartUseCase` creates orders, and `CreateTaskUseCase` creates tasks independently.

### Question: Are employers the same as customers?
**Assumption:** No. Employers are a separate regulated/business entity subject to onboarding and compliance review.

**Solution:** Model Employer separately and allow Orders or Tasks to reference Employer where relevant, without assuming every customer is an employer.

### Question: Does the app support organizations using orgId?
**Assumption:** Yes, since composite indexes explicitly include orgId.

**Solution:** Treat the app as multi-organization capable at the data model level, even if v1 may only operate one org per device.

### Question: Should all major lists use RecyclerView and DiffUtil?
**Assumption:** Yes, this is mandatory.

**Solution:** Use RecyclerView plus DiffUtil for catalog, orders, tasks, workers, employers, cases, and logs.

### Question: What is the performance target for large datasets?
**Assumption:** The prompt’s 60fps target with 10,000 rows is an acceptance requirement, not a suggestion.

**Solution:** Design pagination, minimal row binding, stable IDs, and background diffing so list interactions remain smooth at 10,000-row scale.

### Question: How should media be handled in the catalog?
**Assumption:** Media is local and can be large enough to cause memory pressure.

**Solution:** Downsample images before display and use an LRU cache capped so incremental image memory stays under 20MB.

### Question: Is all database and media IO prohibited on the main thread?
**Assumption:** Yes.

**Solution:** Run all Room operations, hashing, evidence reads/writes, image decoding, import/export, and file validation through ViewModel/UseCase layers with background executors.

### Question: What layout system should be used?
**Assumption:** XML layouts with ConstraintLayout are required.

**Solution:** Use ConstraintLayout-based screen layouts adapted for phones and tablets. Do not pivot to a web-like or server-rendered UI.

### Question: Should the project use Java only?
**Assumption:** Yes, because the prompt says standalone Java Android APK.

**Solution:** Implement the app in Java for Android, with Room, RecyclerView, ViewModel, and XML layouts.

### Question: Is Jetpack Compose allowed?
**Assumption:** No, or at least not the intended path, because the prompt explicitly frames a classic Android stack.

**Solution:** Use traditional Android Views/XML rather than Compose to stay aligned with the prompt.

### Question: How should catalog search be indexed?
**Assumption:** The prompt’s composite index is mandatory and should support fast search.

**Solution:** Add composite indexes like `(brand, series, model)` and optimize query patterns around them for catalog lookup.

### Question: How should order and task querying be indexed?
**Assumption:** The prompt explicitly requires `(orgId, status, updatedAt)`.

**Solution:** Add those composite indexes to both orders and tasks and design list queries around status and recency.

### Question: How should cart merge work?
**Assumption:** Carts merge automatically when they belong to the same customer and store.

**Solution:** On cart load or cart resume, if another active cart exists for the same `customerId` and `storeId`, merge them automatically into one working cart.

### Question: What defines “same customer + store”?
**Assumption:** Exact local IDs, not fuzzy matching.

**Solution:** Use exact `customerId` and `storeId` equality as the merge key to avoid accidental merges.

### Question: How should line items merge inside merged carts?
**Assumption:** Same product should consolidate unless pricing conflicts.

**Solution:** Merge identical product lines by `productId` and compatible pricing context; sum quantities when prices match.

### Question: What happens when merged carts have conflicting prices?
**Assumption:** The prompt explicitly says it should trigger review.

**Solution:** Preserve the merged cart, flag conflicting line items, and show a review banner requiring acknowledgement before checkout.

### Question: Which price should win during cart merge conflicts?
**Assumption:** The system should not silently pick one and hide the conflict.

**Solution:** Retain the active line values in the merged cart but mark them as needing review; require recalculation or user confirmation before finalization.

### Question: Should tax, discounts, and shipping be revalidated after cart merge?
**Assumption:** Yes, because totals may have changed.

**Solution:** Mark the cart totals stale after merge and force a recalculation pass before checkout finalization.

### Question: What shipping templates are required?
**Assumption:** Exactly the three specified templates are baseline system templates.

**Solution:** Ship with Standard 3–5 business days, Expedited 1–2 business days, and Local Pickup as default `ShippingTemplate` records.

### Question: Are shipping templates editable by Administrators?
**Assumption:** Yes, because administrators configure commerce rules.

**Solution:** Seed the required templates but let Administrators manage template metadata while preserving the required baseline options.

### Question: How should discounts stack?
**Assumption:** Discounts can stack, but strict validation applies.

**Solution:** Allow up to 3 discounts per order, enforce combined percent-off discounts not exceeding 40%, and never apply discounts to sales tax.

### Question: Can fixed-amount and percentage discounts both be used?
**Assumption:** Yes, unless prohibited, because the prompt only caps count and percent total.

**Solution:** Support both, with combined percentage discounts capped at 40% and overall maximum of 3 total discounts.

### Question: In what order should discounts apply?
**Assumption:** The prompt does not specify, so the safest route is deterministic order on subtotal before tax.

**Solution:** Apply discounts against eligible merchandise subtotal only, before tax, in a stable defined sequence such as percent discounts first then fixed discounts.

### Question: Can discounts reduce tax directly?
**Assumption:** No, the prompt explicitly forbids it.

**Solution:** Compute tax on the original pre-discount merchandise subtotal and do not treat tax itself as discountable.

### Question: Can discounts apply to shipping?
**Assumption:** No, unless a future explicit shipping-discount rule is introduced.

**Solution:** In v1, discounts apply only to merchandise subtotal and not to shipping or tax.

### Question: What is required for regulated items?
**Assumption:** Regulated items require notes at checkout.

**Solution:** Add a regulated-item flag to Product and block checkout until required order notes are present when the cart contains any regulated item.

### Question: Should regulated notes be per-line or order-level?
**Assumption:** Order-level is the minimum required because the prompt says required order notes.

**Solution:** Require an order-level note for any regulated-item order, with optional per-line notes as an extension if needed later.

### Question: Should regulated notes also pass compliance scanning?
**Assumption:** Yes, because all job/order notes pass the sensitive-word ruleset.

**Solution:** Run required regulated notes through the same local content screening before finalization.

### Question: What is the amount consistency rule at checkout?
**Assumption:** It is a hard validation before order completion.

**Solution:** Validate that item subtotal plus discounts plus tax plus shipping equals displayed total within `$0.01`, otherwise block finalization.

### Question: How should money be rounded?
**Assumption:** The current implementation uses integer cents for stored money values and `Math.round` when percentage calculations are needed.

**Solution:** Use integer cents for monetary values and apply `Math.round` consistently for percentage-based discount and tax calculations.

### Question: What happens if line items are edited after totals are computed?
**Assumption:** Calculated totals become stale and potentially tampered with.

**Solution:** Lock calculated fields, mark totals stale, show a Recalculate warning banner, and block checkout finalization until totals are recomputed.

### Question: Should recalculation happen automatically or manually?
**Assumption:** The prompt explicitly calls for a visible “Recalculate” warning.

**Solution:** Use manual recalculation with a clear warning state so users cannot overlook post-total edits.

### Question: Which fields are considered calculated and must be locked?
**Assumption:** Subtotal, discount amount, tax amount, shipping total, grand total, and invoice summary fields.

**Solution:** Make those fields read-only and only changeable through system recomputation.

### Question: What invoice details are required?
**Assumption:** At minimum, invoice must reflect checkout financial breakdown and order metadata.

**Solution:** Include line items, unit prices, quantities, discounts, shipping template, shipping charge, tax, total, notes, order number, timestamps, and customer/store identifiers.

### Question: Should invoice numbering be local-only?
**Assumption:** Yes, because there is no server allocator.

**Solution:** Generate unique local invoice/order numbers on-device using deterministic org-aware sequencing or timestamp-based identifiers.

### Question: How should dispatch matching work?
**Assumption:** It is a weighted local scoring system configurable by administrators/dispatchers.

**Solution:** Score candidate workers using configurable weights for time window adherence, current workload, worker reputation, and dispatcher-entered zone score.

### Question: How should “distance” be represented?
**Assumption:** No GPS or maps are allowed.

**Solution:** Represent distance entirely as a zone score entered or maintained locally, such as Zone A=1 through Zone E=5, with lower scores treated as closer/better.

### Question: Who defines zones and zone scores?
**Assumption:** Administrator defines the zone system, Dispatcher uses it operationally.

**Solution:** Provide Zone management under admin settings and allow dispatch tasks/orders to reference a zone.

### Question: What are the dispatch modes?
**Assumption:** Exactly two modes exist: grab-order and assigned mode.

**Solution:** Implement both modes. In assigned mode, dispatcher explicitly assigns a worker. In grab-order mode, eligible workers can accept from a visible pool on the device.

### Question: How should grab-order mode behave without a backend?
**Assumption:** Since there is no live multi-device sync, grab-order is local to the device’s current dataset.

**Solution:** Treat grab-order as a local queue visible within the app instance. Real-time multi-device claim coordination is out of scope for v1.

### Question: How should current workload be measured?
**Assumption:** It should reflect how busy a worker is right now.

**Solution:** Compute workload from active open/in-progress task count, possibly weighted by priority if needed later. For v1, active task count is sufficient and aligned with the prompt.

### Question: How should worker reputation be measured?
**Assumption:** Reputation is derived from local `ReputationEvent` records.

**Solution:** Maintain reputation as an aggregate score from `ReputationEvent`s such as successful completion, lateness, cancellation, complaint, and compliance flags.

### Question: Is the scoring formula fixed or configurable?
**Assumption:** Weights are configurable, but the score model itself should remain stable.

**Solution:** Use a normalized weighted sum formula where each factor is normalized and multiplied by a configurable weight.

### Question: What is the anti-duplication control for task acceptance?
**Assumption:** Duplicate acceptance must be prevented transactionally.

**Solution:** Use a local 3-second mutex around acceptance attempts and perform acceptance in a Room transaction that verifies task status before setting `acceptedBy` and status.

### Question: Is the unique constraint on `(taskId, acceptedBy)` enough?
**Assumption:** No, by itself it does not fully prevent multiple workers accepting one task.

**Solution:** In addition to the prompt’s constraint, enforce only one active acceptance per task by making task acceptance an atomic status update on the `Task` row or by using a unique active acceptance keyed by `taskId`.

### Question: Should task acceptance and checkout finalization be transactional?
**Assumption:** Yes, the prompt explicitly requires it.

**Solution:** Wrap checkout finalization and task acceptance in database transactions so partial writes cannot occur.

### Question: How should employer onboarding work?
**Assumption:** It is a local verification workflow, not external legal verification.

**Solution:** Collect required fields and validate them locally for presence and format only.

### Question: What fields are required for employers?
**Assumption:** At minimum, legal name, EIN, and US address are mandatory because the prompt names them.

**Solution:** Require legal name, EIN in proper format, and structured US address fields before employer onboarding can be marked complete.

### Question: Is EIN verification external?
**Assumption:** No, since the app is offline and local-only.

**Solution:** Perform EIN format validation only, not government database verification.

### Question: How should US address validation work?
**Assumption:** Only structural format checking is possible offline.

**Solution:** Validate address line presence plus city, two-letter state code, and ZIP/ZIP+4 format locally.

### Question: What content must pass the sensitive-word ruleset?
**Assumption:** At minimum, all job notes and order notes.

**Solution:** Screen all order notes, job/task notes, employer-entered free text, and reviewer-visible reported content through the local ruleset.

### Question: Should the sensitive-word system be local and configurable?
**Assumption:** Yes, because compliance is handled locally.

**Solution:** Store a local ruleset managed by administrators/compliance roles, with severity metadata including zero-tolerance terms.

### Question: What does zero-tolerance mean in enforcement?
**Assumption:** Certain terms or violations bypass the normal warning flow.

**Solution:** If content hits zero-tolerance terms, reviewers may proceed directly to takedown/suspension without requiring prior warnings.

### Question: What is the anti-harassment rule?
**Assumption:** Two warnings are required before suspension unless zero-tolerance content is present.

**Solution:** Track warning count per offending account and block ordinary suspension until two warnings exist, except for zero-tolerance hits.

### Question: Should warnings be auditable?
**Assumption:** Yes, because all enforcement should be reviewable.

**Solution:** Persist warnings and enforcement actions in `AuditLog` and `ComplianceCase` records with timestamp, actor, reason, evidence references, and outcome.

### Question: What moderation outcomes are supported?
**Assumption:** Exactly the outcomes listed in the prompt.

**Solution:** Support takedown, account suspension using 7/30/365-day presets, and traffic throttling that limits visibility to internal users only.

### Question: What does traffic throttling mean in practice?
**Assumption:** It is not network throttling but content visibility restriction.

**Solution:** Mark the relevant employer/content/account as internal-only so it is hidden from normal operational views and visible only to internal roles.

### Question: Can users file reports with evidence?
**Assumption:** Yes, this is mandatory.

**Solution:** Allow report submission inside the app with attachments selected through scoped-storage-compatible flows and retained locally.

### Question: How should evidence be stored?
**Assumption:** Evidence is sensitive and must be encrypted at rest.

**Solution:** Store evidence in app-private encrypted local files with metadata in Room, and track SHA-256 fingerprints for integrity.

### Question: Why is SHA-256 needed?
**Assumption:** For local file integrity and import/export validation.

**Solution:** Compute SHA-256 fingerprints for imported/exported files and sensitive evidence to detect tampering or mismatch.

### Question: What import/export mechanism is required?
**Assumption:** Manual local file-based import/export only, respecting Android 10+ scoped storage.

**Solution:** Use the system document picker for selecting destinations and sources, validate file structure locally, and verify hashes before import.

### Question: What file formats should imports/exports use?
**Assumption:** The current implementation uses a JSON document format with an embedded SHA-256 fingerprint.

**Solution:** Use a versioned JSON export format (`roadrunner_v1`) with an embedded SHA-256 fingerprint for integrity verification.

### Question: Should imports merge or replace data?
**Assumption:** Default behavior should avoid destructive changes unless explicit.

**Solution:** Use validated additive import behavior in v1. Valid records are inserted through repositories/use cases, while invalid or conflicting records are skipped; no destructive replace flow is implemented.

### Question: What should be audited?
**Assumption:** At minimum, compliance actions, onboarding changes, checkout finalization, task acceptance, and sensitive configuration changes.

**Solution:** Write immutable audit logs for all critical actions affecting money, workforce assignment, account enforcement, and sensitive identity records.

### Question: Are audit logs editable?
**Assumption:** No, they must be append-only in practice.

**Solution:** Implement audit logs as append-only records that cannot be edited through normal UI flows.

### Question: Should catalog-to-cart flow be optimized for speed?
**Assumption:** Yes, this is a core UX requirement.

**Solution:** Design the catalog screens for fast add-to-cart actions, minimal navigation depth, and responsive list/grid updates using RecyclerView and cached media.

### Question: Should the app support tablet layouts as well as phone layouts?
**Assumption:** Yes, because the prompt explicitly mentions both.

**Solution:** Use ConstraintLayout-based responsive XML screens with alternate resource layouts where needed for larger screens.

### Question: Should Android 10+ scoped storage be strictly respected?
**Assumption:** Yes.

**Solution:** Do not use legacy broad file access. Use app-private storage plus system document picker for user-selected imports/exports.

### Question: Should all critical rules be enforced in UseCases rather than only in the UI?
**Assumption:** Yes, UI-only enforcement would be too fragile.

**Solution:** Put validations, recalculation, discount rules, compliance scanning, and transactional guards into business-layer UseCases so the UI cannot bypass them.

### Question: Should sales tax be editable by users?
**Assumption:** No, because totals are system-calculated and anti-tampering applies.

**Solution:** Compute tax from configured rules and lock it as a calculated field.

### Question: Should shipping charge be editable by users?
**Assumption:** No, unless through shipping template selection or authorized override flow.

**Solution:** Derive shipping from selected shipping template and lock the calculated shipping total after computation.

### Question: Should administrators be able to change workforce matching weights?
**Assumption:** Yes, because they configure workforce rules.

**Solution:** Put dispatch weighting rules in admin-configurable settings and let dispatch use the current active profile.

### Question: Should compliance reviewers be able to see report evidence inside the app?
**Assumption:** Yes, reviewing evidence is core to the role.

**Solution:** Add evidence preview/access in the compliance module with local encrypted file access mediated by the app.

### Question: Should worker completion updates be transactional as well?
**Assumption:** While not explicitly named, they are critical task-state transitions.

**Solution:** Process worker acceptance/completion/status changes inside transactions whenever task ownership or completion state changes.

### Question: Should there be local mutexing only for acceptance, or also checkout?
**Assumption:** Acceptance explicitly needs mutexing due to race risk; checkout is already transaction-protected.

**Solution:** Use the required 3-second local mutex for task acceptance and rely on transactional consistency plus stale-total checks for checkout.

### Question: Should the app depend on GPS, maps, or geocoding?
**Assumption:** No.

**Solution:** Exclude all GPS/map service dependencies and use zone scores as the only distance proxy.

### Question: Should Local Pickup orders still go through checkout validation?
**Assumption:** Yes, checkout consistency rules apply regardless of fulfillment type.

**Solution:** Run the same amount checks, discount rules, and anti-tampering logic for all shipping templates, including Local Pickup.

### Question: Should there be internal-only visibility for throttled accounts/content?
**Assumption:** Yes, exactly as described.

**Solution:** Add visibility flags and UI filters so throttled records are hidden from standard operational views and available only to internal roles.

### Question: Should line-item edits after total calculation invalidate invoices too?
**Assumption:** Yes, because invoice details depend on totals.

**Solution:** Mark invoice summary stale whenever financial inputs change and regenerate it only after recalculation.

### Question: Should the app explicitly reject legacy server assumptions in code and documentation?
**Assumption:** Yes, to prevent scope drift.

**Solution:** State non-goals clearly in project documentation: no backend, no web UI, no remote DB, no cloud sync, and no map service dependency.

### Question: What is the safest overall implementation stance when the prompt is silent?
**Assumption:** Prefer the narrowest solution that preserves the original constraints and avoids adding distributed complexity.

**Solution:** Default to local-only, deterministic, auditable, transactional behavior, with extension points for future sync or expanded validation but no server-era assumptions in v1.

---

## Summary

This document resolves the prompt toward a conservative, implementation-ready interpretation:

- one standalone Java Android APK
- no backend, no web UI, no remote DB
- local-only authentication and role-based access
- Room-backed offline-first data model
- manual import/export only
- deterministic pricing and checkout validation
- transaction-protected checkout and task acceptance
- local compliance moderation and evidence handling
- auditable enforcement and critical operations
- classic Android UI with RecyclerView, DiffUtil, XML, and ConstraintLayout

The guiding principle is to preserve the original prompt exactly where specified and make the smallest possible assumptions everywhere else.
