package com.roadrunner.dispatch.core.domain.repository;

import com.roadrunner.dispatch.core.domain.model.Cart;
import com.roadrunner.dispatch.core.domain.model.CartItem;
import java.util.List;

public interface CartRepository {
    Cart findActiveCart(String customerId, String storeId, String orgId);
    Cart findMostRecentByCreator(String createdBy, String orgId);
    Cart getByIdScoped(String id, String orgId);
    String createCart(String orgId, String customerId, String storeId, String createdBy);
    List<CartItem> getCartItems(String cartId);
    CartItem findCartItem(String cartId, String productId);
    void insertCartItem(CartItem item);
    void updateCartItem(CartItem item);
    void deleteCartItem(String itemId);
    int getConflictCount(String cartId);
    void deleteCart(String cartId);
}
