package com.aiops.backend.component;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ComponentStatusAggregatorTests {

    @Test
    void allUpIsUp() {
        assertThat(ComponentStatusAggregator.aggregate(List.of(ComponentStatus.UP, ComponentStatus.UP)))
                .isEqualTo(ComponentStatus.UP);
    }

    @Test
    void upAndDownIsDegraded() {
        assertThat(ComponentStatusAggregator.aggregate(List.of(ComponentStatus.UP, ComponentStatus.DOWN)))
                .isEqualTo(ComponentStatus.DEGRADED);
    }

    @Test
    void allDownIsDown() {
        assertThat(ComponentStatusAggregator.aggregate(List.of(ComponentStatus.DOWN, ComponentStatus.DOWN)))
                .isEqualTo(ComponentStatus.DOWN);
    }

    @Test
    void warningIsPreservedWhenNoFailure() {
        assertThat(ComponentStatusAggregator.aggregate(List.of(ComponentStatus.UP, ComponentStatus.WARNING)))
                .isEqualTo(ComponentStatus.WARNING);
    }

    @Test
    void downAndDegradedIsDegradedNotDown() {
        assertThat(ComponentStatusAggregator.aggregate(List.of(ComponentStatus.DOWN, ComponentStatus.DEGRADED)))
                .isEqualTo(ComponentStatus.DEGRADED);
    }

    @Test
    void noEnabledChecksIsUnknownNotDown() {
        assertThat(ComponentStatusAggregator.aggregate(List.of())).isEqualTo(ComponentStatus.UNKNOWN);
        assertThat(ComponentStatusAggregator.aggregate(List.of(ComponentStatus.UNKNOWN)))
                .isEqualTo(ComponentStatus.UNKNOWN);
    }
}
