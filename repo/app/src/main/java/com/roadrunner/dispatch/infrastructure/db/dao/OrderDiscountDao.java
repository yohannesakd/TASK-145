package com.roadrunner.dispatch.infrastructure.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.roadrunner.dispatch.infrastructure.db.entity.OrderDiscountEntity;

import java.util.List;

@Dao
public interface OrderDiscountDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(OrderDiscountEntity discount);

    @Query("SELECT * FROM order_discounts WHERE order_id = :orderId")
    List<OrderDiscountEntity> getDiscountsForOrder(String orderId);

    @Query("DELETE FROM order_discounts WHERE order_id = :orderId")
    void deleteByOrderId(String orderId);
}
