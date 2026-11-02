package com.cegesoft.jarvis.radio;

import java.util.List;

/**
 * A packet radio. Not thread-safe : only the radio thread ({@link com.cegesoft.jarvis.radio.link.RadioLink}) uses it.
 */
public interface Radio {

    /**
     * Configure the radio and start listening.
     *
     * @throws RadioException if the radio doesn't answer (wiring, power)
     */
    void start() throws RadioException;

    /**
     * Send one packet to a node and wait for the hardware acknowledgement (unless {@code noAck}).
     */
    TxResult transmit(int node, byte[] packet, boolean noAck) throws RadioException;

    /**
     * @return the packets received since the last call (possibly none)
     */
    List<byte[]> receive() throws RadioException;

    /**
     * @param listener called (from another thread) when a packet may be available ; must be fast
     */
    void setReceiveListener(Runnable listener);

    /**
     * @return true if received packets are signaled through the listener, false if {@link #receive()} must be polled
     */
    boolean signalsReception();

    void stop();
}
