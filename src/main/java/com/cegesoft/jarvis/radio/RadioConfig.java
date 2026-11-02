package com.cegesoft.jarvis.radio;

/**
 * Radio parameters, identical on every node of the network (see docs/protocol.md).
 *
 * @param channel      0..83 (2400 + channel MHz, inside the 2400-2483.5 MHz ISM band)
 * @param networkId    the 4 upper bytes of every radio address
 * @param retries      hardware retransmissions (0..15)
 * @param retryDelayUs delay between hardware retransmissions (250..4000 µs, multiple of 250)
 */
public record RadioConfig(int channel, DataRate dataRate, PaLevel paLevel, int networkId, int retries, int retryDelayUs) {

    public static final int MAX_CHANNEL = 83;

    public RadioConfig {
        if (channel < 0 || channel > MAX_CHANNEL)
            throw new IllegalArgumentException("channel must be in 0.." + MAX_CHANNEL + " (EU 2.4 GHz ISM band) : " + channel);
        if (retries < 0 || retries > 15)
            throw new IllegalArgumentException("retries must be in 0..15 : " + retries);
        if (retryDelayUs < 250 || retryDelayUs > 4000 || retryDelayUs % 250 != 0)
            throw new IllegalArgumentException("retryDelayUs must be a multiple of 250 in 250..4000 : " + retryDelayUs);
    }

    public static RadioConfig defaults(int channel, int networkId) {
        return new RadioConfig(channel, DataRate.KBPS_250, PaLevel.MAX, networkId, 15, 1000);
    }

    public enum DataRate {
        KBPS_250, MBPS_1, MBPS_2
    }

    public enum PaLevel {
        MIN, LOW, HIGH, MAX
    }
}
