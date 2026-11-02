package com.cegesoft.jarvis.radio.protocol;

/**
 * STATUS payload : flags u8 (b0 enabled, b1 sleeping), transmission failures u8 since the previous STATUS
 * [, battery measurement (4 bytes) for battery powered nodes].
 */
public record Status(boolean enabled, boolean sleeping, int txFailures, BatteryMeasurement battery) {

    public static final int FLAG_ENABLED = 1;
    public static final int FLAG_SLEEPING = 1 << 1;

    public static Status read(Frame frame) {
        PayloadReader reader = frame.reader();
        int flags = reader.u8();
        int failures = reader.u8();
        BatteryMeasurement battery = reader.remaining() >= BatteryMeasurement.LENGTH ? BatteryMeasurement.read(reader) : null;
        return new Status((flags & FLAG_ENABLED) != 0, (flags & FLAG_SLEEPING) != 0, failures, battery);
    }

    public byte[] encode() {
        byte[] head = {Payloads.u8((enabled ? FLAG_ENABLED : 0) | (sleeping ? FLAG_SLEEPING : 0)), Payloads.u8(txFailures)};
        if (battery == null)
            return head;
        byte[] measurement = battery.encode();
        byte[] all = new byte[head.length + measurement.length];
        System.arraycopy(head, 0, all, 0, head.length);
        System.arraycopy(measurement, 0, all, head.length, measurement.length);
        return all;
    }
}
