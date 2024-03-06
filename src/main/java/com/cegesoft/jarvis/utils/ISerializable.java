package com.cegesoft.jarvis.utils;

import org.json.simple.JSONObject;

import java.io.Serializable;

/**
 * Created by HoxiSword on 28/04/2020 for JARVIS
 */
public interface ISerializable extends Serializable {

    void load(JSONObject json);
    JSONObject save();

}
