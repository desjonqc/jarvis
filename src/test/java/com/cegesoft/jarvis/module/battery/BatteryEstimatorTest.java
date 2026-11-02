package com.cegesoft.jarvis.module.battery;

import com.cegesoft.jarvis.radio.protocol.BatteryMeasurement;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class BatteryEstimatorTest {

    private static final long DAY = TimeUnit.DAYS.toMillis(1);
    private static final long T0 = 1_800_000_000_000L;

    private final BatteryEstimator estimator = new BatteryEstimator(BatteryProfile.CR2032_X2_SERIES);
    private int counter;

    private void press(long time, int restMv, int loadMv, int temperature, boolean powerOn) {
        counter++;
        estimator.update(new BatteryMeasurement(restMv, loadMv, temperature, powerOn ? BatteryMeasurement.FLAG_POWER_ON_RESET : 0), counter, time);
    }

    private void press(long time, int restMv, int loadMv) {
        press(time, restMv, loadMv, 20, false);
    }

    @Test
    void freshBatteryOnThePlateauIsOkAndSlowlyDecreases() {
        press(T0, 6300, 6000, 20, true);
        assertEquals(BatteryState.OK, estimator.getState());
        assertEquals(100, estimator.getPercent(), 0.5);
        press(T0 + 30 * DAY, 6200, 5950);
        // the voltage says nothing yet ; the charge counting does : 30 days at 0.08 mA = 58 mAh of 150
        assertEquals(61, estimator.getPercent(), 2);
        assertEquals(BatteryState.OK, estimator.getState());
    }

    @Test
    void lowVoltageUnderLoadAsksForAReplacement() {
        press(T0, 6300, 6000, 20, true);
        for (int i = 1; i <= 10; i++)
            press(T0 + i * DAY, 5000, 4400);
        assertEquals(BatteryState.REPLACE, estimator.getState());
        assertTrue(estimator.getPercent() < 5);
    }

    @Test
    void aTemporaryColdDipDoesNotAskForAReplacement() {
        press(T0, 6300, 6000, 20, true);
        for (int i = 0; i < 10; i++)
            press(T0 + DAY, 6000, 5900);
        // two very cold mornings : the median of the last 5 measurements ignores them
        press(T0 + 2 * DAY, 5200, 4300, -8, false);
        press(T0 + 3 * DAY, 5200, 4300, -8, false);
        assertNotEquals(BatteryState.REPLACE, estimator.getState());
        press(T0 + 4 * DAY, 6000, 5900);
        assertEquals(BatteryState.OK, estimator.getState());
    }

    @Test
    void coldNeedsThreeConsecutiveLowReadings() {
        BatteryEstimator cold = new BatteryEstimator(BatteryProfile.CR2032_X2_PARALLEL);
        cold.update(new BatteryMeasurement(2250, 2250, -5, 1), 1, T0); // first measurement already low, in the cold
        assertNotEquals(BatteryState.REPLACE, cold.getState());
        cold.update(new BatteryMeasurement(2250, 2250, -5, 0), 2, T0 + 1000);
        assertNotEquals(BatteryState.REPLACE, cold.getState());
        cold.update(new BatteryMeasurement(2250, 2250, -5, 0), 3, T0 + 2000);
        assertEquals(BatteryState.REPLACE, cold.getState());

        BatteryEstimator warm = new BatteryEstimator(BatteryProfile.CR2032_X2_PARALLEL);
        warm.update(new BatteryMeasurement(2250, 2250, 20, 1), 1, T0);
        assertEquals(BatteryState.REPLACE, warm.getState(), "at room temperature one reading is enough");
    }

    @Test
    void bigVoltageSagWarnsEarly() {
        press(T0, 6300, 5300, 20, true);
        assertEquals(BatteryState.REPLACE_SOON, estimator.getState(), "1 V drop under load : high internal resistance");
    }

    @Test
    void replacementIsDetectedAndTheLifetimeIsLearned() {
        press(T0, 6300, 6000, 20, true);
        for (int i = 1; i <= 5; i++)
            press(T0 + i * DAY, 4900, 4300);
        assertEquals(BatteryState.REPLACE, estimator.getState());

        long change = T0 + 100 * DAY; // the battery lasted 100 days
        press(change, 6300, 6000, 20, true);
        assertEquals(BatteryState.OK, estimator.getState());
        assertEquals(100, estimator.getPercent(), 0.5);
        // learned : 100 days of sleep at 0.08 mA = 192 mAh (more than the 150 mAh assumed)
        assertEquals(192, estimator.getLearnedCapacityMah(), 1);
        press(change + 50 * DAY, 6200, 5950);
        assertEquals(50, estimator.getPercent(), 2, "half of the learned lifetime");
    }

    @Test
    void replacementWithoutPowerOnFlagIsDetectedByTheVoltageRise() {
        press(T0, 6300, 6000, 20, true);
        press(T0 + 60 * DAY, 5400, 5100);
        press(T0 + 61 * DAY, 6300, 6000);
        assertEquals(100, estimator.getPercent(), 0.5);
    }

    @Test
    void daysLeftNeedsAWeekOfData() {
        press(T0, 6300, 6000, 20, true);
        press(T0 + 3 * DAY, 6250, 5980);
        assertNull(estimator.daysLeft(T0 + 3 * DAY));
        press(T0 + 10 * DAY, 6250, 5980);
        Integer days = estimator.daysLeft(T0 + 10 * DAY);
        assertNotNull(days);
        // 150 mAh at about 1.92 mAh per day : 78 days in all, 10 already spent
        assertEquals(68, days, 3);
    }

    @Test
    void stateSurvivesASaveAndLoad() throws Exception {
        press(T0, 6300, 6000, 20, true);
        press(T0 + 10 * DAY, 6200, 5950);
        JSONObject json = (JSONObject) new JSONParser().parse(estimator.save().toJSONString());
        BatteryEstimator loaded = new BatteryEstimator();
        loaded.load(json);
        assertEquals(estimator.getPercent(), loaded.getPercent());
        assertEquals(estimator.getState(), loaded.getState());
        assertEquals(BatteryProfile.CR2032_X2_SERIES, loaded.getProfile());
        assertEquals(2, loaded.historySize());
        assertEquals(estimator.daysLeft(T0 + 10 * DAY), loaded.daysLeft(T0 + 10 * DAY));
    }
}
