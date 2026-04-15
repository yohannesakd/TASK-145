package com.roadrunner.dispatch.core.domain.usecase;

import com.roadrunner.dispatch.core.domain.model.AuditLogEntry;
import com.roadrunner.dispatch.core.domain.model.ContentScanResult;
import com.roadrunner.dispatch.core.domain.model.DiscountRule;
import com.roadrunner.dispatch.core.domain.model.Order;
import com.roadrunner.dispatch.core.domain.model.OrderItem;
import com.roadrunner.dispatch.core.domain.model.OrderTotals;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.model.ShippingTemplate;
import com.roadrunner.dispatch.core.domain.repository.AuditLogRepository;
import com.roadrunner.dispatch.core.domain.repository.OrderRepository;
import com.roadrunner.dispatch.core.util.AppLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FinalizeCheckoutUseCase {
    private final OrderRepository orderRepository;
    private final AuditLogRepository auditLogRepository;
    private final ValidateDiscountsUseCase validateDiscountsUseCase;
    private final ComputeOrderTotalsUseCase computeOrderTotalsUseCase;
    private final ScanContentUseCase scanContentUseCase;

    public FinalizeCheckoutUseCase(OrderRepository orderRepository,
                                     AuditLogRepository auditLogRepository,
                                     ValidateDiscountsUseCase validateDiscountsUseCase,
                                     ComputeOrderTotalsUseCase computeOrderTotalsUseCase) {
        this(orderRepository, auditLogRepository, validateDiscountsUseCase,
             computeOrderTotalsUseCase, null);
    }

    public FinalizeCheckoutUseCase(OrderRepository orderRepository,
                                     AuditLogRepository auditLogRepository,
                                     ValidateDiscountsUseCase validateDiscountsUseCase,
                                     ComputeOrderTotalsUseCase computeOrderTotalsUseCase,
                                     ScanContentUseCase scanContentUseCase) {
        this.orderRepository = orderRepository;
        this.auditLogRepository = auditLogRepository;
        this.validateDiscountsUseCase = validateDiscountsUseCase;
        this.computeOrderTotalsUseCase = computeOrderTotalsUseCase;
        this.scanContentUseCase = scanContentUseCase;
    }

    /**
     * Finalize an order.
     *
     * <p>Delegates to {@link #execute(String, String, String, String, boolean)} with
     * {@code contentApproved=false}. FLAGGED order notes will be rejected; callers that have
     * already obtained explicit user confirmation should use the overload that accepts
     * {@code contentApproved=true}.
     *
     * @param orderId   ID of the order to finalize
     * @param actorId   ID of the actor performing finalization (for audit)
     * @param actorRole Role of the actor; must be "ADMIN", "DISPATCHER", or "WORKER"
     * @param orgId     Organisation scope for the operation
     */
    public Result<Order> execute(String orderId, String actorId, String actorRole, String orgId) {
        return execute(orderId, actorId, actorRole, orgId, false);
    }

    /**
     * Finalize an order, with explicit content-approval control.
     *
     * @param orderId         ID of the order to finalize
     * @param actorId         ID of the actor performing finalization (for audit)
     * @param actorRole       Role of the actor; must be "ADMIN", "DISPATCHER", or "WORKER"
     * @param orgId           Organisation scope for the operation
     * @param contentApproved When {@code true}, FLAGGED order notes are allowed through (the user
     *                        has already confirmed the warning). When {@code false}, FLAGGED notes
     *                        return a distinguishable failure with the "CONTENT_FLAGGED:" prefix.
     *                        ZERO_TOLERANCE always blocks regardless of this flag.
     */
    public Result<Order> execute(String orderId, String actorId, String actorRole, String orgId,
                                  boolean contentApproved) {
        AppLogger.info("Checkout", "finalize orderId=" + AppLogger.mask(orderId) + " role=" + actorRole + " org=" + AppLogger.mask(orgId));
        if (!"ADMIN".equals(actorRole) && !"DISPATCHER".equals(actorRole) && !"WORKER".equals(actorRole)) {
            return Result.failure("Unauthorized: role " + actorRole + " cannot finalize checkouts");
        }

        Order order = orderRepository.getByIdScoped(orderId, orgId);
        if (order == null) {
            return Result.failure("Order not found");
        }

        // Check order is in DRAFT status
        if (!"DRAFT".equals(order.status)) {
            return Result.failure("Order is not in DRAFT status. Current: " + order.status);
        }

        // Anti-tampering: totals must not be stale
        if (order.totalsStale) {
            return Result.failure("Totals are stale. Please recalculate before finalizing.");
        }

        // Totals must have been computed
        if (order.totalsComputedAt == 0) {
            return Result.failure("Totals have not been computed. Please calculate totals first.");
        }

        List<OrderItem> items = orderRepository.getOrderItems(orderId);
        if (items.isEmpty()) {
            return Result.failure("Order has no items");
        }

        // Regulated items require order notes
        boolean hasRegulated = false;
        List<String> regulatedNames = new ArrayList<>();
        for (OrderItem item : items) {
            if (item.regulated) {
                hasRegulated = true;
                regulatedNames.add(item.productName);
            }
        }
        if (hasRegulated && (order.orderNotes == null || order.orderNotes.trim().isEmpty())) {
            return Result.failure("Order notes required for regulated items: " + String.join(", ", regulatedNames));
        }

        // Shipping template must be selected before finalizing
        if (order.shippingTemplateId == null || order.shippingTemplateId.isEmpty()) {
            return Result.failure("A shipping template must be selected before finalizing.");
        }

        // Scan order notes for prohibited content
        if (scanContentUseCase != null && order.orderNotes != null && !order.orderNotes.trim().isEmpty()) {
            ContentScanResult scanResult = scanContentUseCase.execute(order.orderNotes);
            if ("ZERO_TOLERANCE".equals(scanResult.status)) {
                return Result.failure("Order notes contain prohibited terms");
            }
            if (!contentApproved && "FLAGGED".equals(scanResult.status)) {
                return Result.failure("CONTENT_FLAGGED: Order notes contain flagged terms that require review");
            }
        }

        // Validate discounts (org-scoped to prevent cross-org discount injection)
        List<String> discountIds = orderRepository.getAppliedDiscountIds(orderId);
        if (!discountIds.isEmpty()) {
            List<DiscountRule> discountRules = orderRepository.getDiscountRulesByIdScoped(discountIds, orgId);
            Result<List<DiscountRule>> discountResult = validateDiscountsUseCase.execute(discountRules);
            if (!discountResult.isSuccess()) {
                return Result.failure(discountResult.getErrors());
            }
        }

        // Verify amount consistency (±$0.01)
        ShippingTemplate shipping = order.shippingTemplateId != null ?
            orderRepository.getShippingTemplateScoped(order.shippingTemplateId, orgId) : null;
        List<DiscountRule> appliedDiscounts = discountIds.isEmpty() ?
            new ArrayList<>() : orderRepository.getDiscountRulesByIdScoped(discountIds, orgId);

        Result<OrderTotals> totalsResult = computeOrderTotalsUseCase.execute(items, appliedDiscounts, shipping);
        if (!totalsResult.isSuccess()) {
            return Result.failure(totalsResult.getErrors());
        }

        OrderTotals computedTotals = totalsResult.getData();
        long discrepancy = Math.abs(computedTotals.totalCents - order.totalCents);
        if (discrepancy > 1) {
            return Result.failure("Amount consistency check failed. Discrepancy: $" +
                String.format("%.2f", discrepancy / 100.0) + ". Please recalculate totals.");
        }

        // All checks passed — finalize
        Order finalized = new Order(
            order.id, order.orgId, order.cartId, order.customerId, order.storeId,
            "FINALIZED",
            order.subtotalCents, order.discountCents, order.taxCents,
            order.shippingCents, order.totalCents,
            order.shippingTemplateId, order.orderNotes,
            order.totalsComputedAt, false
        );
        AuditLogEntry auditEntry = new AuditLogEntry(
            UUID.randomUUID().toString(), order.orgId, actorId, "ORDER_FINALIZED",
            "ORDER", orderId,
            "{\"total\":" + order.totalCents + ",\"customerId\":\"" + order.customerId + "\"}",
            null, System.currentTimeMillis()
        );
        AppLogger.info("Checkout", "Order finalized orderId=" + AppLogger.mask(orderId) + " total=" + order.totalCents);
        // Use transactional finalize so order update and audit log are atomic
        orderRepository.finalizeOrder(finalized, auditEntry);

        return Result.success(finalized);
    }
}
