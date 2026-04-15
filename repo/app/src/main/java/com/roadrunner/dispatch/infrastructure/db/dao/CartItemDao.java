package com.roadrunner.dispatch.infrastructure.db.dao;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.roadrunner.dispatch.infrastructure.db.entity.CartItemEntity;

import java.util.List;

@Dao
public interface CartItemDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(CartItemEntity item);

    @Update
    void update(CartItemEntity item);

    @Query("SELECT * FROM cart_items WHERE cart_id = :cartId")
    List<CartItemEntity> getCartItems(String cartId);

    @Query("SELECT * FROM cart_items WHERE cart_id = :cartId")
    LiveData<List<CartItemEntity>> getCartItemsLive(String cartId);

    @Nullable
    @Query("SELECT * FROM cart_items WHERE cart_id = :cartId AND product_id = :productId")
    CartItemEntity findByCartAndProduct(String cartId, String productId);

    @Query("DELETE FROM cart_items WHERE id = :id")
    void deleteById(String id);

    @Query("SELECT COUNT(*) FROM cart_items WHERE cart_id = :cartId AND conflict_flag = 1")
    int countConflicts(String cartId);
}
