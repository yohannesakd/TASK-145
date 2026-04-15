package com.roadrunner.dispatch;

import com.roadrunner.dispatch.core.domain.model.DiscountRule;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.usecase.ValidateDiscountsUseCase;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class ValidateDiscountsUseCaseTest {

    private ValidateDiscountsUseCase useCase;

    @Before
    public void setUp() {
        useCase = new ValidateDiscountsUseCase();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static DiscountRule percent(double value) {
        return new DiscountRule("id", "org1", "pct", "PERCENT_OFF", value, "ACTIVE");
    }

    private static DiscountRule flat(double valueCents) {
        return new DiscountRule("id", "org1", "flat", "FLAT_OFF", valueCents, "ACTIVE");
    }

    // -----------------------------------------------------------------------
    // Count validation
    // -----------------------------------------------------------------------

    @Test
    public void zeroDiscounts_success() {
        Result<List<DiscountRule>> result = useCase.execute(Collections.emptyList());
        assertTrue(result.isSuccess());
    }

    @Test
    public void oneDiscount_success() {
        Result<List<DiscountRule>> result = useCase.execute(
                Collections.singletonList(percent(10)));
        assertTrue(result.isSuccess());
    }

    @Test
    public void twoDiscounts_success() {
        Result<List<DiscountRule>> result = useCase.execute(
                Arrays.asList(percent(10), percent(15)));
        assertTrue(result.isSuccess());
    }

    @Test
    public void threeDiscounts_success() {
        Result<List<DiscountRule>> result = useCase.execute(
                Arrays.asList(percent(10), percent(10), percent(10)));
        assertTrue(result.isSuccess());
    }

    @Test
    public void fourDiscounts_failure_maximumMessage() {
        List<DiscountRule> discounts = Arrays.asList(percent(5), percent(5), percent(5), percent(5));
        Result<List<DiscountRule>> result = useCase.execute(discounts);
        assertFalse(result.isSuccess());
        assertTrue("Expected 'Maximum 3' in error",
                result.getErrors().stream().anyMatch(e -> e.contains("Maximum 3")));
    }

    @Test
    public void tenDiscounts_failure_mentionsCount() {
        List<DiscountRule> discounts = new ArrayList<>();
        for (int i = 0; i < 10; i++) discounts.add(percent(1));
        Result<List<DiscountRule>> result = useCase.execute(discounts);
        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("10")));
    }

    // -----------------------------------------------------------------------
    // Percent-cap validation
    // -----------------------------------------------------------------------

    @Test
    public void tenPlusFifteenPlusFourteen_exactlyUnder40_success() {
        // 10 + 15 + 14 = 39 %
        Result<List<DiscountRule>> result = useCase.execute(
                Arrays.asList(percent(10), percent(15), percent(14)));
        assertTrue(result.isSuccess());
    }

    @Test
    public void exactly40Percent_success() {
        // 10 + 15 + 15 = 40 %
        Result<List<DiscountRule>> result = useCase.execute(
                Arrays.asList(percent(10), percent(15), percent(15)));
        assertTrue(result.isSuccess());
    }

    @Test
    public void tenPlusFifteenPlusSixteen_over40_failure() {
        // 10 + 15 + 16 = 41 %
        Result<List<DiscountRule>> result = useCase.execute(
                Arrays.asList(percent(10), percent(15), percent(16)));
        assertFalse(result.isSuccess());
        assertTrue("Expected 'cannot exceed 40%' in error",
                result.getErrors().stream().anyMatch(e -> e.contains("40%") || e.contains("40")));
    }

    @Test
    public void over40Percent_errorMentionsTotalPercent() {
        // 30 + 20 = 50 %
        Result<List<DiscountRule>> result = useCase.execute(
                Arrays.asList(percent(30), percent(20)));
        assertFalse(result.isSuccess());
        // The error message includes the actual total "50.0%"
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("50.0")));
    }

    // -----------------------------------------------------------------------
    // Flat discounts don't count toward percent cap
    // -----------------------------------------------------------------------

    @Test
    public void flatDiscounts_dontCountTowardPercentCap() {
        // Three flat discounts of $100 each — total percent is still 0
        Result<List<DiscountRule>> result = useCase.execute(
                Arrays.asList(flat(10000), flat(10000), flat(10000)));
        assertTrue("Flat discounts should not trigger the 40% cap", result.isSuccess());
    }

    @Test
    public void mixedPercentAndFlat_withinLimits_success() {
        // 20 % + two flat discounts — well under 40 %, only 2 percent discounts counted
        Result<List<DiscountRule>> result = useCase.execute(
                Arrays.asList(percent(20), flat(500), flat(500)));
        assertTrue(result.isSuccess());
    }

    // -----------------------------------------------------------------------
    // Both errors simultaneously
    // -----------------------------------------------------------------------

    @Test
    public void fourDiscountsAnd41Percent_twoErrors() {
        // 4 discounts AND over 40 % — both error messages expected
        List<DiscountRule> discounts = Arrays.asList(
                percent(15), percent(15), percent(12), percent(5)); // 4 items, 47 %
        Result<List<DiscountRule>> result = useCase.execute(discounts);
        assertFalse(result.isSuccess());
        assertEquals("Expected exactly 2 error messages", 2, result.getErrors().size());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Maximum 3")));
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("40")));
    }
}
