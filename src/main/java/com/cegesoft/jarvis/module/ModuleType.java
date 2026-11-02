package com.cegesoft.jarvis.module;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.module.impl.*;
import com.cegesoft.jarvis.module.interfaces.IExternalModule;

import java.lang.reflect.InvocationTargetException;

/**
 * Created by HoxiSword on 14/05/2020 for JARVIS
 * <p>
 * The code is the module type sent by the firmware in its HELLO.
 */
public enum ModuleType {

    //ACTIVATOR
    STATIC_RELAY_ACTIVATOR(1, StaticRelayActivator.class),
    PULSE_RELAY_ACTIVATOR(2, PulseRelayActivator.class),

    // SENSOR
    SWITCH_SENSOR(3, SwitchSensor.class),
    PULSE_SWITCH_SENSOR(4, PulseSwitchSensor.class),
    LIGHT_SENSOR(5, LightSensor.class);

    private final int code;
    private final Class<? extends IExternalModule> module;

    ModuleType(int code, Class<? extends IExternalModule> module) {
        this.code = code;
        this.module = module;
    }

    /**
     * @return the type with this HELLO code, or null if it is unknown
     */
    public static ModuleType fromCode(int code) {
        for (ModuleType type : values()) {
            if (type.code == code)
                return type;
        }
        return null;
    }

    public int getCode() {
        return code;
    }

    public Class<? extends IExternalModule> getModuleClass() {
        return module;
    }

    public IExternalModule getModule(String id) {
        try {
            return module.getConstructor(String.class, ModuleType.class).newInstance(id, this);
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            Jarvis.getLogger().error("Can't create module " + id + " of type " + this + " : ", e);
        }
        return null;
    }
}
