package com.roadrunner.dispatch.core.domain.usecase;

import com.roadrunner.dispatch.core.domain.model.CartItem;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.repository.CartRepository;

public class ResolveCartConflictUseCase {
    private final CartRepository cartRepository;

    public ResolveCartConflictUseCase(CartRepository cartRepository) {
        this.cartRepository = cartRepository;
    }

    /**
     * Resolve a price conflict on a cart item by choosing which price to keep.
     * @param cartId the cart containing the conflicting item
     * @param productId the product whose conflict is being resolved
     * @param chosenPriceCents the price the user selected
     */
    public Result<CartItem> execute(String cartId, String productId, long chosenPriceCents) {
        CartItem item = cartRepository.findCartItem(cartId, productId);
        if (item == null) {
            return Result.failure("Cart item not found");
        }
        if (!item.conflictFlag) {
            return Result.failure("No conflict to resolve");
        }

        if (chosenPriceCents != item.unitPriceSnapshotCents && chosenPriceCents != item.originalPriceCents) {
            return Result.failure("Chosen price must be either the original snapshot price or the current price");
        }

        CartItem resolved = new CartItem(
            item.id, item.cartId, item.productId, item.quantity,
            chosenPriceCents, false, 0
        );
        cartRepository.updateCartItem(resolved);
        return Result.success(resolved);
    }
}
