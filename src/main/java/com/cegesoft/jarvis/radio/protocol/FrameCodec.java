package com.cegesoft.jarvis.radio.protocol;

import java.util.Arrays;

/**
 * Binary encoding of {@link Frame}s. The frame length comes from the radio (dynamic payload length).
 */
public final class FrameCodec {

    private static final int VERSION_SHIFT = 6;
    private static final int ACK_REQ = 1 << 5;
    private static final int IS_REPLY = 1 << 4;
    private static final int TTL_MASK = 0x0F;

    private FrameCodec() {
    }

    public static byte[] encode(Frame frame) {
        byte[] payload = frame.payload();
        byte[] bytes = new byte[Frame.HEADER_LENGTH + payload.length];
        bytes[0] = (byte) frame.dst();
        bytes[1] = (byte) frame.src();
        bytes[2] = (byte) (Frame.VERSION << VERSION_SHIFT
                | (frame.ackRequested() ? ACK_REQ : 0)
                | (frame.reply() ? IS_REPLY : 0)
                | frame.ttl() & TTL_MASK);
        bytes[3] = (byte) frame.seq();
        bytes[4] = (byte) frame.op();
        System.arraycopy(payload, 0, bytes, Frame.HEADER_LENGTH, payload.length);
        return bytes;
    }

    public static Frame decode(byte[] bytes) throws InvalidFrameException {
        if (bytes.length < Frame.HEADER_LENGTH)
            throw new InvalidFrameException("frame too short : " + bytes.length + " bytes");
        if (bytes.length > Frame.MAX_LENGTH)
            throw new InvalidFrameException("frame too long : " + bytes.length + " bytes");
        int ctrl = bytes[2] & 0xFF;
        int version = ctrl >>> VERSION_SHIFT;
        if (version != Frame.VERSION)
            throw new InvalidFrameException("unsupported protocol version : " + version);
        return new Frame(bytes[0] & 0xFF, bytes[1] & 0xFF, (ctrl & ACK_REQ) != 0, (ctrl & IS_REPLY) != 0, ctrl & TTL_MASK,
                bytes[3] & 0xFF, bytes[4] & 0xFF, Arrays.copyOfRange(bytes, Frame.HEADER_LENGTH, bytes.length));
    }
}
