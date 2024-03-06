package com.cegesoft.jarvis.module.activator;

/**
 * Created by HoxiSword on 27/05/2020 for JARVIS
 */
public enum ActivatorState {

    ON(1),
    OFF(0),
    ON_BACK(-1);

    private final int value;

    ActivatorState(int value) {
        this.value = value;
    }

    public static ActivatorState getState(int value) {
        for (ActivatorState state : values()) {
            if (state.value == value)
                return state;
        }
        return OFF;
    }

    public int getValue() {
        return value;
    }
}
