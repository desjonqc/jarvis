package com.cegesoft.jarvis.radio.nrf24;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;

import static com.cegesoft.jarvis.radio.nrf24.Nrf24Registers.*;

/**
 * Register level simulation of a nRF24L01+, enough to check the driver's command sequences.
 */
class FakeNrf24Chip implements RadioHardware {

    final int[] registers = new int[0x20];
    final byte[][] addresses = new byte[0x20][];
    final Deque<byte[]> txFifo = new ArrayDeque<>();
    final Deque<byte[]> rxFifo = new ArrayDeque<>();
    final List<byte[]> transmitted = new ArrayList<>();
    final List<Boolean> transmittedNoAck = new ArrayList<>();
    /**
     * Number of retries before the ack for a transmitted packet, or -1 if it is never acknowledged.
     */
    Function<byte[], Integer> air = packet -> 0;
    boolean ce;
    boolean connected = true;
    boolean plusVariant = true;
    boolean featuresLocked;
    Runnable irq;

    FakeNrf24Chip() {
        registers[CONFIG] = 0x08;
        registers[SETUP_AW] = 0x03;
        registers[RF_CH] = 0x02;
        registers[STATUS] = 0x0E;
    }

    private int status() {
        int status = registers[STATUS] & 0x70;
        status |= rxFifo.isEmpty() ? 0x0E : 0x02;
        return status;
    }

    @Override
    public byte[] transfer(byte[] tx) {
        byte[] rx = new byte[tx.length];
        if (!connected)
            return rx;
        rx[0] = (byte) status();
        int command = tx[0] & 0xFF;
        if (command < W_REGISTER) {
            int register = command & 0x1F;
            if (addresses[register] != null) {
                System.arraycopy(addresses[register], 0, rx, 1, Math.min(addresses[register].length, tx.length - 1));
            } else if (register == FIFO_STATUS) {
                rx[1] = (byte) ((rxFifo.isEmpty() ? 1 : 0) | (txFifo.isEmpty() ? 0x10 : 0));
            } else {
                rx[1] = (byte) registers[register];
            }
        } else if (command < 0x40) {
            write(command & 0x1F, Arrays.copyOfRange(tx, 1, tx.length));
        } else if (command == R_RX_PL_WID) {
            rx[1] = (byte) (rxFifo.isEmpty() ? 0 : rxFifo.peek().length);
        } else if (command == R_RX_PAYLOAD) {
            byte[] packet = rxFifo.poll();
            if (packet != null)
                System.arraycopy(packet, 0, rx, 1, Math.min(packet.length, tx.length - 1));
        } else if (command == W_TX_PAYLOAD || command == W_TX_PAYLOAD_NOACK) {
            txFifo.add(Arrays.copyOfRange(tx, 1, tx.length));
            transmittedNoAck.add(command == W_TX_PAYLOAD_NOACK);
        } else if (command == FLUSH_TX) {
            txFifo.clear();
        } else if (command == FLUSH_RX) {
            rxFifo.clear();
        } else if (command == ACTIVATE) {
            featuresLocked = false;
        }
        return rx;
    }

    private void write(int register, byte[] values) {
        if (register == RX_ADDR_P0 || register == RX_ADDR_P1 || register == TX_ADDR) {
            addresses[register] = values;
            return;
        }
        int value = values[0] & 0xFF;
        switch (register) {
            case STATUS -> registers[STATUS] &= ~(value & 0x70); // write 1 to clear
            case RF_SETUP -> registers[RF_SETUP] = plusVariant ? value : value & ~RF_DR_LOW;
            case FEATURE -> registers[FEATURE] = featuresLocked ? 0 : value;
            default -> registers[register] = value;
        }
    }

    @Override
    public void setCe(boolean high) {
        this.ce = high;
        boolean poweredUp = (registers[CONFIG] & PWR_UP) != 0;
        boolean txMode = (registers[CONFIG] & PRIM_RX) == 0;
        if (high && poweredUp && txMode && !txFifo.isEmpty()) {
            byte[] packet = txFifo.peek();
            transmitted.add(packet);
            boolean noAck = transmittedNoAck.get(transmittedNoAck.size() - 1);
            int retries = noAck ? 0 : air.apply(packet);
            if (retries >= 0) {
                txFifo.poll();
                registers[STATUS] |= TX_DS;
                registers[OBSERVE_TX] = retries;
            } else {
                registers[STATUS] |= MAX_RT; // the packet stays in the FIFO
                registers[OBSERVE_TX] = registers[SETUP_RETR] & 0x0F;
            }
        }
    }

    void receivePacket(byte[] packet) {
        rxFifo.add(packet);
        registers[STATUS] |= RX_DR;
        if (irq != null)
            irq.run();
    }

    @Override
    public boolean onIrq(Runnable callback) {
        this.irq = callback;
        return true;
    }

    @Override
    public void close() {
    }
}
