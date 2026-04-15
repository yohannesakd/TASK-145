package com.roadrunner.dispatch.core.domain.usecase;

import com.roadrunner.dispatch.core.domain.model.Cart;
import com.roadrunner.dispatch.core.domain.model.CartItem;
import com.roadrunner.dispatch.core.domain.model.Order;
import com.roadrunner.dispatch.core.domain.model.OrderItem;
import com.roadrunner.dispatch.core.domain.model.Product;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.repository.CartRepository;
import com.roadrunner.dispatch.core.domain.repository.OrderRepository;
import com.roadrunner.dispatch.core.domain.repository.ProductRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CreateOrderFromCartUseCase {
    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public CreateOrderFromCartUseCase(CartRepository cartRepository,
                                       OrderRepository orderRepository,
                                       ProductRepository productRepository) {
        this.cartRepository = cartRepository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    /**
     * Create a DRAFT order from a cart's contents, atomically.
     *
     * @param actorRole Role of the actor; must be "ADMIN", "DISPATCHER", or "WORKER"
     */
    public Result<String> execute(String cartId, String userId, String actorRole) {
        return execute(cartId, userId, actorRole, null);
    }

    public Result<String> execute(String cartId, String userId, String actorRole, String orgId) {
        if (!"ADMIN".equals(actorRole) && !"DISPATCHER".equals(actorRole) && !"WORKER".equals(actorRole)) {
            return Result.failure("Unauthorized: role " + actorRole + " cannot create orders");
        }

        if (orgId == null || orgId.isEmpty()) {
            return Result.failure("Organisation ID is required");
        }

        Cart cart = cartRepository.getByIdScoped(cartId, orgId);
        if (cart == null) {
            return Result.failure("Cart not found");
        }

        List<CartItem> cartItems = cartRepository.getCartItems(cartId);
        if (cartItems.isEmpty()) {
            return Result.failure("Cart is empty");
        }

        int conflicts = cartRepository.getConflictCount(cartId);
        if (conflicts > 0) {
            return Result.failure("Cart has " + conflicts + " unresolved price conflict(s). Please resolve before checkout.");
        }

        Order existingDraft = orderRepository.findDraftByCartId(cartId, orgId);
        if (existingDraft != null) {
            // Re-sync: atomically replace stale order items with current cart contents
            List<OrderItem> refreshedItems = new ArrayList<>();
            for (CartItem ci : cartItems) {
                Product product = productRepository.getByIdScoped(ci.productId, orgId);
                String productName = product != null ? product.name : "Unknown Product";
                double taxRate = product != null ? product.taxRate : 0.0;
                boolean regulated = product != null && product.regulated;
                long lineTotalCents = ci.unitPriceSnapshotCents * ci.quantity;

                refreshedItems.add(new OrderItem(
                    UUID.randomUUID().toString(),
                    existingDraft.id,
                    ci.productId,
                    productName,
                    ci.quantity,
                    ci.unitPriceSnapshotCents,
                    lineTotalCents,
                    taxRate,
                    regulated
                ));
            }
            orderRepository.replaceOrderItems(existingDraft.id, refreshedItems);

            // Mark totals stale since items may have changed
            Order staleOrder = new Order(
                existingDraft.id, existingDraft.orgId, existingDraft.cartId,
                existingDraft.customerId, existingDraft.storeId, existingDraft.status,
                existingDraft.subtotalCents, existingDraft.discountCents,
                existingDraft.taxCents, existingDraft.shippingCents, existingDraft.totalCents,
                existingDraft.shippingTemplateId, existingDraft.orderNotes,
                existingDraft.totalsComputedAt, true /* totalsStale */
            );
            orderRepository.updateOrder(staleOrder);

            return Result.success(existingDraft.id);
        }

        List<OrderItem> orderItems = new ArrayList<>();
        for (CartItem ci : cartItems) {
            Product product = productRepository.getByIdScoped(ci.productId, orgId);
            String productName = product != null ? product.name : "Unknown Product";
            double taxRate = product != null ? product.taxRate : 0.0;
            boolean regulated = product != null && product.regulated;

            long lineTotalCents = ci.unitPriceSnapshotCents * ci.quantity;

            OrderItem oi = new OrderItem(
                UUID.randomUUID().toString(),
                null, // orderId set by repository
                ci.productId,
                productName,
                ci.quantity,
                ci.unitPriceSnapshotCents,
                lineTotalCents,
                taxRate,
                regulated
            );
            orderItems.add(oi);
        }

        String orderId = orderRepository.createOrderFromCart(
            cart.orgId, cartId, cart.customerId, cart.storeId, userId, orderItems
        );

        return Result.success(orderId);
    }
}
