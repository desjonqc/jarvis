package com.cegesoft.jarvis.radio.protocol;

/**
 * Little-endian payload builders.
 */
public final class Payloads {

    private Payloads() {
    }

    public static byte u8(int value) {
        if (value < 0 || value > 0xFF)
            throw new IllegalArgumentException("u8 out of range : " + value);
        return (byte) value;
    }

    public static byte i8(int value) {
        if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE)
            throw new IllegalArgumentException("i8 out of range : " + value);
        return (byte) value;
    }

    public static byte[] u16(int value) {
        if (value < 0 || value > 0xFFFF)
            throw new IllegalArgumentException("u16 out of range : " + value);
        return new byte[]{(byte) value, (byte) (value >>> 8)};
    }
}
