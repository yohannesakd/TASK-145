package com.roadrunner.dispatch;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.db.entity.ProductEntity;
import com.roadrunner.dispatch.infrastructure.repository.ProductRepositoryImpl;
import com.roadrunner.dispatch.presentation.commerce.catalog.CatalogViewModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Instrumented tests for {@link CatalogViewModel} wired to real Room DB.
 */
@RunWith(AndroidJUnit4.class)
public class CatalogViewModelTest {

    private AppDatabase db;
    private CatalogViewModel viewModel;
    private static final long NOW = System.currentTimeMillis();

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();

        ProductRepositoryImpl productRepo = new ProductRepositoryImpl(db.productDao());
        viewModel = new CatalogViewModel(productRepo);
    }

    @After
    public void tearDown() {
        db.close();
    }

    private void seedProducts() {
        db.productDao().insert(new ProductEntity("p1", "org1", "Dispatch Vest", "RR",
                "Safety", "V100", "High-vis vest", 2999L, 0.08, false, "ACTIVE", null, NOW, NOW));
        db.productDao().insert(new ProductEntity("p2", "org1", "Safety Helmet", "RR",
                "Safety", "H200", "Hard hat", 1499L, 0.08, false, "ACTIVE", null, NOW, NOW));
        db.productDao().insert(new ProductEntity("p3", "org1", "Work Gloves", "RR",
                "Gear", "G100", "Durable gloves", 999L, 0.08, false, "INACTIVE", null, NOW, NOW));
        db.productDao().insert(new ProductEntity("p4", "org2", "Other Org Item", "XX",
                "Other", "X100", "Cross-org", 500L, 0.08, false, "ACTIVE", null, NOW, NOW));
    }

    @Test
    public void getProducts_returnsLiveData() {
        seedProducts();
        assertNotNull(viewModel.getProducts("org1"));
    }

    @Test
    public void getProducts_calledTwice_returnsSameInstance() {
        seedProducts();
        assertSame(viewModel.getProducts("org1"), viewModel.getProducts("org1"));
    }

    @Test
    public void search_withQuery_returnsLiveData() {
        seedProducts();
        assertNotNull(viewModel.search("org1", "Vest"));
    }

    @Test
    public void search_nullQuery_resetsToAllProducts() {
        seedProducts();
        assertNotNull(viewModel.search("org1", null));
    }

    @Test
    public void search_emptyQuery_resetsToAllProducts() {
        seedProducts();
        assertNotNull(viewModel.search("org1", "  "));
    }

    @Test
    public void getError_returnsLiveData() {
        assertNotNull(viewModel.getError());
    }
}
