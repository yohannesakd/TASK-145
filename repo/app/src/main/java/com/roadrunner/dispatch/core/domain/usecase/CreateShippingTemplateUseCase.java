package com.roadrunner.dispatch.core.domain.usecase;

import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.model.ShippingTemplate;
import com.roadrunner.dispatch.core.domain.repository.OrderRepository;

import java.util.ArrayList;
import java.util.List;

public class CreateShippingTemplateUseCase {

    private final OrderRepository orderRepository;

    public CreateShippingTemplateUseCase(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * @param template  Shipping template data to create
     * @param actorRole Role of the actor; must be "ADMIN"
     */
    public Result<ShippingTemplate> execute(ShippingTemplate template, String actorRole) {
        if (!"ADMIN".equals(actorRole)) {
            return Result.failure("Unauthorized: only admins can create shipping templates");
        }

        List<String> errors = new ArrayList<>();

        if (template.name == null || template.name.trim().isEmpty()) {
            errors.add("Name is required");
        }
        if (template.costCents < 0) {
            errors.add("Cost must not be negative");
        }
        if (template.minDays < 0) {
            errors.add("Minimum days must not be negative");
        }
        if (template.maxDays < 0) {
            errors.add("Maximum days must not be negative");
        }
        if (template.minDays > template.maxDays) {
            errors.add("Minimum days must not exceed maximum days");
        }

        if (!errors.isEmpty()) return Result.failure(errors);

        orderRepository.insertShippingTemplate(template);
        return Result.success(template);
    }
}
