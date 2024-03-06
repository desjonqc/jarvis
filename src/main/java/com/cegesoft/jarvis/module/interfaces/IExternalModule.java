package com.cegesoft.jarvis.module.interfaces;

import com.cegesoft.jarvis.events.Listener;
import com.cegesoft.jarvis.module.ModuleType;
import com.cegesoft.jarvis.properties.IAutoSerializable;

/**
 * Created by HoxiSword on 14/05/2020 for JARVIS
 */
public interface IExternalModule extends Listener, IAutoSerializable {

    String getId();
    void setEnabled(boolean bool);
    boolean isEnabled();
    boolean isConnected();
    double getBatteryLevel();
    void updateBatteryLevel();
    int getReceptionSignal();
    boolean connect();
    ModuleState getState();
    ModuleType getType();
    void checkState();
    void removeModule();

    void registerCommands();

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
