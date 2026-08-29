package com.aiops.backend.metric;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SaveMetricThresholdRequest(
        @NotBlank String metricName,
        @NotNull ThresholdOperator operator,
        @NotNull Double warningValue,
        @NotNull Double criticalValue,
        Boolean enabled
) {

    @AssertTrue(message = "For GREATER_THAN thresholds, criticalValue must be greater than or equal to warningValue")
    public boolean isGreaterThanConfigurationValid() {
        return operator != ThresholdOperator.GREATER_THAN
                || warningValue == null
                || criticalValue == null
                || criticalValue >= warningValue;
    }

    @AssertTrue(message = "For LESS_THAN thresholds, criticalValue must be less than or equal to warningValue")
    public boolean isLessThanConfigurationValid() {
        return operator != ThresholdOperator.LESS_THAN
                || warningValue == null
                || criticalValue == null
                || criticalValue <= warningValue;
    }
}
