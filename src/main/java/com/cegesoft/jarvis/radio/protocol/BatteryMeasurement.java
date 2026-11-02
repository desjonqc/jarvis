package com.cegesoft.jarvis.radio.protocol;

/**
 * Raw battery measurement sent by battery powered nodes (4 bytes, in PULSE_EVT and STATUS) :
 * voltage at rest u8, voltage under radio load u8 (25 mV steps), internal temperature i8 (°C), flags u8.
 */
public record BatteryMeasurement(int restMillivolts, int loadMillivolts, int temperature, int flags) {

    public static final int LENGTH = 4;
    public static final int MILLIVOLTS_STEP = 25;
    /**
     * The node restarted after a power loss since its last message : the battery has probably been replaced.
     */
    public static final int FLAG_POWER_ON_RESET = 1;

    public boolean powerOnReset() {
        return (flags & FLAG_POWER_ON_RESET) != 0;
    }

    public static BatteryMeasurement read(PayloadReader reader) {
        return new BatteryMeasurement(reader.u8() * MILLIVOLTS_STEP, reader.u8() * MILLIVOLTS_STEP, reader.i8(), reader.u8());
    }

    public byte[] encode() {
        return new byte[]{
                Payloads.u8(Math.round(restMillivolts / (float) MILLIVOLTS_STEP)),
                Payloads.u8(Math.round(loadMillivolts / (float) MILLIVOLTS_STEP)),
                Payloads.i8(temperature),
                Payloads.u8(flags)};
    }

    public BatteryMeasurement calibrated(double factor) {
        return new BatteryMeasurement((int) Math.round(restMillivolts * factor), (int) Math.round(loadMillivolts * factor), temperature, flags);
    }
}
