package com.cegesoft.jarvis.radio.sim;

import com.cegesoft.jarvis.radio.protocol.BatteryMeasurement;
import com.cegesoft.jarvis.radio.protocol.Frame;
import com.cegesoft.jarvis.radio.protocol.Hello;
import com.cegesoft.jarvis.radio.protocol.Opcode;
import com.cegesoft.jarvis.radio.protocol.Payloads;
import com.cegesoft.jarvis.radio.protocol.Status;

/**
 * Battery powered push button, asleep between two presses.
 */
public class SimulatedSwitch extends SimulatedNode {

    private int counter;
    private volatile int restMillivolts = 6200;
    private volatile int loadMillivolts = 5900;
    private volatile int temperature = 20;
    private boolean powerOnReset = true;

    public SimulatedSwitch(int id, int type) {
        super(id, type, Hello.CAP_SLEEPS | Hello.CAP_BATTERY);
    }

    public void setBattery(int restMillivolts, int loadMillivolts, int temperature) {
        this.restMillivolts = restMillivolts;
        this.loadMillivolts = loadMillivolts;
        this.temperature = temperature;
    }

    private synchronized BatteryMeasurement measure() {
        BatteryMeasurement measurement = new BatteryMeasurement(restMillivolts, loadMillivolts, temperature,
                powerOnReset ? BatteryMeasurement.FLAG_POWER_ON_RESET : 0);
        powerOnReset = false;
        return measurement;
    }

    public synchronized void press() {
        counter = (counter + 1) & 0xFFFF;
        byte[] count = Payloads.u16(counter);
        byte[] battery = measure().encode();
        byte[] payload = new byte[count.length + battery.length];
        System.arraycopy(count, 0, payload, 0, count.length);
        System.arraycopy(battery, 0, payload, count.length, battery.length);
        sendRequest(Opcode.PULSE_EVT, payload);
    }

    @Override
    protected Frame handle(Frame request) {
        if (request.opcode() == Opcode.STATUS_REQ)
            return request.replyWith(getId(), Opcode.STATUS, new Status(true, true, 0, measure()).encode());
        return null;
    }
}
