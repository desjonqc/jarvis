package com.cegesoft.jarvis.module.schedule;

import com.cegesoft.jarvis.Jarvis;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import static com.cegesoft.jarvis.Jarvis.getLogger;

public class ModuleScheduleFile {

    private final File file;
    private JSONArray json;

    public ModuleScheduleFile() {
        this.file = new File(Jarvis.getBaseFolder(), "Module/schedules.json");
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

    public void saveSchedules() {
        json = new JSONArray();
        for (ModuleSchedule module : Jarvis.getModuleManager().getScheduleManager().getSchedules()) {
            json.add(module.getJson());
        }
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(json.toString());
            writer.flush();
        } catch (IOException e) {
            Jarvis.getLogger().error("Can't save WordFile : ", e);
        }
    }

    public JSONArray loadSchedules(){
        return this.json;
    }

}
