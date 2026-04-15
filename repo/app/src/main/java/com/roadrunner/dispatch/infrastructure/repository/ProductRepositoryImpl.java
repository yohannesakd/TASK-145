package com.roadrunner.dispatch.infrastructure.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import com.roadrunner.dispatch.core.domain.model.Product;
import com.roadrunner.dispatch.core.domain.repository.ProductRepository;
import com.roadrunner.dispatch.infrastructure.db.dao.ProductDao;
import com.roadrunner.dispatch.infrastructure.db.entity.ProductEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ProductRepositoryImpl implements ProductRepository {
    private final ProductDao productDao;

    public ProductRepositoryImpl(ProductDao productDao) {
        this.productDao = productDao;
    }

    @Override
    public LiveData<List<Product>> getActiveProducts(String orgId) {
        return Transformations.map(productDao.getActiveProducts(orgId), this::toDomainList);
    }

    @Override
    public List<Product> getActiveProductsSync(String orgId) {
        return toDomainList(productDao.getActiveProductsSync(orgId));
    }

    @Override
    public LiveData<List<Product>> searchProducts(String orgId, String query) {
        return Transformations.map(productDao.searchActiveProducts(orgId, query), this::toDomainList);
    }

    @Override
    public Product getByIdScoped(String id, String orgId) {
        ProductEntity e = productDao.findByIdAndOrg(id, orgId);
        return e == null ? null : toDomain(e);
    }

    @Override
    public void insert(Product p) {
        ProductEntity e = new ProductEntity();
        e.id = p.id != null ? p.id : UUID.randomUUID().toString();
        e.orgId = p.orgId;
        e.name = p.name;
        e.brand = p.brand;
        e.series = p.series;
        e.model = p.model;
        e.description = p.description;
        e.unitPriceCents = p.unitPriceCents;
        e.taxRate = p.taxRate;
        e.regulated = p.regulated;
        e.status = p.status;
        e.imageUri = p.imageUri;
        e.createdAt = System.currentTimeMillis();
        e.updatedAt = e.createdAt;
        productDao.insert(e);
    }

    @Override
    public void update(Product p) {
        ProductEntity e = productDao.findByIdAndOrg(p.id, p.orgId);
        if (e == null) return;
        e.name = p.name;
        e.brand = p.brand;
        e.series = p.series;
        e.model = p.model;
        e.description = p.description;
        e.unitPriceCents = p.unitPriceCents;
        e.taxRate = p.taxRate;
        e.regulated = p.regulated;
        e.status = p.status;
        e.imageUri = p.imageUri;
        e.updatedAt = System.currentTimeMillis();
        productDao.update(e);
    }

    private Product toDomain(ProductEntity e) {
        return new Product(e.id, e.orgId, e.name, e.brand, e.series, e.model,
                e.description, e.unitPriceCents, e.taxRate, e.regulated, e.status, e.imageUri);
    }

    private List<Product> toDomainList(List<ProductEntity> entities) {
        List<Product> result = new ArrayList<>(entities.size());
        for (ProductEntity e : entities) result.add(toDomain(e));
        return result;
    }
}
