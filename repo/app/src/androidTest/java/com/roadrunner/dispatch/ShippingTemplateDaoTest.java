package com.roadrunner.dispatch;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.db.dao.ShippingTemplateDao;
import com.roadrunner.dispatch.infrastructure.db.entity.ShippingTemplateEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ShippingTemplateDaoTest {

    private AppDatabase db;
    private ShippingTemplateDao dao;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = db.shippingTemplateDao();
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void insert_andGetTemplates_returnsList() {
        dao.insert(new ShippingTemplateEntity("s1", "org1", "Standard", "3-7 days", 599L, 3, 7, false));
        dao.insert(new ShippingTemplateEntity("s2", "org1", "Expedited", "1-2 days", 1499L, 1, 2, false));

        List<ShippingTemplateEntity> templates = dao.getTemplates("org1");
        assertEquals(2, templates.size());
    }

    @Test
    public void getTemplates_filtersbyOrg() {
        dao.insert(new ShippingTemplateEntity("s1", "org1", "Standard", "Desc", 599L, 3, 7, false));
        dao.insert(new ShippingTemplateEntity("s2", "org2", "Express", "Desc", 1499L, 1, 2, false));

        List<ShippingTemplateEntity> org1 = dao.getTemplates("org1");
        assertEquals(1, org1.size());
        assertEquals("Standard", org1.get(0).name);
    }

    @Test
    public void findByIdAndOrg_returnsCorrectTemplate() {
        dao.insert(new ShippingTemplateEntity("s1", "org1", "Standard", "Desc", 599L, 3, 7, false));

        ShippingTemplateEntity found = dao.findByIdAndOrg("s1", "org1");
        assertNotNull(found);
        assertEquals("Standard", found.name);
        assertEquals(599L, found.costCents);
    }

    @Test
    public void findByIdAndOrg_wrongOrg_returnsNull() {
        dao.insert(new ShippingTemplateEntity("s1", "org1", "Standard", "Desc", 599L, 3, 7, false));

        assertNull(dao.findByIdAndOrg("s1", "org2"));
    }

    @Test
    public void insert_duplicateId_replacesExisting() {
        dao.insert(new ShippingTemplateEntity("s1", "org1", "Old", "Desc", 599L, 3, 7, false));
        dao.insert(new ShippingTemplateEntity("s1", "org1", "Updated", "New desc", 799L, 2, 5, false));

        ShippingTemplateEntity found = dao.findByIdAndOrg("s1", "org1");
        assertEquals("Updated", found.name);
        assertEquals(799L, found.costCents);
    }

    @Test
    public void insert_pickupTemplate_flagPreserved() {
        dao.insert(new ShippingTemplateEntity("s1", "org1", "Local Pickup", "Free", 0L, 0, 0, true));

        ShippingTemplateEntity found = dao.findByIdAndOrg("s1", "org1");
        assertTrue(found.isPickup);
        assertEquals(0L, found.costCents);
    }
}
