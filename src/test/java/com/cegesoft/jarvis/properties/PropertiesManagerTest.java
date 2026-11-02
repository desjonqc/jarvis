package com.cegesoft.jarvis.properties;

import com.cegesoft.jarvis.module.ModuleType;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PropertiesManagerTest {

    static class Sample implements IAutoSerializable {
        @SavedItem
        boolean flag = false;
        @SavedItem
        int count = 7;
        @SavedItem
        double level = 100.0;
        @SavedItem
        ModuleType type;
        @SavedItem
        String name = "default";
        int notSaved = 3;

        @Override
        public IAutoSerializable getBase() {
            return this;
        }
    }

    private static JSONObject parse(String json) throws Exception {
        return (JSONObject) new JSONParser().parse(json);
    }

    @Test
    void loadsPrimitiveBooleanFromJson() throws Exception {
        Sample sample = new Sample();
        sample.load(parse("{\"flag\":true}"));
        assertTrue(sample.flag);
    }

    @Test
    void convertsJsonNumbersToFieldTypes() throws Exception {
        Sample sample = new Sample();
        sample.load(parse("{\"count\":42,\"level\":85}"));
        assertEquals(42, sample.count);
        assertEquals(85.0, sample.level);
    }

    @Test
    void missingKeysKeepDefaultValues() throws Exception {
        Sample sample = new Sample();
        sample.load(parse("{}"));
        assertFalse(sample.flag);
        assertEquals(7, sample.count);
        assertEquals("default", sample.name);
    }

    @Test
    void invalidValueDoesNotBreakOtherFields() throws Exception {
        Sample sample = new Sample();
        sample.load(parse("{\"type\":\"NOT_A_TYPE\",\"count\":5}"));
        assertNull(sample.type);
        assertEquals(5, sample.count);
    }

    @Test
    void roundTrip() throws Exception {
        Sample sample = new Sample();
        sample.flag = true;
        sample.count = 12;
        sample.level = 42.5;
        sample.type = ModuleType.SWITCH_SENSOR;
        sample.name = "salon";

        Sample copy = new Sample();
        copy.load(parse(sample.save().toJSONString()));

        assertTrue(copy.flag);
        assertEquals(12, copy.count);
        assertEquals(42.5, copy.level);
        assertEquals(ModuleType.SWITCH_SENSOR, copy.type);
        assertEquals("salon", copy.name);
        assertFalse(sample.save().containsKey("notSaved"));
    }
}
