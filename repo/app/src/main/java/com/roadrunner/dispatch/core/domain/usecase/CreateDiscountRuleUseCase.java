package com.roadrunner.dispatch.core.domain.usecase;

import com.roadrunner.dispatch.core.domain.model.DiscountRule;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.repository.OrderRepository;

import java.util.ArrayList;
import java.util.List;

public class CreateDiscountRuleUseCase {

    private final OrderRepository orderRepository;

    public CreateDiscountRuleUseCase(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * @param rule      Discount rule data to create
     * @param actorRole Role of the actor; must be "ADMIN"
     */
    public Result<DiscountRule> execute(DiscountRule rule, String actorRole) {
        if (!"ADMIN".equals(actorRole)) {
            return Result.failure("Unauthorized: only admins can create discount rules");
        }

        List<String> errors = new ArrayList<>();

        if (rule.name == null || rule.name.trim().isEmpty()) {
            errors.add("Name is required");
        }
        if (!"PERCENT_OFF".equals(rule.type) && !"FLAT_OFF".equals(rule.type)) {
            errors.add("Type must be PERCENT_OFF or FLAT_OFF");
        }
        if (rule.value < 0.0) {
            errors.add("Value must not be negative");
        }
        if ("PERCENT_OFF".equals(rule.type) && rule.value > 100.0) {
            errors.add("Percentage discount must not exceed 100");
        }
        if (!"ACTIVE".equals(rule.status) && !"EXPIRED".equals(rule.status)
                && !"DISABLED".equals(rule.status)) {
            errors.add("Status must be ACTIVE, EXPIRED, or DISABLED");
        }

        if (!errors.isEmpty()) return Result.failure(errors);

        orderRepository.insertDiscountRule(rule);
        return Result.success(rule);
    }
}
