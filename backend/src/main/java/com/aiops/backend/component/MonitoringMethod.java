package com.aiops.backend.component;

public enum MonitoringMethod {
    PING,
    HTTP_HEALTH,
    TCP_PORT,
    SNMP_BASIC,
    SNMP_ROUTER_METRICS,
    SNMP_SERVER_METRICS,
    RTSP_HEALTH,
    UPS_HTTP_METRICS,
    UPS_SNMP_METRICS
}
