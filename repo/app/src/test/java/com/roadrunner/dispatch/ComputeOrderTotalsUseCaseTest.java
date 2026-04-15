package com.roadrunner.dispatch;

import com.roadrunner.dispatch.core.domain.model.DiscountRule;
import com.roadrunner.dispatch.core.domain.model.OrderItem;
import com.roadrunner.dispatch.core.domain.model.OrderTotals;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.model.ShippingTemplate;
import com.roadrunner.dispatch.core.domain.usecase.ComputeOrderTotalsUseCase;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class ComputeOrderTotalsUseCaseTest {

    private ComputeOrderTotalsUseCase useCase;

    @Before
    public void setUp() {
        useCase = new ComputeOrderTotalsUseCase();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static OrderItem item(long unitPriceCents, int qty, double taxRate) {
        return new OrderItem("i1", "o1", "p1", "Product", qty, unitPriceCents,
                unitPriceCents * qty, taxRate, false);
    }

    private static OrderItem item(String id, long unitPriceCents, int qty, double taxRate) {
        return new OrderItem(id, "o1", id, "Product " + id, qty, unitPriceCents,
                unitPriceCents * qty, taxRate, false);
    }

    private static DiscountRule percent(double value) {
        return new DiscountRule("d1", "org1", "pct", "PERCENT_OFF", value, "ACTIVE");
    }

    private static DiscountRule flat(double valueCents) {
        return new DiscountRule("d2", "org1", "flat", "FLAT_OFF", valueCents, "ACTIVE");
    }

    private static ShippingTemplate shipping(long costCents) {
        return new ShippingTemplate("s1", "org1", "Standard", "", costCents, 3, 7, false);
    }

    // -----------------------------------------------------------------------
    // Empty items
    // -----------------------------------------------------------------------

    @Test
    public void emptyItems_failure() {
        Result<OrderTotals> result = useCase.execute(Collections.emptyList(), null, null);
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("no items"));
    }

    @Test
    public void nullItems_failure() {
        Result<OrderTotals> result = useCase.execute(null, null, null);
        assertFalse(result.isSuccess());
    }

    // -----------------------------------------------------------------------
    // Single item, no discount, no shipping
    // -----------------------------------------------------------------------

    @Test
    public void singleItem_noDiscount_noShipping_correctSubtotalAndTax() {
        // $10.00 item, qty 1, 10% tax
        OrderItem item = item(1000L, 1, 0.10);
        Result<OrderTotals> result = useCase.execute(
                Collections.singletonList(item), Collections.emptyList(), null);
        assertTrue(result.isSuccess());
        OrderTotals t = result.getData();
        assertEquals(1000L, t.subtotalCents);      // $10.00 subtotal
        assertEquals(0L, t.discountCents);
        assertEquals(100L, t.taxCents);            // 10% of $10.00 = $1.00
        assertEquals(0L, t.shippingCents);
        assertEquals(1100L, t.totalCents);         // $11.00
    }

    @Test
    public void singleItem_noDiscount_noShipping_totalCorrect() {
        OrderItem item = item(500L, 2, 0.08);
        Result<OrderTotals> result = useCase.execute(
                Collections.singletonList(item), Collections.emptyList(), null);
        assertTrue(result.isSuccess());
        OrderTotals t = result.getData();
        assertEquals(1000L, t.subtotalCents);
        assertEquals(80L, t.taxCents);
        assertEquals(1080L, t.totalCents);
    }

    // -----------------------------------------------------------------------
    // Multiple items with different tax rates
    // -----------------------------------------------------------------------

    @Test
    public void multipleItems_differentTaxRates_summedCorrectly() {
        // Item A: $20 qty 1, 5% tax  → tax = $1.00
        // Item B: $10 qty 2, 10% tax → tax = $2.00
        OrderItem a = item("a", 2000L, 1, 0.05);
        OrderItem b = item("b", 1000L, 2, 0.10);
        Result<OrderTotals> result = useCase.execute(
                Arrays.asList(a, b), Collections.emptyList(), null);
        assertTrue(result.isSuccess());
        OrderTotals t = result.getData();
        assertEquals(4000L, t.subtotalCents);     // $40.00
        assertEquals(0L, t.discountCents);
        // tax = round(2000 * 0.05) + round(2000 * 0.10) = 100 + 200 = 300
        assertEquals(300L, t.taxCents);
        assertEquals(4300L, t.totalCents);
    }

    // -----------------------------------------------------------------------
    // Percent discount applied before tax
    // -----------------------------------------------------------------------

    @Test
    public void percentDiscount_taxComputedOnPreDiscountSubtotal() {
        // $100.00 item, 10% discount → discounted subtotal = $90
        // Tax at 10% on ORIGINAL $100 = $10 (discounts never reduce tax)
        OrderItem item = item(10000L, 1, 0.10);
        Result<OrderTotals> result = useCase.execute(
                Collections.singletonList(item),
                Collections.singletonList(percent(10.0)),
                null);
        assertTrue(result.isSuccess());
        OrderTotals t = result.getData();
        assertEquals(10000L, t.subtotalCents);
        assertEquals(1000L, t.discountCents);      // 10% of $100
        assertEquals(1000L, t.taxCents);           // 10% of $100 (pre-discount)
        assertEquals(10000L, t.totalCents);        // $90 + $10
    }

    // -----------------------------------------------------------------------
    // Flat + percent discount
    // -----------------------------------------------------------------------

    @Test
    public void flatAndPercentDiscount_combined() {
        // $100.00 item, 10% off + $5 flat off
        // percent discount = $10, flat = $5, total discount = $15
        // discounted subtotal = $85
        // Tax at 8% on ORIGINAL $100 = $8.00 (discounts never reduce tax)
        OrderItem item = item(10000L, 1, 0.08);
        Result<OrderTotals> result = useCase.execute(
                Collections.singletonList(item),
                Arrays.asList(percent(10.0), flat(500.0)),
                null);
        assertTrue(result.isSuccess());
        OrderTotals t = result.getData();
        assertEquals(10000L, t.subtotalCents);
        assertEquals(1500L, t.discountCents);
        assertEquals(800L, t.taxCents);            // 8% of $100 (pre-discount)
        assertEquals(9300L, t.totalCents);         // $85 + $8
    }

    // -----------------------------------------------------------------------
    // Discount capped at subtotal (no negative totals)
    // -----------------------------------------------------------------------

    @Test
    public void discountExceedsSubtotal_cappedAtSubtotal() {
        // $10.00 item, flat discount of $20 → discount capped at $10
        OrderItem item = item(1000L, 1, 0.10);
        Result<OrderTotals> result = useCase.execute(
                Collections.singletonList(item),
                Collections.singletonList(flat(2000.0)),
                null);
        assertTrue(result.isSuccess());
        OrderTotals t = result.getData();
        assertEquals(1000L, t.subtotalCents);
        assertEquals(1000L, t.discountCents);       // capped at subtotal
        assertEquals(100L, t.taxCents);             // 10% of ORIGINAL $10 (pre-discount)
        assertEquals(100L, t.totalCents);           // $0 discounted subtotal + $1 tax
    }

    // -----------------------------------------------------------------------
    // Shipping added to total
    // -----------------------------------------------------------------------

    @Test
    public void shippingAddedToTotal() {
        // $50 item, no tax, $10 shipping
        OrderItem item = item(5000L, 1, 0.0);
        Result<OrderTotals> result = useCase.execute(
                Collections.singletonList(item), Collections.emptyList(), shipping(1000L));
        assertTrue(result.isSuccess());
        OrderTotals t = result.getData();
        assertEquals(1000L, t.shippingCents);
        assertEquals(6000L, t.totalCents);
    }

    @Test
    public void nullShipping_treatedAsZero() {
        OrderItem item = item(1000L, 1, 0.0);
        Result<OrderTotals> result = useCase.execute(
                Collections.singletonList(item), Collections.emptyList(), null);
        assertTrue(result.isSuccess());
        assertEquals(0L, result.getData().shippingCents);
    }

    // -----------------------------------------------------------------------
    // Totals verified for fractional tax rates
    // -----------------------------------------------------------------------

    @Test
    public void fractionalTaxRate_totalCorrect() {
        OrderItem item = item(1234L, 3, 0.07);
        Result<OrderTotals> result = useCase.execute(
                Collections.singletonList(item), Collections.emptyList(), null);
        assertTrue(result.isSuccess());
        OrderTotals t = result.getData();
        assertEquals(3702L, t.subtotalCents);
        assertEquals(259L, t.taxCents);  // round(3702 * 0.07) = 259
        assertEquals(3961L, t.totalCents);
    }

    // -----------------------------------------------------------------------
    // Tax computed on pre-discount amounts (discounts never reduce tax)
    // -----------------------------------------------------------------------

    @Test
    public void taxComputedOnPreDiscountAmount_notReduced() {
        // $200 item, 50% percent off → discounted subtotal = $100
        // Tax at 10% on ORIGINAL $200 = $20 (discounts never reduce tax)
        OrderItem item = item(20000L, 1, 0.10);
        Result<OrderTotals> result = useCase.execute(
                Collections.singletonList(item),
                Collections.singletonList(percent(50.0)),
                null);
        assertTrue(result.isSuccess());
        OrderTotals t = result.getData();
        assertEquals(10000L, t.discountCents);
        assertEquals(2000L, t.taxCents);     // $20 on pre-discount $200
    }

    @Test
    public void multipleItems_taxProportionallyAllocated() {
        // Two items of equal value; 20% discount applied proportionally
        // Each item: $100 price, so $10 discount each (20% of $100)
        // discounted each = $90, tax at 0% — just verifies structural consistency
        OrderItem a = item("a", 10000L, 1, 0.0);
        OrderItem b = item("b", 10000L, 1, 0.0);
        Result<OrderTotals> result = useCase.execute(
                Arrays.asList(a, b),
                Collections.singletonList(percent(20.0)),
                null);
        assertTrue(result.isSuccess());
        OrderTotals t = result.getData();
        assertEquals(20000L, t.subtotalCents);
        assertEquals(4000L, t.discountCents);  // 20% of $200
        assertEquals(0L, t.taxCents);
        assertEquals(16000L, t.totalCents);
    }
}
