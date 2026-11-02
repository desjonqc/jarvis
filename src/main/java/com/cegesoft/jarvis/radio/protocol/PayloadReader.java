package com.cegesoft.jarvis.radio.protocol;

/**
 * Reads a little-endian payload. Reading past the end throws {@link IllegalArgumentException}.
 */
public class PayloadReader {

    private final byte[] payload;
    private int position;

    PayloadReader(byte[] payload) {
        this.payload = payload;
    }

    private void require(int length) {
        if (position + length > payload.length)
            throw new IllegalArgumentException("payload too short : " + payload.length + " bytes");
    }

    public int u8() {
        require(1);
        return payload[position++] & 0xFF;
    }

    public int i8() {
        require(1);
        return payload[position++];
    }

    public int u16() {
        require(2);
        int value = (payload[position] & 0xFF) | (payload[position + 1] & 0xFF) << 8;
        position += 2;
        return value;
    }

    public int remaining() {
        return payload.length - position;
    }

    public boolean hasRemaining() {
        return remaining() > 0;
    }
}
