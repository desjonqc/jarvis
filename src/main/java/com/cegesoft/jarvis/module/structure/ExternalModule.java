package com.cegesoft.jarvis.module.structure;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.events.type.module.*;
import com.cegesoft.jarvis.module.ModuleType;
import com.cegesoft.jarvis.module.battery.BatteryEstimator;
import com.cegesoft.jarvis.module.battery.BatteryProfile;
import com.cegesoft.jarvis.module.interfaces.IExternalModule;
import com.cegesoft.jarvis.properties.IAutoSerializable;
import com.cegesoft.jarvis.properties.SavedItem;
import com.cegesoft.jarvis.radio.link.FrameTransport;
import com.cegesoft.jarvis.radio.link.LinkQuality;
import com.cegesoft.jarvis.radio.protocol.BatteryMeasurement;
import com.cegesoft.jarvis.radio.protocol.Frame;
import com.cegesoft.jarvis.radio.protocol.Hello;
import com.cegesoft.jarvis.radio.protocol.Opcode;
import com.cegesoft.jarvis.radio.protocol.Payloads;
import com.cegesoft.jarvis.radio.protocol.Status;
import org.json.simple.JSONObject;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Created by HoxiSword on 14/05/2020 for JARVIS
 * <p>
 * A module reached through the radio. Frames are handled on the radio dispatch thread ; the web API and the
 * schedules call it from other threads : the state is kept in volatile fields.
 */
public abstract class ExternalModule implements IExternalModule {

    /**
     * Status period asked to mains powered modules, and to sleeping ones (they wake up with the AVR watchdog).
     */
    static final int MAINS_HEARTBEAT_S = 60;
    static final int SLEEPING_HEARTBEAT_S = 12 * 3600;
    static final double SILENCE_TOLERANCE = 2.5;

    @SavedItem
    private String id;
    @SavedItem
    private ModuleType type;
    @SavedItem
    private volatile ModuleState state = ModuleState.DISCONNECTED;
    @SavedItem
    private volatile double batteryLevel = 100.0;
    @SavedItem
    private volatile int receptionSignal = 10;
    @SavedItem
    private volatile boolean sleeper;
    /**
     * Correction of the battery voltages measured by this board (multimeter value / measured value).
     */
    @SavedItem
    private double batteryCalibration = 1.0;
    @SavedItem
    private BatteryEstimator battery;
    @SavedItem
    private volatile String name;
    @SavedItem
    private volatile String room;
    private volatile long lastSeen;

    public ExternalModule(String id, ModuleType type) {
        this.id = id;
        this.type = type;
    }

    public ExternalModule() {
    }

    // ------------------------------------------------------------------ radio

    protected FrameTransport transport() {
        return Jarvis.getModuleManager().getTransport();
    }

    protected CompletableFuture<Frame> request(Opcode op, boolean coalesce, byte... payload) {
        return transport().request(getNodeId(), op, payload, coalesce);
    }

    @Override
    public void onHello(Hello hello) {
        this.sleeper = hello.sleeps();
        if (hello.hasBattery() && battery == null)
            battery = new BatteryEstimator(BatteryProfile.CR2032_X2_SERIES);
        BatteryProfile announced = switch (hello.batteryPack()) {
            case Hello.PACK_CR2032_X2_SERIES -> BatteryProfile.CR2032_X2_SERIES;
            case Hello.PACK_CR2032_X2_PARALLEL -> BatteryProfile.CR2032_X2_PARALLEL;
            default -> null;
        };
        if (battery != null && announced != null)
            battery.setProfile(announced); // the firmware knows how its battery is wired
        this.lastSeen = System.currentTimeMillis();
        boolean disabled = state == ModuleState.DISABLED;
        this.state = ModuleState.WORKING;
        this.updateLinkQuality();
        Jarvis.getEventManager().callEvent(new ModuleConnectedEvent(this));
        this.log("Module connected (firmware " + hello.firmware() + (hello.via() != 0 ? ", through relay " + hello.via() : "") + ")");
        if (disabled) {
            // A restarted module is enabled : apply the state chosen by the user again
            this.setEnabled(false);
        }
        this.checkState();
    }

    @Override
    public byte[] welcomePayload() {
        byte[] heartbeat = Payloads.u16(sleeper ? SLEEPING_HEARTBEAT_S : MAINS_HEARTBEAT_S);
        return new byte[]{0, heartbeat[0], heartbeat[1], Payloads.u8(getFallbackTarget())};
    }

    /**
     * @return the node a module may command directly when the hub doesn't answer (0 = none)
     */
    protected int getFallbackTarget() {
        return 0;
    }

    @Override
    public void onFrame(Frame frame) {
        this.lastSeen = System.currentTimeMillis();
        if (state == ModuleState.DISCONNECTED || state == ModuleState.CONNECTING) {
            this.state = ModuleState.WORKING;
            Jarvis.getEventManager().callEvent(new ModuleConnectedEvent(this));
        }
        this.updateLinkQuality();
        if (frame.opcode() == Opcode.STATUS)
            this.onStatus(Status.read(frame));
        else
            this.handleFrame(frame);
        this.checkState();
    }

    protected void onStatus(Status status) {
        boolean wasEnabled = isEnabled();
        if (status.enabled() != wasEnabled) {
            this.state = status.enabled() ? ModuleState.WORKING : ModuleState.DISABLED;
            Jarvis.getEventManager().callEvent(new ModuleEnableChangeEvent(this, status.enabled()));
        }
        if (status.battery() != null)
            this.onBattery(status.battery(), null);
        if (battery != null && status.txFailures() > 0)
            battery.onTransmissionFailures(status.txFailures());
    }

    protected void onBattery(BatteryMeasurement measurement, Integer counter) {
        if (battery == null)
            battery = new BatteryEstimator(BatteryProfile.CR2032_X2_SERIES);
        battery.update(measurement.calibrated(batteryCalibration), counter, System.currentTimeMillis());
        this.batteryLevel = battery.getPercent();
        Jarvis.getEventManager().callEvent(new ModuleBatteryLevelUpdateEvent(this));
    }

    /**
     * Handle a frame specific to the module type.
     */
    protected void handleFrame(Frame frame) {
    }

    private void updateLinkQuality() {
        LinkQuality quality = transport().quality(getNodeId());
        if (quality != null)
            this.receptionSignal = quality.score();
    }

    /**
     * Low signal / low battery states, with their events when they appear.
     */
    protected void checkState() {
        ModuleState previous = state;
        if (previous == ModuleState.DISABLED || previous == ModuleState.DISCONNECTED)
            return;
        ModuleState current = ModuleState.WORKING;
        if (receptionSignal < 3)
            current = ModuleState.SIGNAL_LOW;
        if (batteryLevel < 20)
            current = ModuleState.BATTERY_LOW;
        this.state = current;
        if (current == previous)
            return;
        if (current == ModuleState.SIGNAL_LOW)
            Jarvis.getEventManager().callEvent(new ModuleLowSignalDetectedEvent(this));
        if (current == ModuleState.BATTERY_LOW)
            Jarvis.getEventManager().callEvent(new ModuleLowPowerDetectedEvent(this));
    }

    @Override
    public void markDisconnected() {
        if (state == ModuleState.DISCONNECTED)
            return;
        this.state = ModuleState.DISCONNECTED;
        this.log("Module silent for too long : disconnected.");
        Jarvis.getEventManager().callEvent(new ModuleDisconnectEvent(this));
    }

    // ------------------------------------------------------------------ commands

    @Override
    public CompletableFuture<?> setEnabled(boolean bool) {
        return this.request(Opcode.SET_ENABLED, true, Payloads.u8(bool ? 1 : 0));
    }

    @Override
    public CompletableFuture<?> updateBatteryLevel() {
        if (sleeper)
            return CompletableFuture.completedFuture(batteryLevel); // it sends its battery level each time it wakes up
        return this.request(Opcode.STATUS_REQ, true);
    }

    // ------------------------------------------------------------------ getters

    @Override
    public String getId() {
        return id;
    }

    @Override
    public int getNodeId() {
        return Integer.parseInt(id);
    }

    @Override
    public ModuleType getType() {
        return type;
    }

    @Override
    public ModuleState getState() {
        return state;
    }

    @Override
    public boolean isEnabled() {
        return state != ModuleState.DISABLED;
    }

    @Override
    public boolean isConnected() {
        return state != ModuleState.DISCONNECTED && state != ModuleState.CONNECTING;
    }

    @Override
    public double getBatteryLevel() {
        return batteryLevel;
    }

    @Override
    public int getReceptionSignal() {
        return receptionSignal;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getRoom() {
        return room;
    }

    @Override
    public void setLabels(String name, String room) {
        this.name = name == null || name.isBlank() ? null : name.strip();
        this.room = room == null || room.isBlank() ? null : room.strip();
    }

    public boolean isSleeper() {
        return sleeper;
    }

    public BatteryEstimator getBattery() {
        return battery;
    }

    @Override
    public long getSilenceTimeoutMs() {
        long heartbeat = sleeper ? SLEEPING_HEARTBEAT_S : MAINS_HEARTBEAT_S;
        return (long) (TimeUnit.SECONDS.toMillis(heartbeat) * SILENCE_TOLERANCE);
    }

    @Override
    public long getLastSeen() {
        return lastSeen;
    }

    @Override
    public void setLastSeen(long time) {
        this.lastSeen = time;
    }

    @Override
    @SuppressWarnings("unchecked")
    public JSONObject save() {
        JSONObject json = IExternalModule.super.save();
        if (battery != null) {
            json.put("batteryState", battery.getState().name());
            Integer daysLeft = battery.daysLeft(System.currentTimeMillis());
            if (daysLeft != null)
                json.put("batteryDaysLeft", daysLeft);
        }
        return json;
    }

    @Override
    public IAutoSerializable getBase() {
        return this;
    }

    protected void log(String message) {
        String prefix = " [" + this.id + " | " + this.state.name() + " | " + this.type.name() + " | Signal : " + this.receptionSignal + " | Battery : " + this.batteryLevel + "] ";
        Jarvis.getLogger().info(prefix + message);
    }
}
