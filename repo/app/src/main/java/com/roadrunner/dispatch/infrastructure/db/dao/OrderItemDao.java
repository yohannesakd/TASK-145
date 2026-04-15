package com.roadrunner.dispatch.infrastructure.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.roadrunner.dispatch.infrastructure.db.entity.OrderItemEntity;

import java.util.List;

@Dao
public interface OrderItemDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(OrderItemEntity item);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertAll(List<OrderItemEntity> items);

    @Query("SELECT * FROM order_items WHERE order_id = :orderId")
    List<OrderItemEntity> getOrderItems(String orderId);

    @Query("SELECT * FROM order_items WHERE order_id = :orderId")
    LiveData<List<OrderItemEntity>> getOrderItemsLive(String orderId);

    @Query("DELETE FROM order_items WHERE order_id = :orderId")
    void deleteByOrderId(String orderId);
}
