package com.cegesoft.jarvis.radio.nrf24;

import com.diozero.api.DigitalInputDevice;
import com.diozero.api.DigitalOutputDevice;
import com.diozero.api.GpioEventTrigger;
import com.diozero.api.GpioPullUpDown;
import com.diozero.api.SpiClockMode;
import com.diozero.api.SpiDevice;
import com.diozero.api.SpiDeviceInterface;

/**
 * nRF24 wired on the Raspberry Pi header : SPI through /dev/spidevX.Y, CE and IRQ through /dev/gpiochip0 (BCM numbers).
 */
public class DiozeroRadioHardware implements RadioHardware {

    private final SpiDeviceInterface spi;
    private final DigitalOutputDevice ce;
    private final int irqGpio;
    private DigitalInputDevice irq;

    /**
     * @param irqGpio BCM number of the IRQ pin, or a negative value if it isn't wired
     */
    public DiozeroRadioHardware(int spiController, int spiChipSelect, int spiFrequency, int ceGpio, int irqGpio) {
        this.spi = SpiDevice.builder(spiChipSelect)
                .setController(spiController)
                .setFrequency(spiFrequency)
                .setClockMode(SpiClockMode.MODE_0)
                .build();
        this.ce = DigitalOutputDevice.Builder.builder(ceGpio).setInitialValue(false).build();
        this.irqGpio = irqGpio;
    }

    @Override
    public byte[] transfer(byte[] tx) {
        return spi.writeAndRead(tx);
    }

    @Override
    public void setCe(boolean high) {
        ce.setOn(high);
    }

    @Override
    public boolean onIrq(Runnable callback) {
        if (irqGpio < 0)
            return false;
        // IRQ is an active low push-pull output : no pull resistor needed, event on the falling edge
        this.irq = DigitalInputDevice.Builder.builder(irqGpio)
                .setPullUpDown(GpioPullUpDown.NONE)
                .setTrigger(GpioEventTrigger.FALLING)
                .setActiveHigh(false)
                .build();
        this.irq.whenActivated(nanoTime -> callback.run());
        return true;
    }

    @Override
    public void close() {
        if (irq != null)
            irq.close();
        ce.close();
        spi.close();
    }
}
