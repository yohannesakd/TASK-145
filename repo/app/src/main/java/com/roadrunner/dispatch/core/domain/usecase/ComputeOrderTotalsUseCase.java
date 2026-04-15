package com.roadrunner.dispatch.core.domain.usecase;

import com.roadrunner.dispatch.core.domain.model.DiscountRule;
import com.roadrunner.dispatch.core.domain.model.OrderItem;
import com.roadrunner.dispatch.core.domain.model.OrderTotals;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.model.ShippingTemplate;
import java.util.List;

public class ComputeOrderTotalsUseCase {

    public Result<OrderTotals> execute(List<OrderItem> items, List<DiscountRule> discounts,
                                        ShippingTemplate shippingTemplate) {
        if (items == null || items.isEmpty()) {
            return Result.failure("No items in order");
        }

        // Step 1: Compute subtotal (sum of line totals)
        long subtotalCents = 0;
        for (OrderItem item : items) {
            subtotalCents += item.unitPriceCents * item.quantity;
        }

        // Step 2: Compute total discount amount
        long discountCents = 0;
        double totalPercentOff = 0;
        long totalFlatOff = 0;

        if (discounts != null) {
            for (DiscountRule rule : discounts) {
                if ("PERCENT_OFF".equals(rule.type)) {
                    totalPercentOff += rule.value;
                } else if ("FLAT_OFF".equals(rule.type)) {
                    totalFlatOff += (long) rule.value; // value is in cents for FLAT_OFF
                }
            }
        }

        // Apply percent discount to subtotal
        long percentDiscountCents = Math.round(subtotalCents * totalPercentOff / 100.0);
        discountCents = percentDiscountCents + totalFlatOff;

        // Don't let discount exceed subtotal
        if (discountCents > subtotalCents) {
            discountCents = subtotalCents;
        }

        long discountedSubtotal = subtotalCents - discountCents;

        // Step 3: Compute tax on ORIGINAL (pre-discount) line items.
        // Per requirement, discounts never reduce the tax amount — tax is always
        // calculated on the full subtotal before any discounts are applied.
        long taxCents = 0;
        for (OrderItem item : items) {
            long itemTotal = item.unitPriceCents * item.quantity;
            long itemTax = Math.round(itemTotal * item.taxRate);
            taxCents += itemTax;
        }

        // Step 4: Shipping
        long shippingCents = shippingTemplate != null ? shippingTemplate.costCents : 0;

        // Step 5: Total
        long totalCents = discountedSubtotal + taxCents + shippingCents;

        OrderTotals totals = new OrderTotals(
            subtotalCents, discountCents, taxCents, shippingCents, totalCents
        );

        return Result.success(totals);
    }
}
