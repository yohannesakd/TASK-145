package com.roadrunner.dispatch;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.di.ServiceLocator;
import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.db.entity.UserEntity;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Instrumented tests verifying application bootstrap wiring:
 * ServiceLocator initialisation, database creation, repository access,
 * RoadRunnerApp singleton, MainActivity hosting, and seed data insertion.
 */
@RunWith(AndroidJUnit4.class)
public class AppBootstrapTest {

    // ── ServiceLocator ───────────────────────────────────────────────────────

    @Test
    public void serviceLocator_initialises_withContext() {
        Context context = ApplicationProvider.getApplicationContext();
        ServiceLocator sl = ServiceLocator.getInstance(context);
        assertNotNull(sl);
    }

    @Test
    public void serviceLocator_returnsSessionManager() {
        Context context = ApplicationProvider.getApplicationContext();
        ServiceLocator sl = ServiceLocator.getInstance(context);
        assertNotNull(sl.getSessionManager());
    }

    @Test
    public void serviceLocator_returnsDatabase() {
        Context context = ApplicationProvider.getApplicationContext();
        ServiceLocator sl = ServiceLocator.getInstance(context);
        assertNotNull(sl.getDatabase());
    }

    @Test
    public void serviceLocator_allRepositories_nonNull() {
        Context context = ApplicationProvider.getApplicationContext();
        ServiceLocator sl = ServiceLocator.getInstance(context);

        assertNotNull("UserRepository", sl.getUserRepository());
        assertNotNull("ProductRepository", sl.getProductRepository());
        assertNotNull("CartRepository", sl.getCartRepository());
        assertNotNull("OrderRepository", sl.getOrderRepository());
        assertNotNull("ZoneRepository", sl.getZoneRepository());
        assertNotNull("WorkerRepository", sl.getWorkerRepository());
        assertNotNull("TaskRepository", sl.getTaskRepository());
        assertNotNull("EmployerRepository", sl.getEmployerRepository());
        assertNotNull("ComplianceCaseRepository", sl.getComplianceCaseRepository());
        assertNotNull("AuditLogRepository", sl.getAuditLogRepository());
        assertNotNull("ReportRepository", sl.getReportRepository());
        assertNotNull("SensitiveWordRepository", sl.getSensitiveWordRepository());
    }

    @Test
    public void serviceLocator_allUseCases_nonNull() {
        Context context = ApplicationProvider.getApplicationContext();
        ServiceLocator sl = ServiceLocator.getInstance(context);

        assertNotNull("LoginUseCase", sl.getLoginUseCase());
        assertNotNull("RegisterUserUseCase", sl.getRegisterUserUseCase());
        assertNotNull("AddToCartUseCase", sl.getAddToCartUseCase());
        assertNotNull("ResolveCartConflictUseCase", sl.getResolveCartConflictUseCase());
        assertNotNull("CreateOrderFromCartUseCase", sl.getCreateOrderFromCartUseCase());
        assertNotNull("ValidateDiscountsUseCase", sl.getValidateDiscountsUseCase());
        assertNotNull("ComputeOrderTotalsUseCase", sl.getComputeOrderTotalsUseCase());
        assertNotNull("FinalizeCheckoutUseCase", sl.getFinalizeCheckoutUseCase());
        assertNotNull("CreateTaskUseCase", sl.getCreateTaskUseCase());
        assertNotNull("MatchTasksUseCase", sl.getMatchTasksUseCase());
        assertNotNull("AcceptTaskUseCase", sl.getAcceptTaskUseCase());
        assertNotNull("CompleteTaskUseCase", sl.getCompleteTaskUseCase());
        assertNotNull("VerifyEmployerUseCase", sl.getVerifyEmployerUseCase());
        assertNotNull("ScanContentUseCase", sl.getScanContentUseCase());
        assertNotNull("EnforceViolationUseCase", sl.getEnforceViolationUseCase());
        assertNotNull("FileReportUseCase", sl.getFileReportUseCase());
        assertNotNull("OpenCaseUseCase", sl.getOpenCaseUseCase());
        assertNotNull("CreateProductUseCase", sl.getCreateProductUseCase());
        assertNotNull("CreateShippingTemplateUseCase", sl.getCreateShippingTemplateUseCase());
        assertNotNull("CreateDiscountRuleUseCase", sl.getCreateDiscountRuleUseCase());
        assertNotNull("CreateZoneUseCase", sl.getCreateZoneUseCase());
    }

    @Test
    public void serviceLocator_repositorySingletons_areSameInstance() {
        Context context = ApplicationProvider.getApplicationContext();
        ServiceLocator sl = ServiceLocator.getInstance(context);

        assertSame(sl.getUserRepository(), sl.getUserRepository());
        assertSame(sl.getProductRepository(), sl.getProductRepository());
        assertSame(sl.getCartRepository(), sl.getCartRepository());
        assertSame(sl.getOrderRepository(), sl.getOrderRepository());
    }

    @Test
    public void database_isOpen_afterInit() {
        Context context = ApplicationProvider.getApplicationContext();
        ServiceLocator sl = ServiceLocator.getInstance(context);
        AppDatabase db = sl.getDatabase();
        assertTrue(db.isOpen());
    }

    // ── RoadRunnerApp ────────────────────────────────────────────────────────

    @Test
    public void roadRunnerApp_getInstance_returnsNonNull() {
        RoadRunnerApp app = RoadRunnerApp.getInstance();
        assertNotNull("RoadRunnerApp singleton should be available", app);
    }

    @Test
    public void roadRunnerApp_databaseAccessible_afterOnCreate() {
        RoadRunnerApp app = RoadRunnerApp.getInstance();
        assertNotNull(AppDatabase.getInstance(app));
    }

    // ── MainActivity ─────────────────────────────────────────────────────────

    @Test
    public void mainActivity_launches_withoutCrash() {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        scenario.onActivity(activity -> assertNotNull(activity));
        scenario.close();
    }

    // ── Seed Data Verification ───────────────────────────────────────────────

    @Test
    public void seedData_adminUserExists() throws InterruptedException {
        // Give the seed callback time to complete (it runs on a background thread)
        Thread.sleep(2000);
        Context context = ApplicationProvider.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(context);
        UserEntity admin = db.userDao().findByUsername("admin");
        assertNotNull("Seeded admin user should exist", admin);
        assertEquals("ADMIN", admin.role);
        assertTrue(admin.isActive);
    }

    @Test
    public void seedData_dispatcherUserExists() throws InterruptedException {
        Thread.sleep(2000);
        Context context = ApplicationProvider.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(context);
        UserEntity dispatcher = db.userDao().findByUsername("dispatcher");
        assertNotNull("Seeded dispatcher user should exist", dispatcher);
        assertEquals("DISPATCHER", dispatcher.role);
    }

    @Test
    public void seedData_workerUserExists() throws InterruptedException {
        Thread.sleep(2000);
        Context context = ApplicationProvider.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(context);
        UserEntity worker = db.userDao().findByUsername("worker");
        assertNotNull("Seeded worker user should exist", worker);
        assertEquals("WORKER", worker.role);
    }

    @Test
    public void seedData_reviewerUserExists() throws InterruptedException {
        Thread.sleep(2000);
        Context context = ApplicationProvider.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(context);
        UserEntity reviewer = db.userDao().findByUsername("reviewer");
        assertNotNull("Seeded reviewer user should exist", reviewer);
        assertEquals("COMPLIANCE_REVIEWER", reviewer.role);
    }

    @Test
    public void seedData_shippingTemplatesExist() throws InterruptedException {
        Thread.sleep(2000);
        Context context = ApplicationProvider.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(context);
        List<?> templates = db.shippingTemplateDao().getTemplates("default_org");
        assertEquals("Should have 3 seeded shipping templates", 3, templates.size());
    }

    @Test
    public void seedData_zonesExist() throws InterruptedException {
        Thread.sleep(2000);
        Context context = ApplicationProvider.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(context);
        List<?> zones = db.zoneDao().getZones("default_org");
        assertEquals("Should have 5 seeded zones", 5, zones.size());
    }

    @Test
    public void seedData_productsExist() throws InterruptedException {
        Thread.sleep(2000);
        Context context = ApplicationProvider.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(context);
        List<?> products = db.productDao().getActiveProductsSync("default_org");
        assertEquals("Should have 5 seeded products", 5, products.size());
    }

    @Test
    public void seedData_discountRuleExists() throws InterruptedException {
        Thread.sleep(2000);
        Context context = ApplicationProvider.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(context);
        List<?> rules = db.discountRuleDao().getActiveRulesSync("default_org");
        assertEquals("Should have 1 seeded discount rule", 1, rules.size());
    }
}
