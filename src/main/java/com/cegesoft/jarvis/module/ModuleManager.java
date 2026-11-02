package com.cegesoft.jarvis.module;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.JarvisConfig;
import com.cegesoft.jarvis.module.interfaces.IExternalModule;
import com.cegesoft.jarvis.module.schedule.ModuleScheduleManager;
import com.cegesoft.jarvis.radio.link.FrameTransport;
import com.cegesoft.jarvis.radio.link.NackException;
import com.cegesoft.jarvis.radio.link.NodeProfile;
import com.cegesoft.jarvis.radio.link.OfflineTransport;
import com.cegesoft.jarvis.radio.protocol.Frame;
import com.cegesoft.jarvis.radio.protocol.Hello;
import com.cegesoft.jarvis.radio.protocol.Opcode;
import com.cegesoft.jarvis.radio.protocol.Payloads;
import com.cegesoft.jarvis.task.TaskId;
import com.cegesoft.jarvis.task.TaskManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by HoxiSword on 14/05/2020 for JARVIS
 * <p>
 * Modules are identified by their radio node id. A module introduces itself with HELLO (at boot, or when the hub
 * asks with DISCOVER) and is answered with WELCOME.
 */
public class ModuleManager {

    private static final long SILENCE_CHECK_INTERVAL_MS = 30_000;

    private final ModuleFile file;
    private final ModuleScheduleManager scheduleManager;
    private final Map<String, IExternalModule> modules = new ConcurrentHashMap<>();
    private volatile FrameTransport transport = new OfflineTransport();
    private TaskId silenceCheckTask;

    public ModuleManager(JarvisConfig config) {
        this.file = new ModuleFile(config.getDataDir());
        this.scheduleManager = new ModuleScheduleManager(config.getDataDir());
    }

    /**
     * Called by the radio service : the modules start talking through this transport.
     */
    public void attachTransport(FrameTransport transport) {
        this.transport = transport;
        transport.setReceiver(this::onFrame);
        for (IExternalModule module : modules.values()) {
            if (module instanceof com.cegesoft.jarvis.module.structure.ExternalModule external)
                transport.configureNode(module.getNodeId(), new NodeProfile(external.isSleeper(), 0));
        }
    }

    /**
     * Ask every listening module to introduce itself (after a hub restart).
     */
    public void discover() {
        transport.broadcast(Opcode.DISCOVER, new byte[0]);
    }

    public FrameTransport getTransport() {
        return transport;
    }

    private void onFrame(Frame frame) {
        String id = String.valueOf(frame.src());
        if (frame.opcode() == Opcode.HELLO && !frame.reply()) {
            this.onHello(frame, id);
            return;
        }
        IExternalModule module = modules.get(id);
        if (module == null) {
            // A module we don't know (data lost, new module...) : ask it to introduce itself
            transport.send(frame.src(), Opcode.DISCOVER, new byte[0]);
            return;
        }
        module.onFrame(frame);
    }

    private void onHello(Frame frame, String id) {
        Hello hello;
        try {
            hello = Hello.read(frame);
        } catch (IllegalArgumentException e) {
            transport.reply(frame, Opcode.NACK, new byte[]{Payloads.u8(NackException.INVALID_PAYLOAD)});
            return;
        }
        ModuleType type = ModuleType.fromCode(hello.type());
        if (type == null) {
            Jarvis.getLogger().warn("Node " + id + " has an unknown module type : " + hello.type());
            transport.reply(frame, Opcode.NACK, new byte[]{Payloads.u8(NackException.INVALID_PAYLOAD)});
            return;
        }
        IExternalModule module = modules.compute(id, (key, existing) -> {
            if (existing != null && existing.getType() == type)
                return existing;
            if (existing != null)
                Jarvis.getLogger().warn("Node " + id + " was a " + existing.getType() + ", it is now a " + type);
            return type.getModule(key);
        });
        if (module == null)
            return;
        transport.configureNode(frame.src(), new NodeProfile(hello.sleeps(), hello.via()));
        module.onHello(hello);
        transport.reply(frame, Opcode.WELCOME, module.welcomePayload());
    }

    private void checkSilentModules() {
        long now = System.currentTimeMillis();
        for (IExternalModule module : modules.values()) {
            if (module.isConnected() && now - module.getLastSeen() > module.getSilenceTimeoutMs())
                module.markDisconnected();
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends IExternalModule> T getModule(String id) {
        return id == null ? null : (T) modules.get(id);
    }

    public List<IExternalModule> getModules() {
        return new ArrayList<>(modules.values());
    }

    public ModuleScheduleManager getScheduleManager() {
        return scheduleManager;
    }

    public void save() {
        this.file.saveModules(modules.values());
    }

    public void unload() {
        TaskManager.cancelTask(silenceCheckTask);
        this.scheduleManager.unload();
        this.save();
    }

    public void load() {
        long now = System.currentTimeMillis();
        for (IExternalModule module : this.file.loadModules().values()) {
            if (!isValidNodeId(module.getId())) {
                Jarvis.getLogger().warn("Module " + module.getId() + " ignored : its id is not a radio node id (1-254), it must be reflashed.");
                continue;
            }
            // Grace period : modules have their silence timeout to show up after a hub restart
            module.setLastSeen(now);
            modules.put(module.getId(), module);
        }
        this.scheduleManager.load();
        this.silenceCheckTask = TaskManager.scheduleTaskTimer(this::checkSilentModules, "MODULE-SILENCE-CHECK",
                SILENCE_CHECK_INTERVAL_MS, SILENCE_CHECK_INTERVAL_MS);
    }

    static boolean isValidNodeId(String id) {
        try {
            int node = Integer.parseInt(id);
            return node > Frame.HUB && node < Frame.BROADCAST;
        } catch (NumberFormatException e) {
            return false;
        }
    }

}
