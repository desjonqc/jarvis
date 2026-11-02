package com.cegesoft.jarvis.module.interfaces;

import com.cegesoft.jarvis.module.ModuleType;
import com.cegesoft.jarvis.properties.IAutoSerializable;
import com.cegesoft.jarvis.radio.protocol.Frame;
import com.cegesoft.jarvis.radio.protocol.Hello;

import java.util.concurrent.CompletableFuture;

/**
 * Created by HoxiSword on 14/05/2020 for JARVIS
 */
public interface IExternalModule extends IAutoSerializable {

    String getId();

    /**
     * @return the radio node id (1..254)
     */
    int getNodeId();

    /**
     * @return completed when the module confirmed the change
     */
    CompletableFuture<?> setEnabled(boolean bool);

    boolean isEnabled();

    boolean isConnected();

    double getBatteryLevel();

    /**
     * @return completed when the battery level is up to date (immediately for a sleeping module : the last known value)
     */
    CompletableFuture<?> updateBatteryLevel();

    int getReceptionSignal();

    ModuleState getState();

    ModuleType getType();

    /**
     * @return the name given in the web app, or null
     */
    String getName();

    /**
     * @return the room given in the web app, or null
     */
    String getRoom();

    /**
     * Name and room shown in the web app (null or blank = none).
     */
    void setLabels(String name, String room);

    /**
     * The module (re)started and introduced itself.
     */
    void onHello(Hello hello);

    /**
     * A frame was received from the module (radio dispatch thread).
     */
    void onFrame(Frame frame);

    /**
     * @return the WELCOME payload answered to the module's HELLO
     */
    byte[] welcomePayload();

    /**
     * @return the delay without any frame after which the module is considered disconnected
     */
    long getSilenceTimeoutMs();

    long getLastSeen();

    void setLastSeen(long time);

    void markDisconnected();

    enum ModuleState {
        DISABLED,
        DISCONNECTED,
        BATTERY_LOW,
        SIGNAL_LOW,
        WORKING,
        CONNECTING,
        SLEEPING
    }

}
