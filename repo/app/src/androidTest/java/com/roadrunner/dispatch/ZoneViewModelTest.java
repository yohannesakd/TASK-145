package com.roadrunner.dispatch;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.core.domain.model.Zone;
import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.repository.ZoneRepositoryImpl;
import com.roadrunner.dispatch.presentation.dispatch.zone.ZoneViewModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Instrumented tests for {@link ZoneViewModel} wired to real Room DB.
 */
@RunWith(AndroidJUnit4.class)
public class ZoneViewModelTest {

    private AppDatabase db;
    private ZoneViewModel viewModel;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();

        ZoneRepositoryImpl zoneRepo = new ZoneRepositoryImpl(db.zoneDao());
        viewModel = new ZoneViewModel(zoneRepo);
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void createZone_validInput_postsSavedZone() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        final Zone[] observed = {null};

        viewModel.getSavedZone().observeForever(zone -> {
            if (zone != null) {
                observed[0] = zone;
                latch.countDown();
            }
        });

        viewModel.createZone("org1", "Zone A", 5, "Primary zone");

        assertTrue("Zone should be saved within 5s", latch.await(5, TimeUnit.SECONDS));
        assertNotNull(observed[0]);
        assertEquals("Zone A", observed[0].name);
        assertEquals(5, observed[0].score);
    }

    @Test
    public void createZone_emptyName_postsError() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        viewModel.getError().observeForever(err -> {
            if (err != null) latch.countDown();
        });

        viewModel.createZone("org1", "", 3, null);

        assertTrue("Error should post within 5s", latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void createZone_invalidScore_postsError() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        viewModel.getError().observeForever(err -> {
            if (err != null) latch.countDown();
        });

        viewModel.createZone("org1", "Bad Zone", 0, null);

        assertTrue("Error should post within 5s", latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void createZone_scoreTooHigh_postsError() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        viewModel.getError().observeForever(err -> {
            if (err != null) latch.countDown();
        });

        viewModel.createZone("org1", "Bad Zone", 6, null);

        assertTrue("Error should post within 5s", latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void updateZone_validZone_postsSavedZone() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        final Zone[] observed = {null};

        viewModel.getSavedZone().observeForever(zone -> {
            if (zone != null) {
                observed[0] = zone;
                latch.countDown();
            }
        });

        Zone zone = new Zone("z1", "org1", "Updated Zone", 3, "Edited");
        viewModel.updateZone(zone);

        assertTrue("Zone should be saved within 5s", latch.await(5, TimeUnit.SECONDS));
        assertEquals("Updated Zone", observed[0].name);
    }

    @Test
    public void updateZone_emptyName_postsError() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        viewModel.getError().observeForever(err -> {
            if (err != null) latch.countDown();
        });

        viewModel.updateZone(new Zone("z1", "org1", "", 3, null));

        assertTrue("Error should post within 5s", latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void getZones_returnsLiveData() {
        assertNotNull(viewModel.getZones("org1"));
    }
}
