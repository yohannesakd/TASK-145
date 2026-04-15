package com.roadrunner.dispatch.core.domain.usecase;

import com.roadrunner.dispatch.core.domain.model.DiscountRule;
import com.roadrunner.dispatch.core.domain.model.Result;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates stacked discounts:
 * - Max 3 discounts per order
 * - Cumulative percent-off cannot exceed 40%
 * - Discounts never apply to sales tax
 */
public class ValidateDiscountsUseCase {
    public static final int MAX_DISCOUNTS = 3;
    public static final double MAX_PERCENT_OFF = 40.0;

    public Result<List<DiscountRule>> execute(List<DiscountRule> discounts) {
        List<String> errors = new ArrayList<>();

        if (discounts.size() > MAX_DISCOUNTS) {
            errors.add("Maximum " + MAX_DISCOUNTS + " discounts per order. You have " + discounts.size() + ".");
        }

        double totalPercent = 0;
        for (DiscountRule rule : discounts) {
            if ("PERCENT_OFF".equals(rule.type)) {
                totalPercent += rule.value;
            }
        }

        if (totalPercent > MAX_PERCENT_OFF) {
            errors.add("Total discount cannot exceed " + (int) MAX_PERCENT_OFF + "%. Current total: " + String.format("%.1f", totalPercent) + "%.");
        }

        if (!errors.isEmpty()) {
            return Result.failure(errors);
        }

        return Result.success(discounts);
    }
}
