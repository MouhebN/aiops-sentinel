package com.aiops.backend.component;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SaveComponentNetworkInterfaceRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 64) String ipAddress,
        @NotNull NetworkInterfaceRole role,
        Boolean primary
) {
}
