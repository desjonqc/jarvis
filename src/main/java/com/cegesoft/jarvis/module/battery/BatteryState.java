package com.cegesoft.jarvis.module.battery;

public enum BatteryState {
    OK,
    /**
     * Below 20 %, or the voltage drops a lot when the radio transmits (high internal resistance).
     */
    REPLACE_SOON,
    /**
     * The battery is about to stop powering the radio.
     */
    REPLACE
}
