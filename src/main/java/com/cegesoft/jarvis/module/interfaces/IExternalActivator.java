package com.cegesoft.jarvis.module.interfaces;

import com.cegesoft.jarvis.module.activator.ActivatorState;

/**
 * Created by HoxiSword on 14/05/2020 for JARVIS
 */
public interface IExternalActivator extends IExternalModule {
    void write(ActivatorState state);
    ActivatorState getActivatorState();
}
