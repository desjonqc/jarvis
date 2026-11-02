package com.cegesoft.jarvis.alert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AlertManagerTest {

    private static final Set<AlertType> MODULE = EnumSet.of(AlertType.MODULE_DISCONNECTED, AlertType.SIGNAL_LOW, AlertType.BATTERY_LOW);

    @TempDir
    Path dataDir;

    private AlertManager manager() {
        return new AlertManager(dataDir.toFile(), Runnable::run);
    }

    @Test
    void anAlertIsOpenedOnceAndResolvedWhenTheConditionDisappears() {
        AlertManager alerts = manager();
        alerts.update("2", EnumSet.of(AlertType.SIGNAL_LOW), MODULE);
        alerts.update("2", EnumSet.of(AlertType.SIGNAL_LOW), MODULE);
        assertEquals(1, alerts.getAlerts().size());
        assertTrue(alerts.getAlerts().getFirst().isActive());

        alerts.update("2", EnumSet.noneOf(AlertType.class), MODULE);
        assertFalse(alerts.getAlerts().getFirst().isActive());

        // it comes back : a new alert
        alerts.update("2", EnumSet.of(AlertType.SIGNAL_LOW), MODULE);
        assertEquals(2, alerts.getAlerts().size());
        assertEquals(1, alerts.getAlerts().stream().filter(Alert::isActive).count());
    }

    @Test
    void uncheckedTypesAreLeftAsTheyAre() {
        AlertManager alerts = manager();
        alerts.update("3", EnumSet.of(AlertType.BATTERY_LOW), MODULE);
        // disconnected : only the disconnection is evaluated, the battery alert stays
        alerts.update("3", EnumSet.of(AlertType.MODULE_DISCONNECTED), EnumSet.of(AlertType.MODULE_DISCONNECTED));
        assertEquals(2, alerts.getAlerts().stream().filter(Alert::isActive).count());
    }

    @Test
    void alertsAreSavedReadAndCleared() {
        AlertManager alerts = manager();
        alerts.update(null, EnumSet.of(AlertType.RADIO_DOWN), EnumSet.of(AlertType.RADIO_DOWN));
        alerts.update("2", EnumSet.of(AlertType.SIGNAL_LOW), MODULE);
        alerts.update("2", EnumSet.noneOf(AlertType.class), MODULE);
        assertTrue(alerts.markRead(null));
        assertFalse(alerts.markRead(42L));

        AlertManager reloaded = manager();
        reloaded.loadFile();
        assertEquals(2, reloaded.getAlerts().size());
        assertTrue(reloaded.getAlerts().stream().allMatch(Alert::read));

        reloaded.clearResolved();
        assertEquals(1, reloaded.getAlerts().size());
        assertEquals(AlertType.RADIO_DOWN, reloaded.getAlerts().getFirst().type());
        // ids keep increasing after a restart
        reloaded.update("5", EnumSet.of(AlertType.SIGNAL_LOW), MODULE);
        assertEquals(3, reloaded.getAlerts().getFirst().id());
    }
}
