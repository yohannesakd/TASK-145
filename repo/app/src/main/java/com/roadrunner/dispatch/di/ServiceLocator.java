package com.roadrunner.dispatch.di;

import android.content.Context;

import com.roadrunner.dispatch.core.domain.repository.AuditLogRepository;
import com.roadrunner.dispatch.core.domain.repository.CartRepository;
import com.roadrunner.dispatch.core.domain.repository.ComplianceCaseRepository;
import com.roadrunner.dispatch.core.domain.repository.EmployerRepository;
import com.roadrunner.dispatch.core.domain.repository.OrderRepository;
import com.roadrunner.dispatch.core.domain.repository.ProductRepository;
import com.roadrunner.dispatch.core.domain.repository.ReportRepository;
import com.roadrunner.dispatch.core.domain.repository.SensitiveWordRepository;
import com.roadrunner.dispatch.core.domain.repository.TaskRepository;
import com.roadrunner.dispatch.core.domain.repository.UserRepository;
import com.roadrunner.dispatch.core.domain.repository.WorkerRepository;
import com.roadrunner.dispatch.core.domain.repository.ZoneRepository;
import com.roadrunner.dispatch.core.domain.usecase.AcceptTaskUseCase;
import com.roadrunner.dispatch.core.domain.usecase.AddToCartUseCase;
import com.roadrunner.dispatch.core.domain.usecase.CompleteTaskUseCase;
import com.roadrunner.dispatch.core.domain.usecase.ComputeOrderTotalsUseCase;
import com.roadrunner.dispatch.core.domain.usecase.CreateOrderFromCartUseCase;
import com.roadrunner.dispatch.core.domain.usecase.CreateTaskUseCase;
import com.roadrunner.dispatch.core.domain.usecase.EnforceViolationUseCase;
import com.roadrunner.dispatch.core.domain.usecase.FileReportUseCase;
import com.roadrunner.dispatch.core.domain.usecase.FinalizeCheckoutUseCase;
import com.roadrunner.dispatch.core.domain.usecase.LoginUseCase;
import com.roadrunner.dispatch.core.domain.usecase.MatchTasksUseCase;
import com.roadrunner.dispatch.core.domain.usecase.OpenCaseUseCase;
import com.roadrunner.dispatch.core.domain.usecase.RegisterUserUseCase;
import com.roadrunner.dispatch.core.domain.usecase.ResolveCartConflictUseCase;
import com.roadrunner.dispatch.core.domain.usecase.ScanContentUseCase;
import com.roadrunner.dispatch.core.domain.usecase.ValidateDiscountsUseCase;
import com.roadrunner.dispatch.core.domain.usecase.CreateDiscountRuleUseCase;
import com.roadrunner.dispatch.core.domain.usecase.CreateShippingTemplateUseCase;
import com.roadrunner.dispatch.core.domain.usecase.CreateZoneUseCase;
import com.roadrunner.dispatch.core.domain.usecase.VerifyEmployerUseCase;
import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.repository.AuditLogRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.CartRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.ComplianceCaseRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.EmployerRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.OrderRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.ProductRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.ReportRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.SensitiveWordRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.TaskRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.UserRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.WorkerRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.ZoneRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.security.SessionManager;

/**
 * Manual dependency injection container (Service Locator pattern).
 *
 * <p>All repositories are lazily initialised on first use and then cached as
 * singletons. Use-cases are created fresh on each call — they are stateless
 * so no caching is needed.
 *
 * <p>Initialise from {@link com.roadrunner.dispatch.RoadRunnerApp#onCreate()},
 * then retrieve the singleton via {@link #getInstance()}.
 */
public class ServiceLocator {

    private static volatile ServiceLocator INSTANCE;

    private final AppDatabase db;
    private final SessionManager sessionManager;

    // ── Repositories (lazy-init, guarded by their own synchronized getter) ──

    private UserRepository userRepository;
    private ProductRepository productRepository;
    private CartRepository cartRepository;
    private OrderRepository orderRepository;
    private ZoneRepository zoneRepository;
    private WorkerRepository workerRepository;
    private TaskRepository taskRepository;
    private EmployerRepository employerRepository;
    private ComplianceCaseRepository complianceCaseRepository;
    private AuditLogRepository auditLogRepository;
    private ReportRepository reportRepository;
    private SensitiveWordRepository sensitiveWordRepository;

    private ServiceLocator(Context context) {
        db = AppDatabase.getInstance(context);
        sessionManager = new SessionManager(context);
    }

    /** Initialise the singleton; call once from Application.onCreate(). */
    public static synchronized ServiceLocator getInstance(Context context) {
        if (INSTANCE == null) {
            INSTANCE = new ServiceLocator(context.getApplicationContext());
        }
        return INSTANCE;
    }

    /** Retrieve the already-initialised singleton. Throws if not yet initialised. */
    public static ServiceLocator getInstance() {
        if (INSTANCE == null) {
            throw new IllegalStateException(
                    "ServiceLocator has not been initialised. Call getInstance(Context) first.");
        }
        return INSTANCE;
    }

    // ── Core infrastructure ───────────────────────────────────────────────────

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public AppDatabase getDatabase() {
        return db;
    }

    // ── Repositories ──────────────────────────────────────────────────────────

    public synchronized UserRepository getUserRepository() {
        if (userRepository == null) {
            userRepository = new UserRepositoryImpl(db.userDao());
        }
        return userRepository;
    }

    public synchronized ProductRepository getProductRepository() {
        if (productRepository == null) {
            productRepository = new ProductRepositoryImpl(db.productDao());
        }
        return productRepository;
    }

    public synchronized CartRepository getCartRepository() {
        if (cartRepository == null) {
            cartRepository = new CartRepositoryImpl(db.cartDao(), db.cartItemDao());
        }
        return cartRepository;
    }

    public synchronized OrderRepository getOrderRepository() {
        if (orderRepository == null) {
            orderRepository = new OrderRepositoryImpl(
                    db,
                    db.orderDao(),
                    db.orderItemDao(),
                    db.orderDiscountDao(),
                    db.discountRuleDao(),
                    db.shippingTemplateDao(),
                    db.auditLogDao());
        }
        return orderRepository;
    }

    public synchronized ZoneRepository getZoneRepository() {
        if (zoneRepository == null) {
            zoneRepository = new ZoneRepositoryImpl(db.zoneDao());
        }
        return zoneRepository;
    }

    public synchronized WorkerRepository getWorkerRepository() {
        if (workerRepository == null) {
            workerRepository = new WorkerRepositoryImpl(db.workerDao(), db.reputationEventDao());
        }
        return workerRepository;
    }

    public synchronized TaskRepository getTaskRepository() {
        if (taskRepository == null) {
            taskRepository = new TaskRepositoryImpl(db, db.taskDao(), db.taskAcceptanceDao());
        }
        return taskRepository;
    }

    public synchronized EmployerRepository getEmployerRepository() {
        if (employerRepository == null) {
            employerRepository = new EmployerRepositoryImpl(db, db.employerDao());
        }
        return employerRepository;
    }

    public synchronized ComplianceCaseRepository getComplianceCaseRepository() {
        if (complianceCaseRepository == null) {
            complianceCaseRepository = new ComplianceCaseRepositoryImpl(db, db.complianceCaseDao());
        }
        return complianceCaseRepository;
    }

    public synchronized AuditLogRepository getAuditLogRepository() {
        if (auditLogRepository == null) {
            auditLogRepository = new AuditLogRepositoryImpl(db.auditLogDao());
        }
        return auditLogRepository;
    }

    public synchronized ReportRepository getReportRepository() {
        if (reportRepository == null) {
            reportRepository = new ReportRepositoryImpl(db.reportDao());
        }
        return reportRepository;
    }

    public synchronized SensitiveWordRepository getSensitiveWordRepository() {
        if (sensitiveWordRepository == null) {
            sensitiveWordRepository = new SensitiveWordRepositoryImpl(db.sensitiveWordDao());
        }
        return sensitiveWordRepository;
    }

    // ── Use Cases (stateless; create fresh each time) ─────────────────────────

    public LoginUseCase getLoginUseCase() {
        return new LoginUseCase(getUserRepository());
    }

    public RegisterUserUseCase getRegisterUserUseCase() {
        return new RegisterUserUseCase(getUserRepository(), getWorkerRepository());
    }

    public AddToCartUseCase getAddToCartUseCase() {
        return new AddToCartUseCase(getCartRepository(), getProductRepository());
    }

    public ResolveCartConflictUseCase getResolveCartConflictUseCase() {
        return new ResolveCartConflictUseCase(getCartRepository());
    }

    public CreateOrderFromCartUseCase getCreateOrderFromCartUseCase() {
        return new CreateOrderFromCartUseCase(
                getCartRepository(), getOrderRepository(), getProductRepository());
    }

    public ValidateDiscountsUseCase getValidateDiscountsUseCase() {
        return new ValidateDiscountsUseCase();
    }

    public ComputeOrderTotalsUseCase getComputeOrderTotalsUseCase() {
        return new ComputeOrderTotalsUseCase();
    }

    public FinalizeCheckoutUseCase getFinalizeCheckoutUseCase() {
        return new FinalizeCheckoutUseCase(
                getOrderRepository(),
                getAuditLogRepository(),
                getValidateDiscountsUseCase(),
                getComputeOrderTotalsUseCase(),
                getScanContentUseCase());
    }

    public CreateTaskUseCase getCreateTaskUseCase() {
        return new CreateTaskUseCase(getTaskRepository(), getZoneRepository(), getScanContentUseCase());
    }

    public MatchTasksUseCase getMatchTasksUseCase() {
        return new MatchTasksUseCase(
                getWorkerRepository(), getZoneRepository(), getTaskRepository());
    }

    public AcceptTaskUseCase getAcceptTaskUseCase() {
        return new AcceptTaskUseCase(getTaskRepository(), getWorkerRepository(), getAuditLogRepository());
    }

    public CompleteTaskUseCase getCompleteTaskUseCase() {
        return new CompleteTaskUseCase(getTaskRepository(), getWorkerRepository());
    }

    public VerifyEmployerUseCase getVerifyEmployerUseCase() {
        return new VerifyEmployerUseCase(getEmployerRepository());
    }

    public CreateShippingTemplateUseCase getCreateShippingTemplateUseCase() {
        return new CreateShippingTemplateUseCase(getOrderRepository());
    }

    public CreateDiscountRuleUseCase getCreateDiscountRuleUseCase() {
        return new CreateDiscountRuleUseCase(getOrderRepository());
    }

    public CreateZoneUseCase getCreateZoneUseCase() {
        return new CreateZoneUseCase(getZoneRepository());
    }

    public ScanContentUseCase getScanContentUseCase() {
        return new ScanContentUseCase(getSensitiveWordRepository());
    }

    public EnforceViolationUseCase getEnforceViolationUseCase() {
        return new EnforceViolationUseCase(getEmployerRepository(), getAuditLogRepository());
    }

    public FileReportUseCase getFileReportUseCase() {
        return new FileReportUseCase(getReportRepository(), getEmployerRepository(), getOrderRepository(), getTaskRepository());
    }

    public OpenCaseUseCase getOpenCaseUseCase() {
        return new OpenCaseUseCase(getComplianceCaseRepository(), getAuditLogRepository(), getEmployerRepository());
    }
}
