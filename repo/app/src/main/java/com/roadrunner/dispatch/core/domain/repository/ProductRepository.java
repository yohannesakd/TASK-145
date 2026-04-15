package com.roadrunner.dispatch.core.domain.repository;

import androidx.lifecycle.LiveData;
import com.roadrunner.dispatch.core.domain.model.Product;
import java.util.List;

public interface ProductRepository {
    LiveData<List<Product>> getActiveProducts(String orgId);
    List<Product> getActiveProductsSync(String orgId);
    LiveData<List<Product>> searchProducts(String orgId, String query);
    Product getByIdScoped(String id, String orgId);
    void insert(Product product);
    void update(Product product);
}
