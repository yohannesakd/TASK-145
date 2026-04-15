package com.roadrunner.dispatch.infrastructure.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.roadrunner.dispatch.infrastructure.db.entity.DiscountRuleEntity;

import java.util.List;

@Dao
public interface DiscountRuleDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(DiscountRuleEntity rule);

    @Update
    void update(DiscountRuleEntity rule);

    @Query("SELECT * FROM discount_rules WHERE id = :id AND org_id = :orgId")
    DiscountRuleEntity findByIdAndOrg(String id, String orgId);

    @Query("SELECT * FROM discount_rules WHERE org_id = :orgId AND status = 'ACTIVE'")
    LiveData<List<DiscountRuleEntity>> getActiveRules(String orgId);

    @Query("SELECT * FROM discount_rules WHERE org_id = :orgId AND status = 'ACTIVE'")
    List<DiscountRuleEntity> getActiveRulesSync(String orgId);

    @Query("SELECT * FROM discount_rules WHERE id IN (:ids) AND org_id = :orgId")
    List<DiscountRuleEntity> findByIdsAndOrg(List<String> ids, String orgId);
}
