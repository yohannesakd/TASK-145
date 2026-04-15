package com.roadrunner.dispatch;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.core.domain.model.ContentScanResult;
import com.roadrunner.dispatch.core.domain.model.Employer;
import com.roadrunner.dispatch.core.domain.usecase.ScanContentUseCase;
import com.roadrunner.dispatch.core.domain.usecase.VerifyEmployerUseCase;
import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.db.entity.SensitiveWordEntity;
import com.roadrunner.dispatch.infrastructure.repository.EmployerRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.SensitiveWordRepositoryImpl;
import com.roadrunner.dispatch.presentation.compliance.employer.EmployerViewModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Instrumented tests for {@link EmployerViewModel} wired to real Room DB.
 */
@RunWith(AndroidJUnit4.class)
public class EmployerViewModelTest {

    private AppDatabase db;
    private EmployerViewModel viewModel;
    private static final long NOW = System.currentTimeMillis();

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();

        EmployerRepositoryImpl employerRepo = new EmployerRepositoryImpl(db, db.employerDao());
        SensitiveWordRepositoryImpl sensitiveWordRepo =
                new SensitiveWordRepositoryImpl(db.sensitiveWordDao());

        VerifyEmployerUseCase verifyUseCase = new VerifyEmployerUseCase(employerRepo);
        ScanContentUseCase scanUseCase = new ScanContentUseCase(sensitiveWordRepo);

        viewModel = new EmployerViewModel(employerRepo, verifyUseCase, scanUseCase);
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void verifyEmployer_validData_postsSavedEmployer() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        final Employer[] observed = {null};

        viewModel.getSavedEmployer().observeForever(emp -> {
            if (emp != null) {
                observed[0] = emp;
                latch.countDown();
            }
        });

        Employer emp = new Employer(UUID.randomUUID().toString(), "org1", "Acme Corp",
                "12-3456789", "123 Main St", "Springfield", "IL", "62701",
                "PENDING", 0, 0L, false);
        viewModel.verifyEmployer(emp, "COMPLIANCE_REVIEWER");

        assertTrue("Verify should complete within 5s", latch.await(5, TimeUnit.SECONDS));
        assertNotNull(observed[0]);
        assertEquals("Acme Corp", observed[0].legalName);
    }

    @Test
    public void verifyEmployer_invalidEin_postsError() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        viewModel.getError().observeForever(err -> {
            if (err != null) latch.countDown();
        });

        Employer emp = new Employer(UUID.randomUUID().toString(), "org1", "Bad Corp",
                "INVALID", "123 Main", "City", "IL", "60601",
                "PENDING", 0, 0L, false);
        viewModel.verifyEmployer(emp, "COMPLIANCE_REVIEWER");

        assertTrue("Error should post within 5s", latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void verifyEmployer_wrongRole_postsError() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        viewModel.getError().observeForever(err -> {
            if (err != null) latch.countDown();
        });

        Employer emp = new Employer(UUID.randomUUID().toString(), "org1", "Corp",
                "12-3456789", "123 Main", "City", "IL", "60601",
                "PENDING", 0, 0L, false);
        viewModel.verifyEmployer(emp, "WORKER");

        assertTrue("Error should post within 5s", latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void scanContent_cleanText_postsCleanResult() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        final ContentScanResult[] observed = {null};

        viewModel.getScanResult().observeForever(result -> {
            if (result != null) {
                observed[0] = result;
                latch.countDown();
            }
        });

        viewModel.scanContent("Normal employer description");

        assertTrue("Scan should complete within 5s", latch.await(5, TimeUnit.SECONDS));
        assertEquals("CLEAN", observed[0].status);
    }

    @Test
    public void scanContent_withSensitiveWord_postsFlagged() throws InterruptedException {
        db.sensitiveWordDao().insert(new SensitiveWordEntity("sw1", "badword", false, NOW));

        CountDownLatch latch = new CountDownLatch(1);
        final ContentScanResult[] observed = {null};

        viewModel.getScanResult().observeForever(result -> {
            if (result != null) {
                observed[0] = result;
                latch.countDown();
            }
        });

        viewModel.scanContent("Contains badword in text");

        assertTrue("Scan should complete within 5s", latch.await(5, TimeUnit.SECONDS));
        assertEquals("FLAGGED", observed[0].status);
    }

    @Test
    public void getEmployers_returnsLiveData() {
        assertNotNull(viewModel.getEmployers("org1"));
    }

    @Test
    public void getEmployersByStatus_returnsLiveData() {
        assertNotNull(viewModel.getEmployersByStatus("org1", "VERIFIED"));
    }
}
