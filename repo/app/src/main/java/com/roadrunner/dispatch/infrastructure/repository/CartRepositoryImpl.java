package com.roadrunner.dispatch.infrastructure.repository;

import com.roadrunner.dispatch.core.domain.model.Cart;
import com.roadrunner.dispatch.core.domain.model.CartItem;
import com.roadrunner.dispatch.core.domain.repository.CartRepository;
import com.roadrunner.dispatch.infrastructure.db.dao.CartDao;
import com.roadrunner.dispatch.infrastructure.db.dao.CartItemDao;
import com.roadrunner.dispatch.infrastructure.db.entity.CartEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.CartItemEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CartRepositoryImpl implements CartRepository {
    private final CartDao cartDao;
    private final CartItemDao cartItemDao;

    public CartRepositoryImpl(CartDao cartDao, CartItemDao cartItemDao) {
        this.cartDao = cartDao;
        this.cartItemDao = cartItemDao;
    }

    @Override
    public Cart findActiveCart(String customerId, String storeId, String orgId) {
        CartEntity e = cartDao.findByCustomerAndStoreAndOrg(customerId, storeId, orgId);
        return e == null ? null : toDomain(e);
    }

    @Override
    public Cart findMostRecentByCreator(String createdBy, String orgId) {
        CartEntity e = cartDao.findMostRecentByCreator(createdBy, orgId);
        return e == null ? null : toDomain(e);
    }

    @Override
    public Cart getByIdScoped(String id, String orgId) {
        CartEntity e = cartDao.findByIdAndOrg(id, orgId);
        return e == null ? null : toDomain(e);
    }

    @Override
    public String createCart(String orgId, String customerId, String storeId, String createdBy) {
        CartEntity e = new CartEntity();
        e.id = UUID.randomUUID().toString();
        e.orgId = orgId;
        e.customerId = customerId;
        e.storeId = storeId;
        e.createdBy = createdBy;
        e.createdAt = System.currentTimeMillis();
        e.updatedAt = e.createdAt;
        cartDao.insert(e);
        return e.id;
    }

    @Override
    public List<CartItem> getCartItems(String cartId) {
        List<CartItemEntity> entities = cartItemDao.getCartItems(cartId);
        List<CartItem> items = new ArrayList<>(entities.size());
        for (CartItemEntity e : entities) items.add(toCartItemDomain(e));
        return items;
    }

    @Override
    public CartItem findCartItem(String cartId, String productId) {
        CartItemEntity e = cartItemDao.findByCartAndProduct(cartId, productId);
        return e == null ? null : toCartItemDomain(e);
    }

    @Override
    public void insertCartItem(CartItem item) {
        CartItemEntity e = new CartItemEntity();
        e.id = item.id != null ? item.id : UUID.randomUUID().toString();
        e.cartId = item.cartId;
        e.productId = item.productId;
        e.quantity = item.quantity;
        e.unitPriceSnapshotCents = item.unitPriceSnapshotCents;
        e.conflictFlag = item.conflictFlag;
        e.originalPriceCents = item.originalPriceCents;
        e.addedAt = System.currentTimeMillis();
        cartItemDao.insert(e);
    }

    @Override
    public void updateCartItem(CartItem item) {
        // Fetch existing entity to preserve the original addedAt timestamp,
        // since the CartItem domain model does not carry that field.
        CartItemEntity existing = cartItemDao.findByCartAndProduct(item.cartId, item.productId);
        long addedAt = existing != null ? existing.addedAt : System.currentTimeMillis();

        CartItemEntity e = new CartItemEntity();
        e.id = item.id;
        e.cartId = item.cartId;
        e.productId = item.productId;
        e.quantity = item.quantity;
        e.unitPriceSnapshotCents = item.unitPriceSnapshotCents;
        e.conflictFlag = item.conflictFlag;
        e.originalPriceCents = item.originalPriceCents;
        e.addedAt = addedAt;
        cartItemDao.update(e);
    }

    @Override
    public void deleteCartItem(String itemId) {
        cartItemDao.deleteById(itemId);
    }

    @Override
    public int getConflictCount(String cartId) {
        return cartItemDao.countConflicts(cartId);
    }

    @Override
    public void deleteCart(String cartId) {
        cartDao.deleteById(cartId);
    }

    private Cart toDomain(CartEntity e) {
        return new Cart(e.id, e.orgId, e.customerId, e.storeId, e.createdBy);
    }

    private CartItem toCartItemDomain(CartItemEntity e) {
        return new CartItem(e.id, e.cartId, e.productId, e.quantity,
                e.unitPriceSnapshotCents, e.conflictFlag, e.originalPriceCents);
    }
}
