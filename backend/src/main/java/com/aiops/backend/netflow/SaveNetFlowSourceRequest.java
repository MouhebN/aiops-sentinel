package com.aiops.backend.netflow;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SaveNetFlowSourceRequest(
        @NotBlank String name,
        NetFlowProviderType providerType,
        @NotBlank String dataDirectory,
        @NotNull @Min(1) @Max(65535) Integer collectorPort,
        Boolean enabled
) {
}
