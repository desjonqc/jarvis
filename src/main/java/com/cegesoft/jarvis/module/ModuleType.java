package com.cegesoft.jarvis.module;

import com.cegesoft.jarvis.module.impl.*;
import com.cegesoft.jarvis.module.interfaces.IExternalModule;

import java.lang.reflect.InvocationTargetException;

/**
 * Created by HoxiSword on 14/05/2020 for JARVIS
 */
public enum ModuleType {

    //ACTIVATOR
    STATIC_RELAY_ACTIVATOR(StaticRelayActivator.class, 3600),
    PULSE_RELAY_ACTIVATOR(PulseRelayActivator.class, 3600),

    // SENSOR
    SWITCH_SENSOR(SwitchSensor.class, 2678400), // Un mois en secondes
    PULSE_SWITCH_SENSOR(PulseSwitchSensor.class, 2678400), // Un mois en secondes
    LIGHT_SENSOR(LightSensor.class, 3600);

    private final Class<? extends IExternalModule> module;
    private final long refreshInterval;

    ModuleType(Class<? extends IExternalModule> module, long refreshInterval) {
        this.module = module;
        this.refreshInterval = refreshInterval;
    }

    public static IExternalModule getModule(String type, String id) {
        try {
            return valueOf(type).getModule(id);
        } catch (Exception ignored) {}
        return null;
    }

    public Class<? extends IExternalModule> getModuleClass() {
        return module;
    }

    public IExternalModule getModule(String id) {
        try {
            return module.getConstructor(String.class, ModuleType.class).newInstance(id, this);
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return null;
    }

    public long getRefreshInterval() {
        return refreshInterval;
    }
}
