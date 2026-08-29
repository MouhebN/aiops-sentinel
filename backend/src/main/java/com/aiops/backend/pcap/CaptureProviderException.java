package com.aiops.backend.pcap;

public class CaptureProviderException extends RuntimeException {

    private final PacketCaptureFailureCode code;

    public CaptureProviderException(PacketCaptureFailureCode code, String message) {
        super(message);
        this.code = code;
    }

    public CaptureProviderException(PacketCaptureFailureCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public PacketCaptureFailureCode code() {
        return code;
    }
}
