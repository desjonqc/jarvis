package com.cegesoft.jarvis.module.battery;

import com.cegesoft.jarvis.properties.IAutoSerializable;
import com.cegesoft.jarvis.properties.SavedItem;
import com.cegesoft.jarvis.radio.protocol.BatteryMeasurement;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.concurrent.TimeUnit;

/**
 * Estimates the remaining charge of a coin cell powered node from two complementary estimators :
 * <ul>
 *     <li>charge counting (wake ups + sleep time), smooth from the first day, when the voltage tells nothing yet ;</li>
 *     <li>voltage under radio load, reliable at the end of life, when the coin cell's internal resistance rises.</li>
 * </ul>
 * The displayed value is the lowest of both : better warn too early than too late.
 * The consumption model is calibrated with the real lifetime of the previous battery.
 * <p>
 * Thread-safe (synchronized) : updated by the radio dispatch thread, read by the web API.
 */
public class BatteryEstimator implements IAutoSerializable {

    static final int MEDIAN_WINDOW = 5;
    static final double SMOOTHING = 0.5;
    static final int REPLACEMENT_RISE_MILLIVOLTS = 300;
    static final int COLD_TEMPERATURE = 10;
    static final int COLD_CONFIRMATIONS = 3;
    static final int MIN_DAYS_FOR_PREDICTION = 7;
    static final int HISTORY_DAYS = 365;
    static final double REPLACE_SOON_PERCENT = 20;
    private static final long DAY_MS = TimeUnit.DAYS.toMillis(1);
    private static final double HOUR_MS = TimeUnit.HOURS.toMillis(1);

    @SavedItem
    private BatteryProfile profile = BatteryProfile.CR2032_X2_SERIES;
    @SavedItem
    private long installedAt;
    @SavedItem
    private long wakeUps;
    @SavedItem
    private int lastCounter = -1;
    @SavedItem
    private double smoothedLoadMillivolts = -1;
    @SavedItem
    private int lastRestMillivolts = -1;
    @SavedItem
    private int lastTemperature = 20;
    @SavedItem
    private int lowStreak;
    @SavedItem
    private boolean reachedEndOfLife;
    @SavedItem
    private double learnedCapacityMah = -1;
    @SavedItem
    private double percent = 100;
    @SavedItem
    private BatteryState state = BatteryState.OK;
    @SavedItem
    private ArrayList<BatteryPoint> history = new ArrayList<>();
    private final Deque<Integer> recentLoads = new ArrayDeque<>();

    public BatteryEstimator() {
    }

    public BatteryEstimator(BatteryProfile profile) {
        this.profile = profile;
    }

    /**
     * @param counter the node's wake up counter (e.g. PULSE_EVT counter), or null for a wake up without counter (heartbeat)
     */
    public synchronized void update(BatteryMeasurement measurement, Integer counter, long now) {
        boolean voltageRise = lastRestMillivolts > 0 && measurement.restMillivolts() - lastRestMillivolts > REPLACEMENT_RISE_MILLIVOLTS;
        if (installedAt == 0 || measurement.powerOnReset() || voltageRise)
            this.onReplaced(now);
        this.countWakeUps(counter);
        this.smooth(measurement.loadMillivolts());
        lastRestMillivolts = measurement.restMillivolts();
        lastTemperature = measurement.temperature();
        this.evaluate(measurement.restMillivolts() - measurement.loadMillivolts(), now);
        this.record(measurement, now);
    }

    /**
     * The node reported transmissions which failed : with a low voltage, it means the battery can't power the radio anymore.
     */
    public synchronized void onTransmissionFailures(int failures) {
        if (failures > 0 && smoothedLoadMillivolts > 0 && profile.percentForLoadVoltage(smoothedLoadMillivolts) < REPLACE_SOON_PERCENT) {
            state = BatteryState.REPLACE;
            reachedEndOfLife = true;
        }
    }

    private void onReplaced(long now) {
        if (installedAt > 0 && reachedEndOfLife) {
            // The previous battery was used up : its real capacity, in the units of our consumption model
            learnedCapacityMah = consumedMah(now);
        }
        installedAt = now;
        wakeUps = 0;
        lastCounter = -1;
        smoothedLoadMillivolts = -1;
        lowStreak = 0;
        reachedEndOfLife = false;
        recentLoads.clear();
        history.clear();
    }

    private void countWakeUps(Integer counter) {
        if (counter == null) {
            wakeUps++;
            return;
        }
        int delta = lastCounter < 0 ? 1 : (counter - lastCounter) & 0xFFFF;
        // A huge jump means the node restarted its counter : count only this wake up
        wakeUps += delta > 0 && delta < 1000 ? delta : 1;
        lastCounter = counter;
    }

    private void smooth(int loadMillivolts) {
        recentLoads.addLast(loadMillivolts);
        if (recentLoads.size() > MEDIAN_WINDOW)
            recentLoads.removeFirst();
        int[] sorted = recentLoads.stream().mapToInt(Integer::intValue).toArray();
        Arrays.sort(sorted);
        double median = sorted[sorted.length / 2];
        smoothedLoadMillivolts = smoothedLoadMillivolts < 0 ? median : SMOOTHING * median + (1 - SMOOTHING) * smoothedLoadMillivolts;
    }

    private void evaluate(int sagMillivolts, long now) {
        double coulombPercent = coulombPercent(now);
        double voltagePercent = profile.percentForLoadVoltage(smoothedLoadMillivolts);
        percent = Math.min(coulombPercent, voltagePercent);

        boolean low = smoothedLoadMillivolts < profile.replaceMillivolts();
        lowStreak = low ? lowStreak + 1 : 0;
        // In the cold, a coin cell's voltage drops for a while : only a repeated low value counts
        int confirmations = lastTemperature < COLD_TEMPERATURE ? COLD_CONFIRMATIONS : 1;
        if (lowStreak >= confirmations) {
            state = BatteryState.REPLACE;
            reachedEndOfLife = true;
        } else if (state == BatteryState.REPLACE && low) {
            // keep the alert until the battery is replaced
        } else if (percent < REPLACE_SOON_PERCENT || sagMillivolts > profile.maxSagMillivolts()) {
            state = BatteryState.REPLACE_SOON;
        } else {
            state = BatteryState.OK;
        }
    }

    private void record(BatteryMeasurement measurement, long now) {
        long day = now / DAY_MS;
        BatteryPoint last = history.isEmpty() ? null : history.get(history.size() - 1);
        if (last != null && last.day == day) {
            last.loadMillivolts = (int) Math.round(smoothedLoadMillivolts);
            last.restMillivolts = measurement.restMillivolts();
            last.temperature = measurement.temperature();
        } else {
            history.add(new BatteryPoint(day, (int) Math.round(smoothedLoadMillivolts), measurement.restMillivolts(), measurement.temperature()));
            while (history.size() > HISTORY_DAYS)
                history.remove(0);
        }
    }

    private double capacityMah() {
        return learnedCapacityMah > 0 ? learnedCapacityMah : profile.usableCapacityMah();
    }

    private double consumedMah(long now) {
        double hours = Math.max(0, now - installedAt) / HOUR_MS;
        return wakeUps * BatteryProfile.CHARGE_PER_EVENT_MAH + hours * profile.sleepCurrentMa();
    }

    private double coulombPercent(long now) {
        return Math.max(0, Math.min(100, 100 * (1 - consumedMah(now) / capacityMah())));
    }

    public synchronized double getPercent() {
        return Math.round(percent * 10) / 10.0;
    }

    public synchronized BatteryState getState() {
        return state;
    }

    /**
     * @return the estimated days before the battery must be replaced, or null without at least 7 days of data
     */
    public synchronized Integer daysLeft(long now) {
        if (installedAt == 0)
            return null;
        double elapsedDays = (now - installedAt) / (double) DAY_MS;
        if (elapsedDays < MIN_DAYS_FOR_PREDICTION)
            return null;
        double consumed = consumedMah(now);
        double perDay = consumed / elapsedDays;
        double days = Math.max(0, capacityMah() - consumed) / perDay;
        double coulombPercent = coulombPercent(now);
        double voltagePercent = profile.percentForLoadVoltage(smoothedLoadMillivolts);
        if (coulombPercent > 0 && voltagePercent < coulombPercent)
            days *= voltagePercent / coulombPercent; // the voltage says the battery is more worn than the model thinks
        return (int) Math.round(days);
    }

    public synchronized BatteryProfile getProfile() {
        return profile;
    }

    public synchronized void setProfile(BatteryProfile profile) {
        this.profile = profile;
    }

    synchronized double getLearnedCapacityMah() {
        return learnedCapacityMah;
    }

    synchronized int historySize() {
        return history.size();
    }

    @Override
    public IAutoSerializable getBase() {
        return this;
    }

    @Override
    public synchronized org.json.simple.JSONObject save() {
        return IAutoSerializable.super.save();
    }

    @Override
    public synchronized void load(org.json.simple.JSONObject json) {
        IAutoSerializable.super.load(json);
    }
}
