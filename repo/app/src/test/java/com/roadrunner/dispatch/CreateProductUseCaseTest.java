package com.roadrunner.dispatch;

import androidx.lifecycle.LiveData;

import com.roadrunner.dispatch.core.domain.model.Product;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.repository.ProductRepository;
import com.roadrunner.dispatch.core.domain.usecase.CreateProductUseCase;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class CreateProductUseCaseTest {

    private StubProductRepository productRepo;
    private CreateProductUseCase useCase;

    @Before
    public void setUp() {
        productRepo = new StubProductRepository();
        useCase = new CreateProductUseCase(productRepo);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Product validProduct() {
        return new Product("p1", "org1", "Dispatch Vest", "BrandA", "SeriesX", "ModelZ",
                "High visibility vest", 2999L, 0.08, false, "ACTIVE", null);
    }

    // -----------------------------------------------------------------------
    // Role checks
    // -----------------------------------------------------------------------

    @Test
    public void workerRole_rejected() {
        Result<Product> result = useCase.execute(validProduct(), "WORKER");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("unauthorized")
                || result.getFirstError().toLowerCase().contains("admin"));
    }

    @Test
    public void dispatcherRole_rejected() {
        Result<Product> result = useCase.execute(validProduct(), "DISPATCHER");
        assertFalse(result.isSuccess());
    }

    @Test
    public void complianceRole_rejected() {
        Result<Product> result = useCase.execute(validProduct(), "COMPLIANCE_REVIEWER");
        assertFalse(result.isSuccess());
    }

    // -----------------------------------------------------------------------
    // Happy path
    // -----------------------------------------------------------------------

    @Test
    public void adminRole_success() {
        Product product = validProduct();
        Result<Product> result = useCase.execute(product, "ADMIN");
        assertTrue(result.isSuccess());
        assertEquals(1, productRepo.inserted.size());
        assertSame(product, productRepo.inserted.get(0));
    }

    // -----------------------------------------------------------------------
    // Validation
    // -----------------------------------------------------------------------

    @Test
    public void blankName_failure() {
        Product product = new Product("p1", "org1", "", "B", "S", "M", "desc", 100L, 0.0, false, "ACTIVE", null);
        Result<Product> result = useCase.execute(product, "ADMIN");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("name"));
    }

    @Test
    public void nullName_failure() {
        Product product = new Product("p1", "org1", null, "B", "S", "M", "desc", 100L, 0.0, false, "ACTIVE", null);
        Result<Product> result = useCase.execute(product, "ADMIN");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("name"));
    }

    @Test
    public void negativePrice_failure() {
        Product product = new Product("p1", "org1", "Vest", "B", "S", "M", "desc", -100L, 0.0, false, "ACTIVE", null);
        Result<Product> result = useCase.execute(product, "ADMIN");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("price"));
    }

    @Test
    public void taxRateAboveOne_failure() {
        Product product = new Product("p1", "org1", "Vest", "B", "S", "M", "desc", 100L, 1.5, false, "ACTIVE", null);
        Result<Product> result = useCase.execute(product, "ADMIN");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("tax"));
    }

    @Test
    public void negativeTaxRate_failure() {
        Product product = new Product("p1", "org1", "Vest", "B", "S", "M", "desc", 100L, -0.1, false, "ACTIVE", null);
        Result<Product> result = useCase.execute(product, "ADMIN");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("tax"));
    }

    @Test
    public void invalidStatus_failure() {
        Product product = new Product("p1", "org1", "Vest", "B", "S", "M", "desc", 100L, 0.08, false, "UNKNOWN", null);
        Result<Product> result = useCase.execute(product, "ADMIN");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("status"));
    }

    @Test
    public void nullStatus_failure() {
        Product product = new Product("p1", "org1", "Vest", "B", "S", "M", "desc", 100L, 0.08, false, null, null);
        Result<Product> result = useCase.execute(product, "ADMIN");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("status"));
    }

    @Test
    public void regulatedProduct_success() {
        Product product = new Product("p2", "org1", "Chemical Kit", "ChemCo", "", "", "Regulated item",
                4999L, 0.10, true, "ACTIVE", "content://images/chem.png");
        Result<Product> result = useCase.execute(product, "ADMIN");
        assertTrue(result.isSuccess());
        assertTrue(productRepo.inserted.get(0).regulated);
    }

    // -----------------------------------------------------------------------
    // Stub implementations
    // -----------------------------------------------------------------------

    private static class StubProductRepository implements ProductRepository {
        final List<Product> inserted = new ArrayList<>();

        @Override public void insert(Product product) { inserted.add(product); }
        @Override public void update(Product product) {}
        @Override public LiveData<List<Product>> getActiveProducts(String orgId) { return null; }
        @Override public List<Product> getActiveProductsSync(String orgId) { return Collections.emptyList(); }
        @Override public LiveData<List<Product>> searchProducts(String orgId, String query) { return null; }
        @Override public Product getByIdScoped(String id, String orgId) { return null; }
    }
}
