package com.roadrunner.dispatch.infrastructure.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.roadrunner.dispatch.infrastructure.db.entity.ShippingTemplateEntity;

import java.util.List;

@Dao
public interface ShippingTemplateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ShippingTemplateEntity template);

    @Query("SELECT * FROM shipping_templates WHERE org_id = :orgId ORDER BY name ASC")
    List<ShippingTemplateEntity> getTemplates(String orgId);

    @Query("SELECT * FROM shipping_templates WHERE org_id = :orgId ORDER BY name ASC")
    LiveData<List<ShippingTemplateEntity>> getTemplatesLive(String orgId);

    @Query("SELECT * FROM shipping_templates WHERE id = :id AND org_id = :orgId")
    ShippingTemplateEntity findByIdAndOrg(String id, String orgId);
}
