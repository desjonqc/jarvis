package com.cegesoft.jarvis.radio.nrf24;

import com.cegesoft.jarvis.radio.RadioConfig;
import com.cegesoft.jarvis.radio.RadioException;
import com.cegesoft.jarvis.radio.TxResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.cegesoft.jarvis.radio.nrf24.Nrf24Registers.*;
import static org.junit.jupiter.api.Assertions.*;

class Nrf24DriverTest {

    private static final int NETWORK = 0x4A52564E; // "JRVN"

    private final FakeNrf24Chip chip = new FakeNrf24Chip();
    private final Nrf24Driver driver = new Nrf24Driver(chip, RadioConfig.defaults(76, NETWORK), 0x00);

    @Test
    void startConfiguresTheRadioAndListens() throws Exception {
        driver.start();
        assertEquals(76, chip.registers[RF_CH]);
        assertEquals(0x03, chip.registers[SETUP_AW]);
        assertEquals((3 << 4) | 15, chip.registers[SETUP_RETR], "1000 µs, 15 retries");
        assertEquals(RF_DR_LOW, chip.registers[RF_SETUP] & (RF_DR_LOW | RF_DR_HIGH), "250 kbps");
        assertEquals(0x06, chip.registers[RF_SETUP] & 0x06, "max PA");
        assertEquals(EN_DPL | EN_DYN_ACK, chip.registers[FEATURE]);
        assertEquals(0x3F, chip.registers[DYNPD]);
        assertEquals(PIPE_1, chip.registers[EN_RXADDR], "pipe 0 closed while listening");
        assertArrayEquals(new byte[]{0x00, 0x4E, 0x56, 0x52, 0x4A}, chip.addresses[RX_ADDR_P1], "node id first, then network id LSB first");
        assertEquals(EN_CRC | CRCO | PWR_UP | PRIM_RX, chip.registers[CONFIG]);
        assertTrue(chip.ce);
    }

    @Test
    void missingChipIsReported() {
        chip.connected = false;
        RadioException error = assertThrows(RadioException.class, driver::start);
        assertTrue(error.getMessage().contains("not responding"));
    }

    @Test
    void nonPlusChipIsReportedFor250Kbps() {
        chip.plusVariant = false;
        RadioException error = assertThrows(RadioException.class, driver::start);
        assertTrue(error.getMessage().contains("nRF24L01+"));
    }

    @Test
    void featuresAreActivatedOnChipsThatNeedIt() throws Exception {
        chip.featuresLocked = true;
        driver.start();
        assertEquals(EN_DPL | EN_DYN_ACK, chip.registers[FEATURE]);
    }

    @Test
    void acknowledgedTransmission() throws Exception {
        driver.start();
        chip.air = packet -> 3;
        TxResult result = driver.transmit(0x12, new byte[]{1, 2, 3}, false);
        assertEquals(new TxResult(true, 3), result);
        assertArrayEquals(new byte[]{1, 2, 3}, chip.transmitted.get(0));
        assertArrayEquals(new byte[]{0x12, 0x4E, 0x56, 0x52, 0x4A}, chip.addresses[TX_ADDR]);
        assertArrayEquals(chip.addresses[TX_ADDR], chip.addresses[RX_ADDR_P0], "ack received on pipe 0");
        assertEquals(0, chip.registers[STATUS] & (TX_DS | MAX_RT), "flags cleared");
        assertEquals(PIPE_1, chip.registers[EN_RXADDR], "back to listening, pipe 0 closed");
        assertNotEquals(0, chip.registers[CONFIG] & PRIM_RX);
    }

    @Test
    void failedTransmissionClearsMaxRtAndFlushes() throws Exception {
        driver.start();
        chip.air = packet -> -1;
        TxResult result = driver.transmit(0x12, new byte[]{9}, false);
        assertFalse(result.acknowledged());
        assertEquals(15, result.retries());
        assertEquals(0, chip.registers[STATUS] & MAX_RT, "MAX_RT must be cleared or the radio blocks");
        assertTrue(chip.txFifo.isEmpty(), "unsent packet flushed");

        chip.air = packet -> 0;
        assertTrue(driver.transmit(0x12, new byte[]{10}, false).acknowledged(), "the radio still transmits afterwards");
    }

    @Test
    void broadcastIsSentWithoutAck() throws Exception {
        driver.start();
        chip.air = packet -> -1;
        assertTrue(driver.transmit(0xFF, new byte[]{7}, true).acknowledged());
        assertTrue(chip.transmittedNoAck.get(0));
    }

    @Test
    void receivesEveryPacketOfTheFifoAndClearsRxDr() throws Exception {
        driver.start();
        chip.receivePacket(new byte[]{1, 2});
        chip.receivePacket(new byte[]{3, 4, 5});
        List<byte[]> packets = driver.receive();
        assertEquals(2, packets.size());
        assertArrayEquals(new byte[]{3, 4, 5}, packets.get(1));
        assertEquals(0, chip.registers[STATUS] & RX_DR);
        assertTrue(driver.receive().isEmpty());
    }

    @Test
    void corruptedPacketLengthFlushesTheFifo() throws Exception {
        driver.start();
        chip.rxFifo.add(new byte[40]);
        assertTrue(driver.receive().isEmpty());
        assertTrue(chip.rxFifo.isEmpty());
    }

    @Test
    void irqIsWired() throws Exception {
        boolean[] called = {false};
        driver.setReceiveListener(() -> called[0] = true);
        driver.start();
        chip.receivePacket(new byte[]{1});
        assertTrue(called[0]);
        assertTrue(driver.signalsReception());
    }

    @Test
    void stopPowersDown() throws Exception {
        driver.start();
        driver.stop();
        assertEquals(0, chip.registers[CONFIG] & PWR_UP);
        assertFalse(chip.ce);
    }
}
