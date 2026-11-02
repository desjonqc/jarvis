package com.cegesoft.jarvis.radio;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.JarvisConfig;
import com.cegesoft.jarvis.module.ModuleType;
import com.cegesoft.jarvis.radio.link.RadioLink;
import com.cegesoft.jarvis.radio.nrf24.DiozeroRadioHardware;
import com.cegesoft.jarvis.radio.nrf24.Nrf24Driver;
import com.cegesoft.jarvis.radio.protocol.Frame;
import com.cegesoft.jarvis.radio.sim.SimulatedActivator;
import com.cegesoft.jarvis.radio.sim.SimulatedRadio;
import com.cegesoft.jarvis.radio.sim.SimulatedSwitch;
import com.cegesoft.jarvis.service.Service;

import static com.cegesoft.jarvis.Jarvis.getLogger;

/**
 * Starts the radio (nRF24 on the GPIO header, or a simulated network) and connects the modules to it.
 */
public class RadioService extends Service {

    private final JarvisConfig config;
    private SimulatedRadio simulatedRadio;
    private RadioLink link;

    public RadioService(JarvisConfig config) {
        this.config = config;
    }

    @Override
    protected void startService() throws RadioException {
        Radio radio = switch (config.getRadioDriver()) {
            case "simulated" -> this.simulatedRadio = createSimulatedNetwork(config.getSimulatedNodes());
            case "nrf24" -> new Nrf24Driver(openHardware(config), config.getRadioConfig(), Frame.HUB);
            default -> throw new IllegalArgumentException("Unknown JARVIS_RADIO_DRIVER : " + config.getRadioDriver());
        };
        this.link = new RadioLink(radio, config.getCommandTimeoutMs());
        // Attached even if the radio fails to start : the commands then fail cleanly instead of crashing
        Jarvis.getModuleManager().attachTransport(link);
        link.start();
        RadioConfig radioConfig = config.getRadioConfig();
        getLogger().info("Radio " + config.getRadioDriver() + " started : channel " + radioConfig.channel() + ", "
                + radioConfig.dataRate() + ", PA " + radioConfig.paLevel());
        Jarvis.getModuleManager().discover();
    }

    /**
     * The hub keeps running without radio (web API, schedules) : a missing SPI device or native library is reported, not fatal.
     */
    static DiozeroRadioHardware openHardware(JarvisConfig config) throws RadioException {
        try {
            return new DiozeroRadioHardware(config.getSpiController(), config.getSpiChipSelect(),
                    config.getSpiFrequency(), config.getCeGpio(), config.getIrqGpio());
        } catch (RuntimeException | LinkageError e) {
            throw new RadioException("Radio hardware unavailable (SPI " + config.getSpiController() + "." + config.getSpiChipSelect()
                    + ", CE GPIO" + config.getCeGpio() + ") : is SPI enabled (dtparam=spi=on) and /dev/spidev0.0 given to the container ? " + e, e);
        }
    }

    static SimulatedRadio createSimulatedNetwork(String nodes) {
        SimulatedRadio radio = new SimulatedRadio();
        for (String node : nodes.split(",")) {
            String[] parts = node.trim().split(":");
            int id = Integer.parseInt(parts[0]);
            ModuleType type = ModuleType.valueOf(parts[1]);
            radio.add(switch (type) {
                case PULSE_SWITCH_SENSOR, SWITCH_SENSOR, LIGHT_SENSOR -> new SimulatedSwitch(id, type.getCode());
                case STATIC_RELAY_ACTIVATOR, PULSE_RELAY_ACTIVATOR -> new SimulatedActivator(id, type.getCode());
            });
        }
        return radio;
    }

    @Override
    protected void stopService() {
        if (link != null)
            link.stop();
    }

    public boolean isRadioRunning() {
        return link != null && link.isRunning();
    }

    /**
     * @return the simulated network, or null with real hardware
     */
    public SimulatedRadio getSimulatedRadio() {
        return simulatedRadio;
    }
}
