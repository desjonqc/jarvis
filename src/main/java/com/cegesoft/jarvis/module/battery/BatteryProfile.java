package com.cegesoft.jarvis.module.battery;

/**
 * Discharge model of a battery pack. Voltages are measured under radio load (the meaningful value for coin cells).
 */
public enum BatteryProfile {

    /**
     * 2 x CR2032 in series (about 6 V) on the RAW pin of a Pro Mini 3.3 V, through its regulator.
     * The regulator's quiescent current dominates the consumption.
     */
    CR2032_X2_SERIES(150, 0.08, new int[][]{{5800, 100}, {5400, 50}, {5000, 20}, {4600, 5}, {4200, 0}}, 4600, 800),
    /**
     * 2 x CR2032 in parallel directly on VCC (regulator and power LED removed).
     */
    CR2032_X2_PARALLEL(300, 0.005, new int[][]{{2900, 100}, {2700, 50}, {2500, 20}, {2300, 5}, {2000, 0}}, 2300, 400);

    /**
     * Charge of one wake up : about 60 ms awake at 20 mA.
     */
    static final double CHARGE_PER_EVENT_MAH = 60 * 20 / 3_600_000.0;

    private final double usableCapacityMah;
    private final double sleepCurrentMa;
    private final int[][] curve;
    private final int replaceMillivolts;
    private final int maxSagMillivolts;

    BatteryProfile(double usableCapacityMah, double sleepCurrentMa, int[][] curve, int replaceMillivolts, int maxSagMillivolts) {
        this.usableCapacityMah = usableCapacityMah;
        this.sleepCurrentMa = sleepCurrentMa;
        this.curve = curve;
        this.replaceMillivolts = replaceMillivolts;
        this.maxSagMillivolts = maxSagMillivolts;
    }

    public double usableCapacityMah() {
        return usableCapacityMah;
    }

    public double sleepCurrentMa() {
        return sleepCurrentMa;
    }

    public int replaceMillivolts() {
        return replaceMillivolts;
    }

    public int maxSagMillivolts() {
        return maxSagMillivolts;
    }

    /**
     * @return the remaining charge (0..100) for a voltage under load, interpolated on the discharge curve
     */
    public double percentForLoadVoltage(double millivolts) {
        if (millivolts >= curve[0][0])
            return 100;
        for (int i = 1; i < curve.length; i++) {
            if (millivolts >= curve[i][0]) {
                double ratio = (millivolts - curve[i][0]) / (curve[i - 1][0] - curve[i][0]);
                return curve[i][1] + ratio * (curve[i - 1][1] - curve[i][1]);
            }
        }
        return 0;
    }
}
