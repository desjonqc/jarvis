package com.cegesoft.jarvis.radio.nrf24;

import com.cegesoft.jarvis.radio.Radio;
import com.cegesoft.jarvis.radio.RadioConfig;
import com.cegesoft.jarvis.radio.RadioException;
import com.cegesoft.jarvis.radio.TxResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static com.cegesoft.jarvis.radio.nrf24.Nrf24Registers.*;

/**
 * nRF24L01+ driver. The hub listens on its own address (pipe 1) ; pipe 0 is only enabled while transmitting,
 * to receive the hardware acknowledgement, so that the hub never "steals" a packet addressed to another node.
 * <p>
 * Radio address = network id (4 upper bytes) + node id (lowest byte), written LSB first :
 * on the Arduino side (RF24 library) it is the uint64 {@code ((uint64_t) networkId << 8) | nodeId}.
 * <p>
 * Not thread-safe : used by the radio thread only.
 */
public class Nrf24Driver implements Radio {

    private static final long POWER_UP_DELAY_US = 5_000;
    private static final long RX_SETTLING_US = 130;
    private static final long CE_PULSE_US = 15;
    private static final long TX_TIMEOUT_MS = 100;
    private static final int MAX_PACKETS_PER_READ = 16;

    private final RadioHardware hardware;
    private final RadioConfig config;
    private final int nodeId;
    private int configBase;
    private boolean started;
    private boolean irqWired;

    public Nrf24Driver(RadioHardware hardware, RadioConfig config, int nodeId) {
        this.hardware = hardware;
        this.config = config;
        this.nodeId = nodeId;
    }

    // ------------------------------------------------------------------ low level

    private int command(int command) {
        return hardware.transfer(new byte[]{(byte) command})[0] & 0xFF;
    }

    private int readRegister(int register) {
        return hardware.transfer(new byte[]{(byte) (R_REGISTER | register), (byte) NOP})[1] & 0xFF;
    }

    private void writeRegister(int register, int value) {
        hardware.transfer(new byte[]{(byte) (W_REGISTER | register), (byte) value});
    }

    private void writeRegister(int register, byte[] values) {
        byte[] tx = new byte[values.length + 1];
        tx[0] = (byte) (W_REGISTER | register);
        System.arraycopy(values, 0, tx, 1, values.length);
        hardware.transfer(tx);
    }

    private int status() {
        return command(NOP);
    }

    private static void pause(long micros) {
        LockSupport.parkNanos(TimeUnit.MICROSECONDS.toNanos(micros));
    }

    byte[] address(int node) {
        return new byte[]{(byte) node, (byte) config.networkId(), (byte) (config.networkId() >>> 8),
                (byte) (config.networkId() >>> 16), (byte) (config.networkId() >>> 24)};
    }

    private int rfSetup() {
        int rate = switch (config.dataRate()) {
            case KBPS_250 -> RF_DR_LOW;
            case MBPS_1 -> 0;
            case MBPS_2 -> RF_DR_HIGH;
        };
        // RF_PWR in bits 2:1 ; bit 0 is "LNA gain" on clones, ignored by genuine L01+ chips
        return rate | config.paLevel().ordinal() << 1 | 1;
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void start() throws RadioException {
        hardware.setCe(false);
        configBase = EN_CRC | CRCO; // CRC 16 bits, every interrupt enabled on the IRQ pin
        writeRegister(CONFIG, configBase);
        pause(POWER_UP_DELAY_US);

        writeRegister(SETUP_AW, ADDRESS_WIDTH - 2);
        writeRegister(SETUP_RETR, (config.retryDelayUs() / 250 - 1) << 4 | config.retries());
        writeRegister(RF_CH, config.channel());
        writeRegister(RF_SETUP, rfSetup());
        enableFeatures();
        writeRegister(DYNPD, ALL_PIPES);
        writeRegister(EN_AA, ALL_PIPES);
        writeRegister(EN_RXADDR, PIPE_1);
        writeRegister(RX_ADDR_P1, address(nodeId));
        this.verifyConfiguration();

        command(FLUSH_RX);
        command(FLUSH_TX);
        writeRegister(STATUS, RX_DR | TX_DS | MAX_RT);

        configBase |= PWR_UP;
        writeRegister(CONFIG, configBase);
        pause(POWER_UP_DELAY_US);
        this.startListening();
        started = true;
    }

    /**
     * Dynamic payload length and "no ack" packets. Some chips (old L01, clones) need the ACTIVATE command first.
     */
    private void enableFeatures() {
        int features = EN_DPL | EN_DYN_ACK;
        writeRegister(FEATURE, features);
        if (readRegister(FEATURE) != features) {
            hardware.transfer(new byte[]{(byte) ACTIVATE, (byte) ACTIVATE_KEY});
            writeRegister(FEATURE, features);
        }
    }

    private void verifyConfiguration() throws RadioException {
        int channel = readRegister(RF_CH);
        int addressWidth = readRegister(SETUP_AW);
        if (channel != config.channel() || addressWidth != ADDRESS_WIDTH - 2)
            throw new RadioException(String.format("nRF24 not responding (RF_CH=0x%02X, SETUP_AW=0x%02X) : check the SPI wiring, "
                    + "the 3.3V power supply and that SPI is enabled (dtparam=spi=on)", channel, addressWidth));
        int rfSetup = readRegister(RF_SETUP);
        if ((rfSetup & (RF_DR_LOW | RF_DR_HIGH)) != (rfSetup() & (RF_DR_LOW | RF_DR_HIGH)))
            throw new RadioException("nRF24 refused the " + config.dataRate() + " data rate : it is probably not a nRF24L01+ (250 kbps needs the + version)");
        if (readRegister(FEATURE) != (EN_DPL | EN_DYN_ACK))
            throw new RadioException("nRF24 refused the dynamic payload feature");
    }

    private void startListening() {
        writeRegister(EN_RXADDR, PIPE_1);
        writeRegister(CONFIG, configBase | PRIM_RX);
        hardware.setCe(true);
        pause(RX_SETTLING_US);
    }

    @Override
    public void stop() {
        if (!started)
            return;
        started = false;
        hardware.setCe(false);
        writeRegister(CONFIG, configBase & ~PWR_UP);
        hardware.close();
    }

    // ------------------------------------------------------------------ transmission

    @Override
    public TxResult transmit(int node, byte[] packet, boolean noAck) throws RadioException {
        if (packet.length == 0 || packet.length > MAX_PAYLOAD)
            throw new IllegalArgumentException("packet length must be in 1.." + MAX_PAYLOAD + " : " + packet.length);
        hardware.setCe(false);
        writeRegister(CONFIG, configBase);
        byte[] address = address(node);
        writeRegister(TX_ADDR, address);
        // The acknowledgement comes back on pipe 0, which must have the destination address
        writeRegister(RX_ADDR_P0, address);
        writeRegister(EN_RXADDR, PIPE_0 | PIPE_1);
        command(FLUSH_TX);
        writeRegister(STATUS, TX_DS | MAX_RT);

        byte[] tx = new byte[packet.length + 1];
        tx[0] = (byte) (noAck ? W_TX_PAYLOAD_NOACK : W_TX_PAYLOAD);
        System.arraycopy(packet, 0, tx, 1, packet.length);
        hardware.transfer(tx);

        hardware.setCe(true);
        pause(CE_PULSE_US);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TX_TIMEOUT_MS);
        int status;
        while (((status = status()) & (TX_DS | MAX_RT)) == 0) {
            if (System.nanoTime() > deadline) {
                hardware.setCe(false);
                command(FLUSH_TX);
                this.startListening();
                throw new RadioException("nRF24 transmission timeout (no TX_DS nor MAX_RT) : check the power supply of the radio");
            }
            pause(200);
        }
        hardware.setCe(false);
        int retries = readRegister(OBSERVE_TX) & 0x0F;
        boolean acknowledged = (status & TX_DS) != 0;
        if (!acknowledged)
            command(FLUSH_TX); // the packet stays in the FIFO after MAX_RT
        writeRegister(STATUS, TX_DS | MAX_RT); // MAX_RT must be cleared, otherwise the radio stops transmitting
        this.startListening();
        return new TxResult(acknowledged, retries);
    }

    // ------------------------------------------------------------------ reception

    /**
     * RX_DR is cleared before each check of the FIFO (datasheet procedure) : a packet arriving after the last check
     * sets it again and produces a new IRQ edge, so no packet stays unnoticed in the FIFO.
     */
    @Override
    public List<byte[]> receive() {
        List<byte[]> packets = new ArrayList<>();
        while (packets.size() < MAX_PACKETS_PER_READ) {
            writeRegister(STATUS, RX_DR);
            if ((readRegister(FIFO_STATUS) & RX_EMPTY) != 0)
                break;
            int width = hardware.transfer(new byte[]{(byte) R_RX_PL_WID, (byte) NOP})[1] & 0xFF;
            if (width == 0 || width > MAX_PAYLOAD) {
                // Corrupted length (datasheet : the packet must be discarded by flushing the RX FIFO)
                command(FLUSH_RX);
                continue;
            }
            byte[] tx = new byte[width + 1];
            tx[0] = (byte) R_RX_PAYLOAD;
            Arrays.fill(tx, 1, tx.length, (byte) NOP);
            packets.add(Arrays.copyOfRange(hardware.transfer(tx), 1, width + 1));
        }
        return packets;
    }

    @Override
    public void setReceiveListener(Runnable listener) {
        this.irqWired = hardware.onIrq(listener);
    }

    @Override
    public boolean signalsReception() {
        return irqWired;
    }

    // ------------------------------------------------------------------ diagnostics

    /**
     * Listen on every channel and count how often a signal stronger than -64 dBm is detected (RPD), to choose a free channel.
     *
     * @return the detection count per channel (index = channel)
     */
    public int[] scanChannels(int passes) {
        int[] detections = new int[RadioConfig.MAX_CHANNEL + 1];
        for (int pass = 0; pass < passes; pass++) {
            for (int channel = 0; channel <= RadioConfig.MAX_CHANNEL; channel++) {
                hardware.setCe(false);
                writeRegister(RF_CH, channel);
                hardware.setCe(true);
                pause(200); // RPD needs at least 170 µs of reception
                if ((readRegister(RPD) & 1) != 0)
                    detections[channel]++;
            }
        }
        hardware.setCe(false);
        writeRegister(RF_CH, config.channel());
        hardware.setCe(true);
        return detections;
    }
}
