package com.cegesoft.jarvis.module;

import com.cegesoft.jarvis.module.interfaces.IExternalModule;
import com.cegesoft.jarvis.utils.JsonFile;
import com.cegesoft.jarvis.utils.ReflectionUtil;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.cegesoft.jarvis.Jarvis.getLogger;

public class ModuleFile {

    private final JsonFile file;
    // Entries which couldn't be loaded are written back as they were, so that a bug can't erase a module's configuration.
    private final List<Object> unloadable = new ArrayList<>();

    public ModuleFile(File dataDir) {
        this.file = new JsonFile(new File(dataDir, "Module/modules.json").toPath());
    }

    @SuppressWarnings("unchecked")
    public synchronized void saveModules(Collection<IExternalModule> modules) {
        JSONArray json = new JSONArray();
        for (IExternalModule module : modules) {
            JSONObject object = new JSONObject();
            object.put("module", module.save());
            object.put("class", module.getClass().getName());
            json.add(object);
        }
        json.addAll(unloadable);
        try {
            file.write(json);
        } catch (IOException e) {
            getLogger().error("Can't save modules in " + file.getPath() + " : ", e);
        }
    }

    public synchronized Map<String, IExternalModule> loadModules() {
        unloadable.clear();
        Map<String, IExternalModule> modules = new HashMap<>();
        for (Object obj : file.readArray()) {
            try {
                JSONObject object = (JSONObject) obj;
                Class<? extends IExternalModule> tClass = Class.forName((String) object.get("class")).asSubclass(IExternalModule.class);
                IExternalModule module = ReflectionUtil.newInstance(tClass);
                module.load((JSONObject) object.get("module"));
                modules.put(module.getId(), module);
            } catch (ReflectiveOperationException | RuntimeException e) {
                unloadable.add(obj);
                getLogger().error("Can't load module " + obj + " : ", e);
            }
        }
        getLogger().info(modules.size() + " module(s) loaded.");
        return modules;
    }

}
