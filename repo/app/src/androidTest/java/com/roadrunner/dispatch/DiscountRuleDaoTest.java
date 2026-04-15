package com.roadrunner.dispatch;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.db.dao.DiscountRuleDao;
import com.roadrunner.dispatch.infrastructure.db.entity.DiscountRuleEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DiscountRuleDaoTest {

    private AppDatabase db;
    private DiscountRuleDao dao;
    private static final long NOW = System.currentTimeMillis();

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = db.discountRuleDao();
    }

    @After
    public void tearDown() {
        db.close();
    }

    private DiscountRuleEntity makeRule(String id, String orgId, String name,
                                         String type, double value, String status) {
        return new DiscountRuleEntity(id, orgId, name, type, value, status, NOW);
    }

    @Test
    public void insert_andFindByIdAndOrg_returnsRule() {
        DiscountRuleEntity rule = makeRule("r1", "org1", "10% Off", "PERCENT_OFF", 10.0, "ACTIVE");
        dao.insert(rule);

        DiscountRuleEntity found = dao.findByIdAndOrg("r1", "org1");
        assertNotNull(found);
        assertEquals("10% Off", found.name);
        assertEquals("PERCENT_OFF", found.type);
        assertEquals(10.0, found.value, 0.001);
    }

    @Test
    public void findByIdAndOrg_wrongOrg_returnsNull() {
        dao.insert(makeRule("r1", "org1", "Discount", "PERCENT_OFF", 5.0, "ACTIVE"));

        assertNull(dao.findByIdAndOrg("r1", "org2"));
    }

    @Test
    public void update_modifiesExistingRule() {
        DiscountRuleEntity rule = makeRule("r1", "org1", "Old", "PERCENT_OFF", 5.0, "ACTIVE");
        dao.insert(rule);

        rule.name = "Updated";
        rule.value = 15.0;
        dao.update(rule);

        DiscountRuleEntity found = dao.findByIdAndOrg("r1", "org1");
        assertEquals("Updated", found.name);
        assertEquals(15.0, found.value, 0.001);
    }

    @Test
    public void getActiveRulesSync_filtersByOrgAndStatus() {
        dao.insert(makeRule("r1", "org1", "Active1", "PERCENT_OFF", 10.0, "ACTIVE"));
        dao.insert(makeRule("r2", "org1", "Active2", "FLAT_OFF", 500.0, "ACTIVE"));
        dao.insert(makeRule("r3", "org1", "Expired", "PERCENT_OFF", 5.0, "EXPIRED"));
        dao.insert(makeRule("r4", "org2", "OtherOrg", "PERCENT_OFF", 8.0, "ACTIVE"));

        List<DiscountRuleEntity> active = dao.getActiveRulesSync("org1");
        assertEquals(2, active.size());
    }

    @Test
    public void findByIdsAndOrg_returnsMatchingRules() {
        dao.insert(makeRule("r1", "org1", "Rule1", "PERCENT_OFF", 10.0, "ACTIVE"));
        dao.insert(makeRule("r2", "org1", "Rule2", "FLAT_OFF", 200.0, "ACTIVE"));
        dao.insert(makeRule("r3", "org1", "Rule3", "PERCENT_OFF", 5.0, "ACTIVE"));

        List<DiscountRuleEntity> found = dao.findByIdsAndOrg(Arrays.asList("r1", "r3"), "org1");
        assertEquals(2, found.size());
    }

    @Test
    public void findByIdsAndOrg_wrongOrg_returnsEmpty() {
        dao.insert(makeRule("r1", "org1", "Rule1", "PERCENT_OFF", 10.0, "ACTIVE"));

        List<DiscountRuleEntity> found = dao.findByIdsAndOrg(Arrays.asList("r1"), "org2");
        assertTrue(found.isEmpty());
    }

    @Test
    public void insert_duplicateId_throws() {
        dao.insert(makeRule("r1", "org1", "First", "PERCENT_OFF", 10.0, "ACTIVE"));
        try {
            dao.insert(makeRule("r1", "org1", "Duplicate", "FLAT_OFF", 5.0, "ACTIVE"));
            fail("Should throw on duplicate insert");
        } catch (Exception e) {
            // Expected — ABORT strategy
        }
    }
}
