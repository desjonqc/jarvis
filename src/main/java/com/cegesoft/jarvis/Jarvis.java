package com.cegesoft.jarvis;

import com.cegesoft.jarvis.alert.AlertManager;
import com.cegesoft.jarvis.auth.AuthManager;
import com.cegesoft.jarvis.events.EventManager;
import com.cegesoft.jarvis.logging.JLogger;
import com.cegesoft.jarvis.module.ModuleManager;
import com.cegesoft.jarvis.service.ServiceManager;
import com.cegesoft.jarvis.task.TaskManager;

/**
 * Created by HoxiSword on 09/04/2020 for JARVIS
 */
public class Jarvis {
    private static final JLogger logger = new JLogger();
    private static volatile Jarvis jarvis;
    private final JarvisConfig config;
    private final EventManager eventManager;
    private final TaskManager taskManager;
    private final ServiceManager serviceManager;
    private final ModuleManager moduleManager;
    private final AlertManager alertManager;
    private final AuthManager authManager;
    private boolean stopped;

    private Jarvis(JarvisConfig config) {
        jarvis = this;
        this.config = config;
        this.eventManager = new EventManager();
        this.taskManager = new TaskManager();
        this.serviceManager = new ServiceManager(config);
        this.moduleManager = new ModuleManager(config);
        this.alertManager = new AlertManager(config.getDataDir(), task -> taskManager.execute(task, "ALERTS-SAVE"));
        this.authManager = new AuthManager(config);
    }

    private void start() {
        this.taskManager.start();
        // Listens to the module events : registered before the modules start talking
        this.alertManager.load();
        this.moduleManager.load();
        this.authManager.load();
        // Services start last : they feed the event bus and the web API, which both rely on the managers above.
        this.serviceManager.loadServices();
        long autosave = config.getAutosaveIntervalMs();
        if (autosave > 0)
            TaskManager.scheduleTaskTimer(this.moduleManager::save, "AUTOSAVE", autosave, autosave);
    }

    public static JLogger getLogger() {
        return logger;
    }

    public static Jarvis getJarvis() {
        return jarvis;
    }

    public static void startJarvis() {
        Thread.currentThread().setName("JARVIS-BOOT");
        logger.redirectSystemOut();
        new Jarvis(JarvisConfig.fromEnvironment()).start();
        getLogger().info("JARVIS Ready !");
    }

    public static void stopJarvis() {
        if (jarvis != null)
            jarvis.stop();
    }

    public static JarvisConfig getConfig() {
        return jarvis.config;
    }

    public static EventManager getEventManager() {
        return jarvis.eventManager;
    }

    public static ServiceManager getServiceManager() {
        return jarvis.serviceManager;
    }

    public static ModuleManager getModuleManager() {
        return jarvis.moduleManager;
    }

    public static TaskManager getTaskManager() {
        return jarvis.taskManager;
    }

    public static AlertManager getAlertManager() {
        return jarvis.alertManager;
    }

    public static AuthManager getAuthManager() {
        return jarvis.authManager;
    }

    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private synchronized void stop() {
        if (stopped)
            return;
        stopped = true;
        getLogger().info("Stopping JARVIS...");
        this.serviceManager.unloadServices();
        this.moduleManager.unload();
        this.alertManager.unload();
        this.authManager.unload();
        this.taskManager.stop();
        getLogger().info("JARVIS stopped.");
    }
}
