package com.cegesoft.jarvis.module.schedule;

import com.cegesoft.jarvis.utils.JsonFile;
import org.json.simple.JSONArray;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.cegesoft.jarvis.Jarvis.getLogger;

public class ModuleScheduleFile {

    private final JsonFile file;

    public ModuleScheduleFile(File dataDir) {
        this.file = new JsonFile(new File(dataDir, "Module/schedules.json").toPath());
    }

    @SuppressWarnings("unchecked")
    public void saveSchedules(List<?> schedulesJson) {
        JSONArray json = new JSONArray();
        json.addAll(schedulesJson);
        try {
            file.write(json);
        } catch (IOException e) {
            getLogger().error("Can't save schedules in " + file.getPath() + " : ", e);
        }
    }

    public JSONArray loadSchedules() {
        return file.readArray();
    }

}
