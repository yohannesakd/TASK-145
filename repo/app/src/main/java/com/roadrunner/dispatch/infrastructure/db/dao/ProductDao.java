package com.roadrunner.dispatch.infrastructure.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.roadrunner.dispatch.infrastructure.db.entity.ProductEntity;

import java.util.List;

@Dao
public interface ProductDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(ProductEntity product);

    @Update
    void update(ProductEntity product);

    @Query("SELECT * FROM products WHERE id = :id AND org_id = :orgId")
    ProductEntity findByIdAndOrg(String id, String orgId);

    @Query("SELECT * FROM products WHERE org_id = :orgId AND status = 'ACTIVE' ORDER BY updated_at DESC")
    LiveData<List<ProductEntity>> getActiveProducts(String orgId);

    @Query("SELECT * FROM products WHERE org_id = :orgId AND status = 'ACTIVE' AND (brand LIKE '%' || :query || '%' OR series LIKE '%' || :query || '%' OR model LIKE '%' || :query || '%' OR name LIKE '%' || :query || '%') ORDER BY updated_at DESC")
    LiveData<List<ProductEntity>> searchActiveProducts(String orgId, String query);

    @Query("SELECT * FROM products WHERE org_id = :orgId AND status = 'ACTIVE' ORDER BY updated_at DESC")
    List<ProductEntity> getActiveProductsSync(String orgId);

    @Query("SELECT COUNT(*) FROM products WHERE org_id = :orgId AND status = 'ACTIVE'")
    LiveData<Integer> getActiveProductCount(String orgId);
}
