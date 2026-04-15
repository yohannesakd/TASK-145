package com.roadrunner.dispatch;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.core.domain.model.Product;
import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.db.dao.ProductDao;
import com.roadrunner.dispatch.infrastructure.repository.ProductRepositoryImpl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ProductRepositoryImplTest {

    private AppDatabase db;
    private ProductDao productDao;
    private ProductRepositoryImpl repo;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        productDao = db.productDao();
        repo = new ProductRepositoryImpl(productDao);
    }

    @After
    public void tearDown() {
        db.close();
    }

    // -----------------------------------------------------------------------
    // Insert and query
    // -----------------------------------------------------------------------

    @Test
    public void insert_and_getByIdScoped() {
        Product p = new Product("p1", "org1", "Safety Helmet", "BrandX", "Pro", "M100",
                "Hard hat", 2500L, 0.08, false, "ACTIVE", null);
        repo.insert(p);
        Product found = repo.getByIdScoped("p1", "org1");
        assertNotNull(found);
        assertEquals("Safety Helmet", found.name);
        assertEquals("BrandX", found.brand);
        assertEquals(2500L, found.unitPriceCents);
        assertEquals(0.08, found.taxRate, 0.001);
    }

    @Test
    public void getByIdScoped_wrongOrg_returnsNull() {
        Product p = new Product("p1", "org1", "Vest", "B", "S", "M",
                "desc", 1000L, 0.05, false, "ACTIVE", null);
        repo.insert(p);
        assertNull(repo.getByIdScoped("p1", "org2"));
    }

    @Test
    public void getActiveProductsSync_filtersOrg() {
        repo.insert(new Product("p1", "org1", "A", "B", "S", "M", "d", 100L, 0.0, false, "ACTIVE", null));
        repo.insert(new Product("p2", "org2", "B", "B", "S", "M", "d", 200L, 0.0, false, "ACTIVE", null));
        repo.insert(new Product("p3", "org1", "C", "B", "S", "M", "d", 300L, 0.0, false, "INACTIVE", null));

        List<Product> products = repo.getActiveProductsSync("org1");
        assertEquals(1, products.size());
        assertEquals("p1", products.get(0).id);
    }

    // -----------------------------------------------------------------------
    // Update
    // -----------------------------------------------------------------------

    @Test
    public void update_changesFields() {
        repo.insert(new Product("p1", "org1", "Gloves", "B", "S", "M",
                "work gloves", 500L, 0.05, false, "ACTIVE", null));
        Product updated = new Product("p1", "org1", "Gloves v2", "B", "S", "M",
                "updated gloves", 600L, 0.07, true, "ACTIVE", null);
        repo.update(updated);

        Product found = repo.getByIdScoped("p1", "org1");
        assertNotNull(found);
        assertEquals("Gloves v2", found.name);
        assertEquals(600L, found.unitPriceCents);
        assertTrue(found.regulated);
    }

    @Test
    public void insert_regulated_product() {
        Product p = new Product("p1", "org1", "Chemical Kit", "ChemCo", "Hazard", "HZ1",
                "Regulated chemical kit", 5000L, 0.10, true, "ACTIVE", null);
        repo.insert(p);
        Product found = repo.getByIdScoped("p1", "org1");
        assertNotNull(found);
        assertTrue(found.regulated);
        assertEquals(0.10, found.taxRate, 0.001);
    }
}
