package com.roadrunner.dispatch.infrastructure.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.roadrunner.dispatch.infrastructure.db.entity.ZoneEntity;

import java.util.List;

@Dao
public interface ZoneDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ZoneEntity zone);

    @Update
    void update(ZoneEntity zone);

    @Query("SELECT * FROM zones WHERE id = :id AND org_id = :orgId")
    ZoneEntity findByIdAndOrg(String id, String orgId);

    @Query("SELECT * FROM zones WHERE org_id = :orgId ORDER BY score ASC")
    List<ZoneEntity> getZones(String orgId);

    @Query("SELECT * FROM zones WHERE org_id = :orgId ORDER BY score ASC")
    LiveData<List<ZoneEntity>> getZonesLive(String orgId);

    @Query("DELETE FROM zones WHERE id = :id")
    void deleteById(String id);
}
