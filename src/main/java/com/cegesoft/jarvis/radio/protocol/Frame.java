package com.cegesoft.jarvis.radio.protocol;

import java.util.Arrays;
import java.util.HexFormat;

/**
 * A JARVIS-RF v1 frame : 5 bytes header + up to 27 bytes of payload.
 *
 * <pre>
 *  byte 0  dst   final node id (0x00 hub, 0xFF broadcast)
 *  byte 1  src   original node id
 *  byte 2  ctrl  b7-6 version | b5 ACK_REQ | b4 IS_REPLY | b3-0 TTL
 *  byte 3  seq   per sender counter, copied in the reply
 *  byte 4  op    operation code
 * </pre>
 *
 * @param op raw operation code (an unknown code is kept, so that it can be answered with a NACK)
 */
public record Frame(int dst, int src, boolean ackRequested, boolean reply, int ttl, int seq, int op, byte[] payload) {

    public static final int HUB = 0x00;
    public static final int BROADCAST = 0xFF;
    public static final int VERSION = 1;
    public static final int HEADER_LENGTH = 5;
    public static final int MAX_LENGTH = 32;
    public static final int MAX_PAYLOAD = MAX_LENGTH - HEADER_LENGTH;
    public static final int MAX_TTL = 0x0F;
    public static final int DEFAULT_TTL = 2;

    public Frame {
        checkByte("dst", dst);
        checkByte("src", src);
        checkByte("seq", seq);
        checkByte("op", op);
        if (ttl < 0 || ttl > MAX_TTL)
            throw new IllegalArgumentException("ttl out of range : " + ttl);
        payload = payload == null ? new byte[0] : payload.clone();
        if (payload.length > MAX_PAYLOAD)
            throw new IllegalArgumentException("payload too long : " + payload.length + " > " + MAX_PAYLOAD);
    }

    private static void checkByte(String name, int value) {
        if (value < 0 || value > 0xFF)
            throw new IllegalArgumentException(name + " out of range : " + value);
    }

    public static Frame request(int dst, int src, int seq, Opcode op, byte... payload) {
        return new Frame(dst, src, true, false, DEFAULT_TTL, seq, op.code(), payload);
    }

    public static Frame message(int dst, int src, int seq, Opcode op, byte... payload) {
        return new Frame(dst, src, false, false, DEFAULT_TTL, seq, op.code(), payload);
    }

    /**
     * @return the reply to this frame : same seq, source and destination swapped
     */
    public Frame replyWith(int src, Opcode op, byte... payload) {
        return new Frame(this.src, src, false, true, DEFAULT_TTL, seq, op.code(), payload);
    }

    /**
     * @return the opcode, or null if unknown
     */
    public Opcode opcode() {
        return Opcode.fromCode(op);
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }

    public int payloadLength() {
        return payload.length;
    }

    public PayloadReader reader() {
        return new PayloadReader(payload);
    }

    public Frame withTtl(int newTtl) {
        return new Frame(dst, src, ackRequested, reply, newTtl, seq, op, payload);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Frame frame)) return false;
        return dst == frame.dst && src == frame.src && ackRequested == frame.ackRequested && reply == frame.reply
                && ttl == frame.ttl && seq == frame.seq && op == frame.op && Arrays.equals(payload, frame.payload);
    }

    @Override
    public int hashCode() {
        return 31 * java.util.Objects.hash(dst, src, ackRequested, reply, ttl, seq, op) + Arrays.hashCode(payload);
    }

    @Override
    public String toString() {
        Opcode opcode = opcode();
        return String.format("Frame[%02X->%02X %s seq=%d%s%s ttl=%d %s]", src, dst,
                opcode == null ? String.format("op=0x%02X", op) : opcode.name(), seq,
                ackRequested ? " ACK_REQ" : "", reply ? " REPLY" : "", ttl, HexFormat.of().formatHex(payload));
    }
}
