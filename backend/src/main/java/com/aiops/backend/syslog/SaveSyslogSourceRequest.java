package com.aiops.backend.syslog;

import com.aiops.backend.device.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SaveSyslogSourceRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 120) String expectedHost,
        @NotBlank @Size(max = 120) String deviceId,
        @NotBlank @Size(max = 120) String deviceName,
        @NotNull DeviceType deviceType,
        @NotBlank @Size(max = 160) String location,
        @NotNull SyslogParserProfile parserProfile,
        Boolean enabled
) {
}
