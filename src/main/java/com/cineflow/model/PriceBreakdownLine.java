package com.cineflow.model;

import java.math.BigDecimal;

/**
 * Represents an individual rule evaluation line item within a PriceQuote breakdown.
 */
public class PriceBreakdownLine {

    private final String ruleName;
    private final PricingRuleType ruleType;
    private final BigDecimal adjustmentPct;
    private final BigDecimal amountDelta;
    private final String explanation;

    public PriceBreakdownLine(String ruleName, PricingRuleType ruleType, BigDecimal adjustmentPct,
                              BigDecimal amountDelta, String explanation) {
        this.ruleName = ruleName;
        this.ruleType = ruleType;
        this.adjustmentPct = adjustmentPct;
        this.amountDelta = amountDelta;
        this.explanation = explanation;
    }

    public String getRuleName() { return ruleName; }
    public PricingRuleType getRuleType() { return ruleType; }
    public BigDecimal getAdjustmentPct() { return adjustmentPct; }
    public BigDecimal getAmountDelta() { return amountDelta; }
    public String getExplanation() { return explanation; }

    @Override
    public String toString() {
        String sign = adjustmentPct.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
        return String.format("%s (%s%s%%): ₹%s — %s", ruleName, sign, adjustmentPct.toPlainString(),
                amountDelta.toPlainString(), explanation);
    }
}
