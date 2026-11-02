package com.cegesoft.jarvis.module.structure.sensor;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.module.schedule.ModuleSchedule;
import com.cegesoft.jarvis.module.schedule.ModuleScheduleElement;
import com.cegesoft.jarvis.module.schedule.ModuleScheduleEvent;
import com.cegesoft.jarvis.events.type.module.sensor.SensorPulseEvent;
import com.cegesoft.jarvis.module.ModuleType;
import com.cegesoft.jarvis.module.structure.ExternalModule;
import com.cegesoft.jarvis.properties.SavedItem;
import com.cegesoft.jarvis.radio.protocol.BatteryMeasurement;
import com.cegesoft.jarvis.radio.protocol.Frame;
import com.cegesoft.jarvis.radio.protocol.Hello;
import com.cegesoft.jarvis.radio.protocol.Opcode;
import com.cegesoft.jarvis.radio.protocol.PayloadReader;

/**
 * A push button : each press sends a PULSE_EVT with an increasing counter (and the battery measurement if it has one).
 */
public abstract class ExternalPulseSensor extends ExternalModule {

    /**
     * Node commanded directly by the button when the hub doesn't answer. 0 = automatic : the activator of the
     * first schedule triggered by this button.
     */
    @SavedItem
    private int fallbackTarget;
    private volatile int lastCounter = -1;
    private volatile int announcedFallback = -1;

    public ExternalPulseSensor(String id, ModuleType type) {
        super(id, type);
    }

    public ExternalPulseSensor() {
    }

    @Override
    public void onHello(Hello hello) {
        lastCounter = -1; // the counter restarts with the module
        super.onHello(hello);
    }

    @Override
    protected int getFallbackTarget() {
        if (fallbackTarget != 0)
            return fallbackTarget;
        for (ModuleSchedule schedule : Jarvis.getModuleManager().getScheduleManager().getSchedules()) {
            if (!schedule.isEnabled() || !(schedule.getEvent() instanceof ModuleScheduleEvent.SwitchScheduleEvent event) || !getId().equals(event.getModuleId()))
                continue;
            for (ModuleScheduleElement element : schedule.getElements()) {
                if (element instanceof ModuleScheduleElement.ActivateScheduleElement activate) {
                    try {
                        return Integer.parseInt(activate.getModuleId());
                    } catch (NumberFormatException ignored) {
                        // not a radio module
                    }
                }
            }
        }
        return 0;
    }

    @Override
    public byte[] welcomePayload() {
        byte[] payload = super.welcomePayload();
        announcedFallback = getFallbackTarget();
        return payload;
    }

    @Override
    protected void handleFrame(Frame frame) {
        if (frame.opcode() != Opcode.PULSE_EVT)
            return;
        PayloadReader reader = frame.reader();
        int counter = reader.u16();
        if (reader.remaining() >= BatteryMeasurement.LENGTH)
            this.onBattery(BatteryMeasurement.read(reader), counter);
        if (counter == lastCounter)
            return;
        lastCounter = counter;
        Jarvis.getEventManager().callEvent(new SensorPulseEvent(this));
        if (announcedFallback != getFallbackTarget()) {
            // The schedules changed : the button is listening right now, ask it to introduce itself again (new WELCOME)
            transport().send(getNodeId(), Opcode.DISCOVER, new byte[0]);
        }
    }
}
