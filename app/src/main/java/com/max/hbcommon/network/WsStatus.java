package com.max.hbcommon.network;

public final class WsStatus {
    public static final WsStatus CONNECTING = new WsStatus("CONNECTING");
    public static final WsStatus CONNECT_SUCCESS = new WsStatus("CONNECT_SUCCESS");
    public static final WsStatus CONNECT_FAIL = new WsStatus("CONNECT_FAIL");

    private final String name;

    private WsStatus(String name) {
        this.name = name;
    }

    private WsStatus() {
        this.name = "";
    }

    @Override public String toString() {
        return name == null ? "" : name;
    }
}
