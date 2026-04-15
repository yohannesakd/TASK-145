package com.roadrunner.dispatch.infrastructure.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.roadrunner.dispatch.infrastructure.db.entity.OrderEntity;

import java.util.List;

@Dao
public interface OrderDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(OrderEntity order);

    @Update
    void update(OrderEntity order);

    @Query("SELECT * FROM orders WHERE id = :id AND org_id = :orgId")
    OrderEntity findByIdAndOrg(String id, String orgId);

    @Query("SELECT * FROM orders WHERE org_id = :orgId AND status = :status ORDER BY updated_at DESC")
    LiveData<List<OrderEntity>> getOrdersByStatus(String orgId, String status);

    @Query("SELECT * FROM orders WHERE org_id = :orgId ORDER BY updated_at DESC")
    LiveData<List<OrderEntity>> getOrders(String orgId);

    @Query("SELECT * FROM orders WHERE created_by = :userId AND org_id = :orgId ORDER BY updated_at DESC")
    LiveData<List<OrderEntity>> getOrdersByUserAndOrg(String userId, String orgId);

    @Query("SELECT * FROM orders WHERE cart_id = :cartId AND org_id = :orgId AND status = 'DRAFT' LIMIT 1")
    OrderEntity findDraftByCartIdAndOrg(String cartId, String orgId);
}
