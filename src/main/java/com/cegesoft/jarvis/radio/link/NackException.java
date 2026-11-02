package com.cegesoft.jarvis.radio.link;

public class NackException extends Exception {

    public static final int UNKNOWN_OPERATION = 1;
    public static final int INVALID_PAYLOAD = 2;
    public static final int BUSY = 3;
    public static final int UNREACHABLE = 4;

    private final int code;

    public NackException(int code) {
        super("Request refused by the node (NACK " + code + ")");
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
