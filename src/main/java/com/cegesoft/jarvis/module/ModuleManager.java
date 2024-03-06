package com.cegesoft.jarvis.module;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.events.EventHandler;
import com.cegesoft.jarvis.events.Listener;
import com.cegesoft.jarvis.events.type.SerialReceiveEvent;
import com.cegesoft.jarvis.events.type.module.ModuleMessageReceiveEvent;
import com.cegesoft.jarvis.module.interfaces.IExternalModule;
import com.cegesoft.jarvis.module.schedule.ModuleScheduleManager;
import com.cegesoft.jarvis.service.serial.SerialService;
import lombok.Getter;
import org.apache.commons.lang3.ArrayUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by HoxiSword on 14/05/2020 for JARVIS
 */
public class ModuleManager implements Listener {

    public static final String EXTERNAL_MODULE_PORT = Jarvis.isWindows() ? "COM5" : "/dev/ttyS81";

    private final ModuleFile file;
    @Getter
    private final ModuleScheduleManager scheduleManager;
    private final HashMap<String, IExternalModule> modules = new HashMap<>();
    private final ArrayList<String> connectionPending = new ArrayList<>();

    public ModuleManager() {
        this.file = new ModuleFile();
        this.scheduleManager = new ModuleScheduleManager();
        Jarvis.getEventManager().registerListener(this);
    }

    @EventHandler
    public void onReceive(SerialReceiveEvent event) {
        if (!event.getPort().equals(EXTERNAL_MODULE_PORT))
            return;
        String[] receivedSplit = new String(event.getData()).split("#");
        ArrayList<String> reception = new ArrayList<>();
        for (String s : receivedSplit) {
            if (s != null && ((s.contains(":") && !reception.contains(s)) || s.contains("recep")))
                reception.add(s.replace("\n", "").replace("\r", ""));
        }
        for (String received : reception) {
            if (received == null || !received.contains(":"))
                continue;
            try {
                if (!received.startsWith("new")) {
                    String id = received.split(":")[0];
                    if (modules.containsKey(id))
                        Jarvis.getEventManager().callEvent(new ModuleMessageReceiveEvent(id, received.substring(id.length() + 1)));
                    continue;
                }
                if (!connectionPending.contains(received.split(":")[1])) {
                    String id = received.split(":")[1];
                    String type = received.split(":")[2];
                    IExternalModule module;
                    if (modules.containsKey(id))
                        module = modules.getOrDefault(id, null);
                    else {
                        module = ModuleType.getModule(type, id);
                        if (module == null)
                            continue;
                        module.registerCommands();
                        modules.put(id, module);
                    }
                    if (module == null)
                        continue;
                    connectionPending.add(id);
                    new Thread(() -> {
                        module.connect();
                        connectionPending.remove(id);
                    }).start();

                }
            }catch (ArrayIndexOutOfBoundsException ignored) {}
        }
    }

    private void write(String message) {
        Jarvis.getServiceManager().getService(SerialService.class).writeSerial(
                EXTERNAL_MODULE_PORT,
                ArrayUtils.toObject(message.getBytes(StandardCharsets.UTF_8)));
    }

    public <T extends IExternalModule> T getModule(String id) {
        try {
            return (T) modules.get(id);
        } catch (ClassCastException ignored) {
            return null;
        }
    }

    public List<IExternalModule> getModules() {
        return new ArrayList<>(modules.values());
    }

    public void unload() {
        this.scheduleManager.unload();
        this.modules.forEach((id, module) -> module.removeModule());
        this.file.saveModules();
    }

    public void load() {
        this.modules.putAll(this.file.loadModules());
        this.scheduleManager.load();
    }

}
