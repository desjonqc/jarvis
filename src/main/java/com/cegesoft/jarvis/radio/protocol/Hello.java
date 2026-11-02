package com.cegesoft.jarvis.radio.protocol;

/**
 * HELLO payload : module type u8, firmware version u8, capabilities u8 [, relay id u8 appended by a relay].
 */
public record Hello(int type, int firmware, int capabilities, int via) {

    public static final int CAP_MAINS_POWERED = 1;
    public static final int CAP_SLEEPS = 1 << 1;
    public static final int CAP_BATTERY = 1 << 2;
    /**
     * Bits 3-4 : battery pack (0 none or unknown, 1 = 2 x CR2032 in series, 2 = 2 x CR2032 in parallel).
     */
    public static final int CAP_PACK_SHIFT = 3;
    public static final int PACK_CR2032_X2_SERIES = 1;
    public static final int PACK_CR2032_X2_PARALLEL = 2;

    public boolean sleeps() {
        return (capabilities & CAP_SLEEPS) != 0;
    }

    public boolean hasBattery() {
        return (capabilities & CAP_BATTERY) != 0;
    }

    public int batteryPack() {
        return (capabilities >> CAP_PACK_SHIFT) & 0x03;
    }

    public boolean mainsPowered() {
        return (capabilities & CAP_MAINS_POWERED) != 0;
    }

    public static Hello read(Frame frame) {
        PayloadReader reader = frame.reader();
        int type = reader.u8();
        int firmware = reader.u8();
        int capabilities = reader.u8();
        int via = reader.hasRemaining() ? reader.u8() : 0;
        return new Hello(type, firmware, capabilities, via);
    }

    public byte[] encode() {
        return via == 0
                ? new byte[]{Payloads.u8(type), Payloads.u8(firmware), Payloads.u8(capabilities)}
                : new byte[]{Payloads.u8(type), Payloads.u8(firmware), Payloads.u8(capabilities), Payloads.u8(via)};
    }
}
