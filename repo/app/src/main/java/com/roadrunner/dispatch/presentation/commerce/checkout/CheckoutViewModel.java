package com.roadrunner.dispatch.presentation.commerce.checkout;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.roadrunner.dispatch.core.domain.model.DiscountRule;
import com.roadrunner.dispatch.core.domain.model.Order;
import com.roadrunner.dispatch.core.domain.model.OrderItem;
import com.roadrunner.dispatch.core.domain.model.OrderTotals;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.model.ShippingTemplate;
import com.roadrunner.dispatch.core.domain.repository.OrderRepository;
import com.roadrunner.dispatch.core.domain.usecase.ComputeOrderTotalsUseCase;
import com.roadrunner.dispatch.core.domain.usecase.CreateOrderFromCartUseCase;
import com.roadrunner.dispatch.core.domain.usecase.FinalizeCheckoutUseCase;
import com.roadrunner.dispatch.core.domain.usecase.ValidateDiscountsUseCase;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel for the checkout flow.
 *
 * <p>Orchestrates order creation from a cart, shipping-template selection,
 * discount application, totals computation, and final order submission.
 * Each operation executes on the background executor and posts results
 * to LiveData.
 */
public class CheckoutViewModel extends ViewModel {

    private final CreateOrderFromCartUseCase createOrderFromCartUseCase;
    private final FinalizeCheckoutUseCase finalizeCheckoutUseCase;
    private final ComputeOrderTotalsUseCase computeOrderTotalsUseCase;
    private final ValidateDiscountsUseCase validateDiscountsUseCase;
    private final OrderRepository orderRepository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /** The draft order currently being checked out. */
    private final MutableLiveData<Order> order = new MutableLiveData<>();

    /** Line items belonging to the current order. */
    private final MutableLiveData<List<OrderItem>> orderItems = new MutableLiveData<>();

    /** Computed totals (subtotal, discount, tax, shipping, grand total). */
    private final MutableLiveData<OrderTotals> totals = new MutableLiveData<>();

    /** Shipping templates available for selection. */
    private final MutableLiveData<List<ShippingTemplate>> shippingTemplates = new MutableLiveData<>();

    /** Discount rules the user may apply. */
    private final MutableLiveData<List<DiscountRule>> availableDiscounts = new MutableLiveData<>();

    /** True when order.totalsStale == true; prompts the UI to recalculate. */
    private final MutableLiveData<Boolean> staleWarning = new MutableLiveData<>(false);

    /** Error message from any failed operation; observed once then cleared. */
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public CheckoutViewModel(CreateOrderFromCartUseCase createOrderFromCartUseCase,
                              FinalizeCheckoutUseCase finalizeCheckoutUseCase,
                              ComputeOrderTotalsUseCase computeOrderTotalsUseCase,
                              ValidateDiscountsUseCase validateDiscountsUseCase,
                              OrderRepository orderRepository) {
        this.createOrderFromCartUseCase = createOrderFromCartUseCase;
        this.finalizeCheckoutUseCase = finalizeCheckoutUseCase;
        this.computeOrderTotalsUseCase = computeOrderTotalsUseCase;
        this.validateDiscountsUseCase = validateDiscountsUseCase;
        this.orderRepository = orderRepository;
    }

    // ── Exposed LiveData ──────────────────────────────────────────────────────

    public LiveData<Order> getOrder() { return order; }
    public LiveData<List<OrderItem>> getOrderItems() { return orderItems; }
    public LiveData<OrderTotals> getTotals() { return totals; }
    public LiveData<List<ShippingTemplate>> getShippingTemplates() { return shippingTemplates; }
    public LiveData<List<DiscountRule>> getAvailableDiscounts() { return availableDiscounts; }
    public LiveData<Boolean> getStaleWarning() { return staleWarning; }
    public LiveData<String> getError() { return error; }

    // ── Actions ───────────────────────────────────────────────────────────────

    /**
     * Create a DRAFT order from the given cart. Posts the resulting order ID
     * and loads all related data on success.
     */
    public void createOrderFromCart(String cartId, String userId, String orgId, String actorRole) {
        executor.execute(() -> {
            Result<String> result = createOrderFromCartUseCase.execute(cartId, userId, actorRole, orgId);
            if (result.isSuccess()) {
                loadOrder(result.getData(), orgId);
            } else {
                error.postValue(result.getFirstError());
            }
        });
    }

    /**
     * Load an existing order by ID (e.g., on screen resume).
     */
    public void loadOrder(String orderId, String orgId) {
        executor.execute(() -> {
            Order loaded = orderRepository.getByIdScoped(orderId, orgId);
            if (loaded == null) {
                error.postValue("Order not found");
                return;
            }
            order.postValue(loaded);
            staleWarning.postValue(loaded.totalsStale);
            orderItems.postValue(orderRepository.getOrderItems(orderId));
            shippingTemplates.postValue(orderRepository.getShippingTemplates(orgId));
            availableDiscounts.postValue(orderRepository.getActiveDiscountRules(orgId));
        });
    }

    /**
     * Attach a shipping template to the current order.
     */
    public void selectShipping(String orderId, String templateId, String orgId) {
        executor.execute(() -> {
            Order current = orderRepository.getByIdScoped(orderId, orgId);
            if (current == null) { error.postValue("Order not found"); return; }

            Order updated = new Order(
                    current.id, current.orgId, current.cartId, current.customerId, current.storeId,
                    current.status,
                    current.subtotalCents, current.discountCents, current.taxCents,
                    current.shippingCents, current.totalCents,
                    templateId, current.orderNotes,
                    current.totalsComputedAt, true /* stale after shipping change */);
            orderRepository.updateOrder(updated);
            order.postValue(updated);
            staleWarning.postValue(true);
        });
    }

    /**
     * Apply a discount rule to the current order. The actual amount is computed
     * from the current items; posts stale warning so the UI prompts recalculation.
     */
    public void applyDiscount(String orderId, String discountRuleId, String orgId) {
        executor.execute(() -> {
            Order current = orderRepository.getByIdScoped(orderId, orgId);
            if (current == null) { error.postValue("Order not found"); return; }

            // Pre-validate discount stack rules before applying
            List<String> currentIds = orderRepository.getAppliedDiscountIds(orderId);
            if (currentIds.contains(discountRuleId)) {
                error.postValue("This discount is already applied");
                return;
            }
            List<String> prospectiveIds = new java.util.ArrayList<>(currentIds);
            prospectiveIds.add(discountRuleId);
            List<DiscountRule> prospectiveRules = orderRepository.getDiscountRulesByIdScoped(prospectiveIds, orgId);
            Result<List<DiscountRule>> validation = validateDiscountsUseCase.execute(prospectiveRules);
            if (!validation.isSuccess()) {
                error.postValue(validation.getFirstError());
                return;
            }

            // Apply with 0-cent placeholder; computeTotals will recalculate
            orderRepository.applyDiscount(orderId, discountRuleId, 0L);

            // Persist stale flag so it survives fragment recreation
            Order updated = new Order(
                    current.id, current.orgId, current.cartId, current.customerId, current.storeId,
                    current.status,
                    current.subtotalCents, current.discountCents, current.taxCents,
                    current.shippingCents, current.totalCents,
                    current.shippingTemplateId, current.orderNotes,
                    current.totalsComputedAt, true /* totalsStale */);
            orderRepository.updateOrder(updated);
            staleWarning.postValue(true);
        });
    }

    /**
     * Remove a specific discount from the order and re-apply the remaining ones.
     * Posts staleWarning so the user knows to recalculate totals.
     */
    public void removeDiscount(String orderId, String discountRuleId, String orgId) {
        executor.execute(() -> {
            List<String> currentIds = orderRepository.getAppliedDiscountIds(orderId);
            orderRepository.removeDiscounts(orderId);
            for (String id : currentIds) {
                if (!id.equals(discountRuleId)) {
                    orderRepository.applyDiscount(orderId, id, 0L);
                }
            }

            // Persist stale flag so it survives fragment recreation
            Order current = orderRepository.getByIdScoped(orderId, orgId);
            if (current != null) {
                Order updated = new Order(
                        current.id, current.orgId, current.cartId, current.customerId, current.storeId,
                        current.status,
                        current.subtotalCents, current.discountCents, current.taxCents,
                        current.shippingCents, current.totalCents,
                        current.shippingTemplateId, current.orderNotes,
                        current.totalsComputedAt, true /* totalsStale */);
                orderRepository.updateOrder(updated);
            }
            staleWarning.postValue(true);
        });
    }

    /**
     * Recompute order totals based on current items, discounts, and shipping.
     */
    public void computeTotals(String orderId, String orgId) {
        executor.execute(() -> {
            Order current = orderRepository.getByIdScoped(orderId, orgId);
            if (current == null) { error.postValue("Order not found"); return; }

            if (current.shippingTemplateId == null) {
                error.postValue("Please select a shipping option before calculating totals.");
                return;
            }

            List<OrderItem> items = orderRepository.getOrderItems(orderId);
            List<String> discountIds = orderRepository.getAppliedDiscountIds(orderId);
            List<DiscountRule> discountRules = discountIds.isEmpty()
                    ? java.util.Collections.emptyList()
                    : orderRepository.getDiscountRulesByIdScoped(discountIds, orgId);

            // Validate discount stack rules before computing totals
            if (!discountRules.isEmpty()) {
                Result<List<DiscountRule>> discountValidation = validateDiscountsUseCase.execute(discountRules);
                if (!discountValidation.isSuccess()) {
                    error.postValue(discountValidation.getFirstError());
                    return;
                }
            }

            ShippingTemplate shipping = current.shippingTemplateId != null
                    ? orderRepository.getShippingTemplateScoped(current.shippingTemplateId, orgId)
                    : null;

            Result<OrderTotals> result =
                    computeOrderTotalsUseCase.execute(items, discountRules, shipping);
            if (!result.isSuccess()) {
                error.postValue(result.getFirstError());
                return;
            }

            OrderTotals computed = result.getData();
            totals.postValue(computed);

            // Persist updated totals on the order entity
            long now = System.currentTimeMillis();
            Order persisted = new Order(
                    current.id, current.orgId, current.cartId, current.customerId, current.storeId,
                    current.status,
                    computed.subtotalCents, computed.discountCents, computed.taxCents,
                    computed.shippingCents, computed.totalCents,
                    current.shippingTemplateId, current.orderNotes,
                    now, false /* totals no longer stale */);
            orderRepository.updateOrder(persisted);
            order.postValue(persisted);
            staleWarning.postValue(false);
        });
    }

    /**
     * Save order notes for the given order.
     */
    public void saveNotes(String orderId, String notes, String orgId) {
        executor.execute(() -> {
            Order current = orderRepository.getByIdScoped(orderId, orgId);
            if (current == null) { error.postValue("Order not found"); return; }
            Order updated = new Order(
                    current.id, current.orgId, current.cartId, current.customerId, current.storeId,
                    current.status,
                    current.subtotalCents, current.discountCents, current.taxCents,
                    current.shippingCents, current.totalCents,
                    current.shippingTemplateId, notes,
                    current.totalsComputedAt, current.totalsStale);
            orderRepository.updateOrder(updated);
            order.postValue(updated);
        });
    }

    /**
     * Finalize the order (runs all pre-flight checks and transitions to FINALIZED).
     *
     * @param actorRole Role of the actor; must be "ADMIN", "DISPATCHER", or "WORKER"
     */
    public void finalize(String orderId, String actorId, String actorRole, String orgId) {
        finalize(orderId, actorId, actorRole, orgId, false);
    }

    /**
     * Finalize with optional content approval to bypass FLAGGED content warnings.
     *
     * @param contentApproved when true, FLAGGED content in order notes is allowed through
     */
    public void finalize(String orderId, String actorId, String actorRole, String orgId,
                         boolean contentApproved) {
        executor.execute(() -> {
            Result<Order> result = finalizeCheckoutUseCase.execute(
                    orderId, actorId, actorRole, orgId, contentApproved);
            if (result.isSuccess()) {
                order.postValue(result.getData());
            } else {
                error.postValue(result.getFirstError());
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
