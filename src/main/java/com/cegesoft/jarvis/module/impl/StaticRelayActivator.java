package com.cegesoft.jarvis.module.impl;

import com.cegesoft.jarvis.module.ModuleType;
import com.cegesoft.jarvis.module.structure.ExternalActivator;
import com.cegesoft.jarvis.properties.IAutoSerializable;

/**
 * Created by HoxiSword on 26/05/2020 for JARVIS
 */
public class StaticRelayActivator extends ExternalActivator {
    public StaticRelayActivator(String id, ModuleType type) {
        super(id, type);
    }

    public StaticRelayActivator() {

    }

    @Override
    public IAutoSerializable getBase() {
        return this;
    }
}
