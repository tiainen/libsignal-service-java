package com.gluonhq.snl;

public enum NetworkMode {
    LEGACY,
    GRPC,
    QUIC;

    public static NetworkMode get() {
        if ("true".equalsIgnoreCase(System.getProperty("wave.quic", "true"))) {
            return QUIC;
        } else if ("true".equalsIgnoreCase(System.getProperty("wave.grpc", "false"))) {
            return GRPC;
        } else {
            return LEGACY;
        }
    }
}