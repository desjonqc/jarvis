package com.cegesoft.jarvis.radio.protocol;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The test vectors are shared with the firmware tests (protocol/test-vectors.json).
 */
class FrameCodecTest {

    private static final HexFormat HEX = HexFormat.of();

    @Test
    void testVectors() throws Exception {
        JSONArray vectors = (JSONArray) new JSONParser().parse(Files.readString(Path.of("protocol/test-vectors.json")));
        assertFalse(vectors.isEmpty());
        for (Object o : vectors) {
            JSONObject vector = (JSONObject) o;
            String name = (String) vector.get("name");
            byte[] bytes = HEX.parseHex((String) vector.get("hex"));
            if (Boolean.TRUE.equals(vector.get("invalid"))) {
                assertThrows(InvalidFrameException.class, () -> FrameCodec.decode(bytes), name);
                continue;
            }
            Frame expected = new Frame(((Long) vector.get("dst")).intValue(), ((Long) vector.get("src")).intValue(),
                    (Boolean) vector.get("ackRequested"), (Boolean) vector.get("reply"), ((Long) vector.get("ttl")).intValue(),
                    ((Long) vector.get("seq")).intValue(), ((Long) vector.get("op")).intValue(), HEX.parseHex((String) vector.get("payload")));
            assertEquals(expected, FrameCodec.decode(bytes), name);
            assertArrayEquals(bytes, FrameCodec.encode(expected), name);
        }
    }

    @Test
    void rejectsTooLongFramesAndPayloads() {
        assertThrows(InvalidFrameException.class, () -> FrameCodec.decode(new byte[33]));
        assertThrows(IllegalArgumentException.class, () -> Frame.message(1, 0, 0, Opcode.PING, new byte[28]));
        assertDoesNotThrow(() -> Frame.message(1, 0, 0, Opcode.PING, new byte[27]));
    }

    @Test
    void replyKeepsSeqAndSwapsAddresses() {
        Frame request = Frame.request(0x12, 0, 200, Opcode.PULSE);
        Frame reply = request.replyWith(0x12, Opcode.STATE, (byte) 1);
        assertEquals(0, reply.dst());
        assertEquals(0x12, reply.src());
        assertEquals(200, reply.seq());
        assertTrue(reply.reply());
        assertFalse(reply.ackRequested());
    }

    @Test
    void helloAnnouncesTheBatteryPack() {
        int capabilities = Hello.CAP_SLEEPS | Hello.CAP_BATTERY | Hello.PACK_CR2032_X2_SERIES << Hello.CAP_PACK_SHIFT;
        Hello hello = Hello.read(Frame.request(0, 3, 1, Opcode.HELLO, (byte) 4, (byte) 2, (byte) capabilities));
        assertTrue(hello.sleeps());
        assertTrue(hello.hasBattery());
        assertEquals(Hello.PACK_CR2032_X2_SERIES, hello.batteryPack());
        assertEquals(0, hello.via());
    }

    @Test
    void batteryMeasurementRoundTrip() {
        BatteryMeasurement measurement = new BatteryMeasurement(6200, 5900, -5, BatteryMeasurement.FLAG_POWER_ON_RESET);
        Frame frame = Frame.message(0, 3, 1, Opcode.STATUS, measurement.encode());
        assertEquals(measurement, BatteryMeasurement.read(frame.reader()));
        assertTrue(measurement.powerOnReset());
    }

    @Test
    void duplicateFilterRemembersRepliesAndExpires() {
        DuplicateFilter filter = new DuplicateFilter();
        Frame reply = Frame.message(3, 0, 9, Opcode.ACK);
        filter.record(9, 1000);
        filter.recordReply(9, reply, 1000);
        assertTrue(filter.isDuplicate(9, 2000));
        assertEquals(reply, filter.replyOf(9, 2000));
        assertFalse(filter.isDuplicate(9, 1000 + DuplicateFilter.EXPIRY_MS + 1));
        for (int seq = 10; seq < 10 + DuplicateFilter.WINDOW; seq++)
            filter.record(seq, 3000);
        assertFalse(filter.isDuplicate(9, 3000), "pushed out of the window");
    }
}
