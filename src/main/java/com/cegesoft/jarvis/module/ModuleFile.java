package com.cegesoft.jarvis.module;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.module.interfaces.IExternalModule;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;

import static com.cegesoft.jarvis.Jarvis.getLogger;

public class ModuleFile {

    private final File file;
    private JSONArray json;

    public ModuleFile() {
        this.file = new File(Jarvis.getBaseFolder(), "Module/modules.json");
        try {
            if (!this.file.exists()) {
                if (this.file.getParentFile().isDirectory() && !this.file.getParentFile().exists() && !this.file.getParentFile().mkdirs()) {
                    getLogger().error("Can't create action folder");
                    return;
                }
                if (!this.file.createNewFile()) {
                    getLogger().error("Can't create action file");
                    return;
                }
                this.json = new JSONArray();
            } else {
                this.json = (JSONArray)new JSONParser().parse(new FileReader(file));
            }
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
    }

    public void saveModules() {
        json = new JSONArray();
        for (IExternalModule module : Jarvis.getModuleManager().getModules()) {
            JSONObject object = new JSONObject();
            object.put("module", module.save());
            object.put("class", module.getClass().getName());
            json.add(object);
        }
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(json.toString());
            writer.flush();
        } catch (IOException e) {
            Jarvis.getLogger().error("Can't save WordFile : ", e);
        }
    }

    public HashMap<String, IExternalModule> loadModules(){
        HashMap<String, IExternalModule> modules = new HashMap<>();
        for (Object obj : this.json) {
            try {
                JSONObject object = (JSONObject) obj;
                Class<? extends IExternalModule> tClass = (Class<? extends IExternalModule>) Class.forName((String) object.get("class"));
                IExternalModule module = tClass.getConstructor().newInstance();
                module.load((JSONObject)object.get("module"));
                modules.put(module.getId(), module);
            } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return modules;
    }

}
