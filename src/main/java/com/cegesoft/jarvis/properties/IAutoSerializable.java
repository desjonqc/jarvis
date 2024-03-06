package com.cegesoft.jarvis.properties;

import com.cegesoft.jarvis.utils.ISerializable;
import org.json.simple.JSONObject;

/**
 * Created by HoxiSword on 15/06/2020 for JARVIS
 */
public interface IAutoSerializable extends ISerializable {

    IAutoSerializable getBase();

    @Override
    default void load(JSONObject json) {
        preLoad();
        PropertiesManager.load(this.getBase(), json);
        postLoad();
    }

    @Override
    default JSONObject save() {
        preSave();
        JSONObject object = PropertiesManager.save(this.getBase());
        postSave();
        return object;
    }

    default void preLoad() {}

    default void postLoad() {}

    default void preSave() {}

    default void postSave() {}
}
