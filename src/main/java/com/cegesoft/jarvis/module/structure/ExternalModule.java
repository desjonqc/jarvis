package com.cegesoft.jarvis.module.structure;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.events.EventHandler;
import com.cegesoft.jarvis.events.type.module.*;
import com.cegesoft.jarvis.module.ModuleManager;
import com.cegesoft.jarvis.module.ModuleType;
import com.cegesoft.jarvis.module.interfaces.IExternalModule;
import com.cegesoft.jarvis.properties.SavedItem;
import com.cegesoft.jarvis.service.serial.SerialService;
import com.cegesoft.jarvis.task.TaskId;
import com.cegesoft.jarvis.task.TaskManager;
import org.apache.commons.lang3.ArrayUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.function.Consumer;

/**
 * Created by HoxiSword on 14/05/2020 for JARVIS
 */
public abstract class ExternalModule implements IExternalModule {

    private final HashMap<String, Consumer<String>> commands = new HashMap<>();
    @SavedItem
    private String id;
    @SavedItem
    private ModuleType type;
    private final Object messageSync = new Object();
    @SavedItem
    private ModuleState state = ModuleState.DISCONNECTED;
    @SavedItem
    private double batteryLevel = 100.0;
    private int receptionSignalCache = 10;
    @SavedItem
    private int receptionSignal = 10;
    private String connectionBuffer = "";
    private TaskId commandTimerId;

    private int disconnectionLoopCount = 0;
    private TaskId disconnectionTaskId;

    public ExternalModule(String id, ModuleType type) {
        this.id = id;
        this.type = type;
        Jarvis.getEventManager().registerListener(this);
    }

    public ExternalModule() {
    }

    @Override
    public void postLoad() {
        Jarvis.getEventManager().registerListener(this);
        this.startDisconnectionTask();
        this.registerCommands();
    }

    private void startDisconnectionTask() {
        if (this.state != ModuleState.DISCONNECTED)
            return;
        TaskManager.cancelTask(disconnectionTaskId);
        this.disconnectionLoopCount = 0;
        this.disconnectionTaskId = TaskManager.scheduleTaskTimer(() -> {
            if (this.state != ModuleState.DISCONNECTED) {
                TaskManager.cancelTask(this.disconnectionTaskId);
                return;
            }
            this.write("disconnect");
            this.disconnectionLoopCount ++;
            if (this.disconnectionLoopCount > 5) {
                TaskManager.cancelTask(this.disconnectionTaskId);
            }
        }, 0, 120000);
    }

    protected void registerCommand(String command, Consumer<String> consumer) {
        commands.remove(command);
        commands.put(command, consumer);
    }

    protected void unregisterCommand(String command) {
        commands.remove(command);
    }

    protected void executeCommand(String command, Object arguments, boolean first) {
        if (this.state == ModuleState.SLEEPING)
            return;
        if (first) {
            TaskManager.cancelTask(this.commandTimerId);
            this.commandTimerId = TaskManager.scheduleTaskLater(() -> {
                this.executeCommand(command, arguments, false);
            }, 5000);
        }
        if (arguments != null)
            this.write(command + ":" + arguments);
        else
            this.write(command);
    }

    @EventHandler
    public void onReceive(ModuleMessageReceiveEvent event) {
        if (!event.getModuleId().equals(this.id))
            return;

        if (!event.getMessage().contains("recep")) {
            synchronized (messageSync) {
                connectionBuffer = event.getMessage();
                messageSync.notifyAll();
            }
        }
        String[] args = event.getMessage().split(":");

        if (!event.getMessage().contains("recep") && state == ModuleState.SLEEPING) {
            this.log("Module " + id + " awaken");
            state = ModuleState.WORKING;
            this.checkState();
        }

        if (state == ModuleState.WORKING || state == ModuleState.SIGNAL_LOW || state == ModuleState.BATTERY_LOW || state == ModuleState.DISABLED) {
            try {
                switch (args[0]) {
                    case "connected":
                        this.write("connected");
                        break;
                    case "sleep":
                        this.log("Module " + id + " is sleeping.");
                        this.state = ModuleState.SLEEPING;
                        this.stateChanged(ModuleState.SLEEPING);
                        break;
                    case "batt":
                        this.batteryLevel = Double.parseDouble(args[1]);
                        Jarvis.getEventManager().callEvent(new ModuleBatteryLevelUpdateEvent(this));
                        break;
                    case "recep":
                        this.receptionSignalCache = this.receptionSignal;
                        this.receptionSignal = Integer.parseInt(args[1]);
                        break;
                    case "enab":
                        if (!(args[1].equals("true") || args[1].equals("false")))
                            break;
                        TaskManager.cancelTask(this.commandTimerId);
                        boolean disabled = this.state == ModuleState.DISABLED;
                        this.state = Boolean.parseBoolean(args[1]) ? ModuleState.WORKING : ModuleState.DISABLED;
                        if (disabled != (this.state == ModuleState.DISABLED))
                            Jarvis.getEventManager().callEvent(new ModuleEnableChangeEvent(this, this.state != ModuleState.DISABLED));
                        break;
                    default:
                        if (commands.containsKey(args[0])) {
                            TaskManager.cancelTask(this.commandTimerId);
                            String key = args[0];
                            commands.get(key).accept(args[1]);
                            Jarvis.getEventManager().callEvent(new ModuleCommandResultReceiveEvent(this, key));
                        }
                        break;
                }
                this.checkState();
            } catch (ArrayIndexOutOfBoundsException ignored) {
            }
        } else if (state == ModuleState.DISCONNECTED && !args[0].equals("recep")) {
            this.write("disconnect");
        }
    }

    @Override
    public void checkState() {
        if (state == ModuleState.DISABLED || state == ModuleState.SLEEPING)
            return;
        state = ModuleState.WORKING;
        this.stateChanged(state);

        if (receptionSignal < 3) {
            state = ModuleState.SIGNAL_LOW;
            this.stateChanged(state);
            Jarvis.getEventManager().callEvent(new ModuleLowSignalDetectedEvent(this));
        }
        if (batteryLevel < 20) {
            state = ModuleState.BATTERY_LOW;
            this.stateChanged(state);
            Jarvis.getEventManager().callEvent(new ModuleLowPowerDetectedEvent(this));
        }
        if (receptionSignal == 0 && receptionSignalCache == 0) {
            state = ModuleState.DISCONNECTED;
            this.stateChanged(state);
            Jarvis.getEventManager().callEvent(new ModuleDisconnectEvent(this));
            this.startDisconnectionTask();
            removeModule();
        } else if (receptionSignal == 0) {
            this.write("test");
        }
        if (this.state != ModuleState.DISCONNECTED) {
            TaskManager.cancelTask(this.disconnectionTaskId);
        }
    }

    @Override
    public ModuleState getState() {
        return state;
    }

    @Override
    public boolean isEnabled() {
        return state != ModuleState.DISABLED;
    }

    @Override
    public void setEnabled(boolean bool) {
        this.executeCommand("enab", bool, true);
    }

    @Override
    public boolean isConnected() {
        return state != ModuleState.DISCONNECTED && state != ModuleState.CONNECTING;
    }

    @Override
    public double getBatteryLevel() {
        return batteryLevel;
    }

    @Override
    public void updateBatteryLevel() {
        this.write("batt");
    }

    @Override
    public int getReceptionSignal() {
        return receptionSignal;
    }

    @Override
    public boolean connect() {
        Jarvis.getEventManager().callEvent(new ModuleConnectingEvent(this));
        state = ModuleState.CONNECTING;
        for (int i = 0; i < 10; i++) {
            this.write("connect");
            if (this.read().equals("connected")) {
                break;
            } else if (i == 9)
                return false;
        }
        this.write("connected");
        state = ModuleState.WORKING;
        Jarvis.getEventManager().callEvent(new ModuleConnectedEvent(this));
        this.log("New Module connection : " + this.id);
        this.checkState();
        return true;
    }

    protected void write(String message) {
        Jarvis.getServiceManager().getService(SerialService.class).writeSerial(
                ModuleManager.EXTERNAL_MODULE_PORT,
                ArrayUtils.toObject((id + ":" + message + "#").getBytes(StandardCharsets.UTF_8)));
    }

    public String read() {
        synchronized (messageSync) {
            try {
                messageSync.wait(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            String buffer = connectionBuffer;
            connectionBuffer = "";
            return buffer;
        }
    }

    public String getId() {
        return id;
    }

    public ModuleType getType() {
        return type;
    }

    public void removeModule() {
        this.state = ModuleState.DISCONNECTED;
        Jarvis.getEventManager().unregisterListener(this);
    }

    protected void log(String message) {
        String prefix = " [" + this.id + " | " + this.state.name() + " | " + this.type.name() + " | Signal : " + this.receptionSignal + " | Battery : " + this.batteryLevel + "] ";
        Jarvis.getLogger().info(prefix + message);
    }

    protected abstract void stateChanged(ModuleState state);
}
