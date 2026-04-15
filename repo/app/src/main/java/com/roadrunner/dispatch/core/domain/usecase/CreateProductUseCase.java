package com.roadrunner.dispatch.core.domain.usecase;

import com.roadrunner.dispatch.core.domain.model.Product;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.repository.ProductRepository;

import java.util.ArrayList;
import java.util.List;

public class CreateProductUseCase {

    private final ProductRepository productRepository;

    public CreateProductUseCase(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * @param product   Product data to create
     * @param actorRole Role of the actor; must be "ADMIN"
     */
    public Result<Product> execute(Product product, String actorRole) {
        if (!"ADMIN".equals(actorRole)) {
            return Result.failure("Unauthorized: only admins can create products");
        }

        List<String> errors = new ArrayList<>();

        if (product.name == null || product.name.trim().isEmpty()) {
            errors.add("Name is required");
        }
        if (product.unitPriceCents < 0) {
            errors.add("Unit price must not be negative");
        }
        if (product.taxRate < 0.0 || product.taxRate > 1.0) {
            errors.add("Tax rate must be between 0.0 and 1.0");
        }
        if (product.status == null ||
                (!"ACTIVE".equals(product.status) && !"INACTIVE".equals(product.status)
                        && !"DISCONTINUED".equals(product.status))) {
            errors.add("Status must be ACTIVE, INACTIVE, or DISCONTINUED");
        }

        if (!errors.isEmpty()) return Result.failure(errors);

        productRepository.insert(product);
        return Result.success(product);
    }
}
