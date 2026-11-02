package com.cegesoft.jarvis.radio.nrf24;

/**
 * The wires of a nRF24L01+ : SPI bus (chip select handled by the bus), CE pin, optional IRQ pin.
 */
public interface RadioHardware extends AutoCloseable {

    /**
     * Full duplex SPI transfer, in one chip select assertion.
     */
    byte[] transfer(byte[] tx);

    void setCe(boolean high);

    /**
     * @param callback called on the IRQ falling edge
     * @return false if no IRQ pin is wired
     */
    boolean onIrq(Runnable callback);

    @Override
    void close();
}
