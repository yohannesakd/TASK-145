package com.roadrunner.dispatch.infrastructure.db.dao;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.roadrunner.dispatch.infrastructure.db.entity.CartEntity;

@Dao
public interface CartDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(CartEntity cart);

    @Update
    void update(CartEntity cart);

    @Query("SELECT * FROM carts WHERE id = :id AND org_id = :orgId")
    CartEntity findByIdAndOrg(String id, String orgId);

    @Nullable
    @Query("SELECT * FROM carts WHERE customer_id = :customerId AND store_id = :storeId AND org_id = :orgId LIMIT 1")
    CartEntity findByCustomerAndStoreAndOrg(String customerId, String storeId, String orgId);

    @Nullable
    @Query("SELECT * FROM carts WHERE created_by = :createdBy AND org_id = :orgId ORDER BY rowid DESC LIMIT 1")
    CartEntity findMostRecentByCreator(String createdBy, String orgId);

    @Query("DELETE FROM carts WHERE id = :id")
    void deleteById(String id);
}
