package com.cegesoft.jarvis.module.interfaces;

import com.cegesoft.jarvis.module.activator.ActivatorState;

import java.util.concurrent.CompletableFuture;

/**
 * Created by HoxiSword on 14/05/2020 for JARVIS
 */
public interface IExternalActivator extends IExternalModule {
    /**
     * @return completed when the module confirmed its new state
     */
    CompletableFuture<?> write(ActivatorState state);

    ActivatorState getActivatorState();
}
