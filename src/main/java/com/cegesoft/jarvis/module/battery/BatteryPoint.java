package com.cegesoft.jarvis.module.battery;

import com.cegesoft.jarvis.properties.IAutoSerializable;
import com.cegesoft.jarvis.properties.SavedItem;

/**
 * Daily summary of the battery measurements (history kept for one year).
 */
public class BatteryPoint implements IAutoSerializable {

    @SavedItem
    long day;
    @SavedItem
    int loadMillivolts;
    @SavedItem
    int restMillivolts;
    @SavedItem
    int temperature;

    BatteryPoint() {
    }

    BatteryPoint(long day, int loadMillivolts, int restMillivolts, int temperature) {
        this.day = day;
        this.loadMillivolts = loadMillivolts;
        this.restMillivolts = restMillivolts;
        this.temperature = temperature;
    }

    @Override
    public IAutoSerializable getBase() {
        return this;
    }
}
