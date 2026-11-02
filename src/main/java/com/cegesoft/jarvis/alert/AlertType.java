package com.cegesoft.jarvis.alert;

public enum AlertType {

    /**
     * The module didn't talk for longer than its silence timeout.
     */
    MODULE_DISCONNECTED(Severity.CRITICAL),
    /**
     * Below 20 %, or the battery estimator says it has to be replaced soon.
     */
    BATTERY_LOW(Severity.WARNING),
    /**
     * The battery is about to stop powering the radio.
     */
    BATTERY_CRITICAL(Severity.CRITICAL),
    /**
     * Link quality score below 3 / 10 : commands may be lost.
     */
    SIGNAL_LOW(Severity.WARNING),
    /**
     * The hub's radio is not running : no module can be reached.
     */
    RADIO_DOWN(Severity.CRITICAL);

    public enum Severity {
        WARNING,
        CRITICAL
    }

    private final Severity severity;

    AlertType(Severity severity) {
        this.severity = severity;
    }

    public Severity getSeverity() {
        return severity;
    }
}
