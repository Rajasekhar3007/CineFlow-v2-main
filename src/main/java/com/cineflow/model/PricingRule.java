package com.cineflow.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity representing an active or inactive dynamic pricing rule.
 */
public class PricingRule {

    private int id;
    private String name;
    private PricingRuleType ruleType;
    private BigDecimal minValue;
    private BigDecimal maxValue;
    private BigDecimal adjustmentPct;
    private int priority;
    private boolean active;
    private String description;
    private LocalDateTime createdAt;

    public PricingRule() {}

    public PricingRule(int id, String name, PricingRuleType ruleType, BigDecimal minValue,
                       BigDecimal maxValue, BigDecimal adjustmentPct, int priority,
                       boolean active, String description) {
        this.id = id;
        this.name = name;
        this.ruleType = ruleType;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.adjustmentPct = adjustmentPct;
        this.priority = priority;
        this.active = active;
        this.description = description;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public PricingRuleType getRuleType() { return ruleType; }
    public void setRuleType(PricingRuleType ruleType) { this.ruleType = ruleType; }

    public BigDecimal getMinValue() { return minValue; }
    public void setMinValue(BigDecimal minValue) { this.minValue = minValue; }

    public BigDecimal getMaxValue() { return maxValue; }
    public void setMaxValue(BigDecimal maxValue) { this.maxValue = maxValue; }

    public BigDecimal getAdjustmentPct() { return adjustmentPct; }
    public void setAdjustmentPct(BigDecimal adjustmentPct) { this.adjustmentPct = adjustmentPct; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
