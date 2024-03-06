package com.cegesoft.jarvis.events.type;

import com.cegesoft.jarvis.events.Event;
import com.cegesoft.jarvis.events.HandlerList;

public class SerialReceiveEvent extends Event {

    private static final HandlerList handlers = new HandlerList();
    private final byte[] data;
    private final String port;

    public SerialReceiveEvent(byte[] data, String port) {
        this.data = data;
        this.port = port;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    public byte[] getData() {
        return data;
    }

    public String getPort() {
        return port;
    }
}
