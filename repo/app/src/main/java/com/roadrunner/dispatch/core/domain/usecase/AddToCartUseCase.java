package com.roadrunner.dispatch.core.domain.usecase;

import com.roadrunner.dispatch.core.domain.model.Cart;
import com.roadrunner.dispatch.core.domain.model.CartItem;
import com.roadrunner.dispatch.core.domain.model.Product;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.repository.CartRepository;
import com.roadrunner.dispatch.core.domain.repository.ProductRepository;
import java.util.UUID;

public class AddToCartUseCase {
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;

    public AddToCartUseCase(CartRepository cartRepository, ProductRepository productRepository) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
    }

    public Result<CartItem> execute(String customerId, String storeId, String productId,
                                     int quantity, String orgId, String userId) {
        if (quantity <= 0) {
            return Result.failure("Quantity must be positive");
        }

        // Get product (org-scoped to prevent cross-org access)
        Product product = productRepository.getByIdScoped(productId, orgId);
        if (product == null) {
            return Result.failure("Product not found");
        }
        if (!"ACTIVE".equals(product.status)) {
            return Result.failure("Product is not available");
        }

        // Find or create cart for this customer+store scoped to the org (prevents cross-org cart merge)
        Cart cart = cartRepository.findActiveCart(customerId, storeId, orgId);
        String cartId;
        if (cart == null) {
            cartId = cartRepository.createCart(orgId, customerId, storeId, userId);
        } else {
            cartId = cart.id;
        }

        // Check if product already in cart (merge logic)
        CartItem existingItem = cartRepository.findCartItem(cartId, productId);
        if (existingItem != null) {
            // Product already in cart — merge
            boolean priceConflict = existingItem.unitPriceSnapshotCents != product.unitPriceCents;

            CartItem updated = new CartItem(
                existingItem.id,
                cartId,
                productId,
                existingItem.quantity + quantity,
                priceConflict ? existingItem.unitPriceSnapshotCents : product.unitPriceCents,
                priceConflict || existingItem.conflictFlag,
                priceConflict ? product.unitPriceCents : existingItem.originalPriceCents
            );
            cartRepository.updateCartItem(updated);
            return Result.success(updated);
        } else {
            // New product in cart
            CartItem newItem = new CartItem(
                UUID.randomUUID().toString(),
                cartId,
                productId,
                quantity,
                product.unitPriceCents,
                false,
                0
            );
            cartRepository.insertCartItem(newItem);
            return Result.success(newItem);
        }
    }
}
