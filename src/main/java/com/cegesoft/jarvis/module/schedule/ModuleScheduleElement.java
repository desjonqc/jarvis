package com.cegesoft.jarvis.module.schedule;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.module.activator.ActivatorState;
import com.cegesoft.jarvis.module.structure.ExternalActivator;
import com.cegesoft.jarvis.module.structure.ExternalModule;
import org.json.simple.JSONObject;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public abstract class ModuleScheduleElement {

    /**
     * Wait for the module's confirmation, so that the next elements (e.g. WAIT) start after the action really happened.
     */
    static void await(CompletableFuture<?> command, String description) {
        try {
            command.get(Jarvis.getConfig().getCommandTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            Jarvis.getLogger().warn("Schedule : " + description + " failed : " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
        }
    }

    public static ModuleScheduleElement load(JSONObject elemObj) throws Exception {
        ModuleSchedule.ScheduleElementType type = ModuleSchedule.ScheduleElementType.valueOf((String) elemObj.get("type"));
        JSONObject args = (JSONObject) elemObj.get("args");
        switch (type) {
            case ACTIVATOR:
                return new ActivateScheduleElement((String) args.get("id"), ActivatorState.valueOf((String) args.get("value")));
            case WAIT:
                return new WaitScheduleElement((long) args.get("value"));
            case ENABLE:
                return new EnableScheduleElement((String) args.get("id"), (boolean) args.get("value"));
        }
        throw new Exception("Invalid Element Type");
    }

    public abstract void execute();

    public static class WaitScheduleElement extends ModuleScheduleElement {

        private final long timing;

        public WaitScheduleElement(long timing) {
            this.timing = timing;
        }

        @Override
        public void execute() {
            try {
                Thread.sleep(timing);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static class EnableScheduleElement extends ModuleScheduleElement {

        private final String moduleId;
        private final boolean enable;

        public EnableScheduleElement(String moduleId, boolean enable) {
            this.moduleId = moduleId;
            this.enable = enable;
        }

        @Override
        public void execute() {
            ExternalModule module = Jarvis.getModuleManager().getModule(this.moduleId);
            if (module != null && module.isConnected() && (module.isEnabled() != enable)) {
                await(module.setEnabled(enable), (enable ? "enable " : "disable ") + moduleId);
            }
        }
    }

    public static class ActivateScheduleElement extends ModuleScheduleElement {

        private final String moduleId;
        private final ActivatorState state;

        public ActivateScheduleElement(String moduleId, ActivatorState state) {
            this.moduleId = moduleId;
            this.state = state;
        }

        public String getModuleId() {
            return moduleId;
        }

        @Override
        public void execute() {
            ExternalModule module = Jarvis.getModuleManager().getModule(this.moduleId);
            if (module instanceof ExternalActivator && module.isConnected()) {
                await(((ExternalActivator) module).write(state), "set " + moduleId + " to " + state);
            }
        }
    }
}
