package com.cegesoft.jarvis;

import com.cegesoft.jarvis.radio.RadioConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JarvisConfigTest {

    @Test
    void readsValuesAndSplitsApiKeys() {
        JarvisConfig config = JarvisConfig.from(Map.of(
                "JARVIS_RADIO_CHANNEL", "60",
                "JARVIS_RADIO_DATA_RATE", "1mbps",
                "JARVIS_API_PORT", "9000",
                "JARVIS_API_KEYS", " key1, ,key2 ")::get);
        assertEquals(60, config.getRadioConfig().channel());
        assertEquals(RadioConfig.DataRate.MBPS_1, config.getRadioConfig().dataRate());
        assertEquals(9000, config.getApiPort());
        assertEquals(List.of("key1", "key2"), config.getApiKeys());
    }

    @Test
    void defaults() {
        JarvisConfig config = JarvisConfig.from(key -> null);
        assertTrue(config.getApiKeys().isEmpty());
        assertEquals("nrf24", config.getRadioDriver());
        assertEquals(RadioConfig.DataRate.KBPS_250, config.getRadioConfig().dataRate());
        assertEquals(0x4A52564E, config.getRadioConfig().networkId());
        assertEquals(22, config.getCeGpio());
    }

    @Test
    void channelOutsideTheEuropeanBandIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> JarvisConfig.from(Map.of("JARVIS_RADIO_CHANNEL", "100")::get));
    }
}
