package com.cegesoft.jarvis;

import com.cegesoft.jarvis.events.EventManager;
import com.cegesoft.jarvis.logging.JLogger;
import com.cegesoft.jarvis.module.ModuleManager;
import com.cegesoft.jarvis.service.ServiceManager;
import com.cegesoft.jarvis.task.TaskManager;

import java.io.File;

/**
 * Created by HoxiSword on 09/04/2020 for JARVIS
 */
public class Jarvis {
    private static JLogger logger;
    private static Jarvis jarvis;
    private final EventManager eventManager;
    private final ServiceManager serviceManager;
    private final ModuleManager moduleManager;
    private final TaskManager taskManager;

    public Jarvis() {
        logger = new JLogger();
        jarvis = this;
        this.eventManager = new EventManager();
        this.taskManager = new TaskManager();
        this.taskManager.start();
        this.serviceManager = new ServiceManager();
        this.moduleManager = new ModuleManager();
        this.moduleManager.load();
        this.serviceManager.waitForServiceLoadFinish();
    }

    public static JLogger getLogger() {
        return logger;
    }

    public static Jarvis getJarvis() {
        return jarvis;
    }

    public static void startJarvis() {
        Thread.currentThread().setName("JARVIS-BOOT");
        new Jarvis();
        getLogger().info("JARVIS Ready !");
    }

    public static void stopJarvis() {
        jarvis.stop();
    }

    public static File getBaseFolder() {
        return new File(isWindows() ? ("JARVIS" + File.separator) : System.getProperty("user.dir"));
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

    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private void stop() {
        this.serviceManager.loadServices();
        this.moduleManager.unload();
        this.taskManager.stop();
    }
}
