package com.cegesoft.jarvis.alert;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.events.EventHandler;
import com.cegesoft.jarvis.events.Listener;
import com.cegesoft.jarvis.events.type.EventCalledEvent;
import com.cegesoft.jarvis.events.type.module.ModuleEvent;
import com.cegesoft.jarvis.module.battery.BatteryEstimator;
import com.cegesoft.jarvis.module.battery.BatteryState;
import com.cegesoft.jarvis.module.interfaces.IExternalModule;
import com.cegesoft.jarvis.module.structure.ExternalModule;
import com.cegesoft.jarvis.radio.RadioService;
import com.cegesoft.jarvis.task.TaskId;
import com.cegesoft.jarvis.task.TaskManager;
import com.cegesoft.jarvis.utils.JsonFile;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

import static com.cegesoft.jarvis.Jarvis.getLogger;

/**
 * Opens an alert when a module gets disconnected, its battery or its signal gets low, or the radio stops ; and
 * resolves it as soon as the condition disappears. Every module event re-evaluates the module's conditions.
 */
public class AlertManager implements Listener {

    static final int MAX_RESOLVED_KEPT = 100;
    private static final long HUB_CHECK_INTERVAL_MS = 60_000;
    private static final Set<AlertType> MODULE_TYPES = EnumSet.of(AlertType.MODULE_DISCONNECTED, AlertType.BATTERY_LOW,
            AlertType.BATTERY_CRITICAL, AlertType.SIGNAL_LOW);

    private final JsonFile file;
    private final Executor saver;
    private final List<Alert> alerts = new ArrayList<>();
    private long nextId = 1;
    private TaskId hubCheckTask;

    /**
     * @param saver runs the file writes (module events come from the radio thread : the file is written elsewhere)
     */
    public AlertManager(File dataDir, Executor saver) {
        this.file = new JsonFile(new File(dataDir, "alerts.json").toPath());
        this.saver = saver;
    }

    public void load() {
        loadFile();
        Jarvis.getEventManager().registerListener(this);
        hubCheckTask = TaskManager.scheduleTaskTimer(this::checkHub, "ALERTS-HUB-CHECK", 15_000, HUB_CHECK_INTERVAL_MS);
    }

    synchronized void loadFile() {
        for (Object obj : file.readArray()) {
            try {
                Alert alert = Alert.fromJson((JSONObject) obj);
                alerts.add(alert);
                nextId = Math.max(nextId, alert.id() + 1);
            } catch (RuntimeException e) {
                getLogger().warn("Alert ignored : " + e);
            }
        }
    }

    public void unload() {
        TaskManager.cancelTask(hubCheckTask);
        Jarvis.getEventManager().unregisterListener(this);
        save();
    }

    @EventHandler
    public void onEvent(EventCalledEvent event) {
        if (event.getEventCalled() instanceof ModuleEvent moduleEvent)
            checkModule(moduleEvent.getModule());
    }

    void checkModule(IExternalModule module) {
        if (!module.isConnected()) {
            // The last known battery and signal can't change while it is silent : only the disconnection matters.
            update(module.getId(), EnumSet.of(AlertType.MODULE_DISCONNECTED), EnumSet.of(AlertType.MODULE_DISCONNECTED));
            return;
        }
        Set<AlertType> conditions = EnumSet.noneOf(AlertType.class);
        if (module.getReceptionSignal() < 3)
            conditions.add(AlertType.SIGNAL_LOW);
        BatteryEstimator battery = module instanceof ExternalModule external ? external.getBattery() : null;
        if (battery != null) {
            BatteryState state = battery.getState();
            if (state == BatteryState.REPLACE)
                conditions.add(AlertType.BATTERY_CRITICAL);
            else if (state == BatteryState.REPLACE_SOON || module.getBatteryLevel() < 20)
                conditions.add(AlertType.BATTERY_LOW);
        }
        update(module.getId(), conditions, MODULE_TYPES);
    }

    private void checkHub() {
        RadioService radio = Jarvis.getServiceManager().getService(RadioService.class);
        boolean down = radio == null || !radio.isRadioRunning();
        update(null, down ? EnumSet.of(AlertType.RADIO_DOWN) : EnumSet.noneOf(AlertType.class), EnumSet.of(AlertType.RADIO_DOWN));
    }

    /**
     * @param conditions the alert types whose condition is currently true
     * @param checked    the alert types evaluated (the other ones are left as they are)
     */
    synchronized void update(String moduleId, Set<AlertType> conditions, Set<AlertType> checked) {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (AlertType type : checked) {
            int index = indexOfActive(moduleId, type);
            boolean condition = conditions.contains(type);
            if (condition && index < 0) {
                alerts.add(new Alert(nextId++, type, moduleId, now, 0, false));
                getLogger().warn("Alert : " + type + (moduleId != null ? " on module " + moduleId : ""));
                changed = true;
            } else if (!condition && index >= 0) {
                alerts.set(index, alerts.get(index).resolve(now));
                changed = true;
            }
        }
        if (changed) {
            trim();
            saveLater();
        }
    }

    private int indexOfActive(String moduleId, AlertType type) {
        for (int i = 0; i < alerts.size(); i++) {
            Alert alert = alerts.get(i);
            if (alert.isActive() && alert.type() == type && Objects.equals(alert.moduleId(), moduleId))
                return i;
        }
        return -1;
    }

    private void trim() {
        List<Alert> resolved = alerts.stream().filter(alert -> !alert.isActive())
                .sorted(Comparator.comparingLong(Alert::resolvedAt).reversed()).toList();
        if (resolved.size() > MAX_RESOLVED_KEPT)
            alerts.removeAll(resolved.subList(MAX_RESOLVED_KEPT, resolved.size()));
    }

    /**
     * @return the alerts, newest first
     */
    public synchronized List<Alert> getAlerts() {
        return alerts.stream().sorted(Comparator.comparingLong(Alert::createdAt).reversed().thenComparing(Comparator.comparingLong(Alert::id).reversed())).toList();
    }

    /**
     * @param id null = every alert
     * @return false if there is no alert with this id
     */
    public synchronized boolean markRead(Long id) {
        boolean found = false;
        for (int i = 0; i < alerts.size(); i++) {
            Alert alert = alerts.get(i);
            if (id == null || alert.id() == id) {
                alerts.set(i, alert.markRead());
                found = true;
            }
        }
        if (found)
            saveLater();
        return found || id == null;
    }

    /**
     * Remove the resolved alerts (the history).
     */
    public synchronized void clearResolved() {
        if (alerts.removeIf(alert -> !alert.isActive()))
            saveLater();
    }

    private void saveLater() {
        saver.execute(this::save);
    }

    @SuppressWarnings("unchecked")
    private synchronized void save() {
        JSONArray array = new JSONArray();
        for (Alert alert : alerts)
            array.add(alert.toJson());
        try {
            file.write(array);
        } catch (IOException e) {
            getLogger().error("Can't save alerts in " + file.getPath() + " : ", e);
        }
    }
}
