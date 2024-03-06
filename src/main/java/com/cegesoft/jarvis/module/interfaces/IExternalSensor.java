package com.cegesoft.jarvis.module.interfaces;

/**
 * Created by HoxiSword on 14/05/2020 for JARVIS
 */
public interface IExternalSensor<T> extends IExternalModule {

    T getSensorValue();

}
