package com.cegesoft.jarvis.radio.protocol;

/**
 * Operation codes of the JARVIS-RF v1 protocol (see docs/protocol.md).
 */
public enum Opcode {
    HELLO(0x01),
    WELCOME(0x02),
    STATUS(0x03),
    STATUS_REQ(0x04),
    PING(0x05),
    PONG(0x06),
    DISCOVER(0x07),
    SET_ENABLED(0x10),
    SET_STATE(0x11),
    PULSE(0x12),
    STATE(0x13),
    SWITCH(0x20),
    ANALOG(0x21),
    PULSE_EVT(0x22),
    NACK(0x7E),
    ACK(0x7F);

    private static final Opcode[] BY_CODE = new Opcode[256];

    static {
        for (Opcode opcode : values())
            BY_CODE[opcode.code] = opcode;
    }

    private final int code;

    Opcode(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    /**
     * @return the opcode, or null if the code is unknown
     */
    public static Opcode fromCode(int code) {
        return code >= 0 && code < 256 ? BY_CODE[code] : null;
    }
}
