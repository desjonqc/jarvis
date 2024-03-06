package com.cegesoft.jarvis.properties;

import com.cegesoft.jarvis.utils.ISerializable;
import com.cegesoft.jarvis.utils.ReflectionUtil;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by HoxiSword on 15/06/2020 for JARVIS
 */
public class PropertiesManager {

    static void load(IAutoSerializable serializable, JSONObject object) {
        for (Field field : ReflectionUtil.getFieldsUpTo(serializable.getClass(), null)) {
            if (field.isAnnotationPresent(SavedItem.class)) {
                boolean isAccessible = field.isAccessible();
                field.setAccessible(true);
                try {
                    if (!object.containsKey(field.getName())) {
                        field.set(serializable, null);
                    } else {
                        field.set(serializable, loadObject(object, field, field.getName(), object.get(serializable)));
                    }
                } catch (IllegalAccessException | InstantiationException e) {
                    e.printStackTrace();
                }
                field.setAccessible(isAccessible);
            }
        }
    }

    static JSONObject save(IAutoSerializable serializable) {
        JSONObject object = new JSONObject();
        for (Field field : ReflectionUtil.getFieldsUpTo(serializable.getClass(), null)) {
            if (field.isAnnotationPresent(SavedItem.class)) {
                boolean isAccessible = field.isAccessible();
                field.setAccessible(true);
                try {
                    Object value = field.get(serializable);
                    if (value == null)
                        continue;
                    saveObject(object, field.getName(), value);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
                field.setAccessible(isAccessible);
            }
        }
        return object;
    }

    public static void saveObject(JSONObject parent, String key, Object object) {
        if (object.getClass().isEnum()) {
            parent.put(key, ((Enum<?>)object).name());
        } else if (object instanceof Serializable) {
            if (object instanceof ISerializable) {
                parent.put(key, ((ISerializable) object).save());
            } else {
                parent.put(key, object);
            }
        } else if (object instanceof Collection) {
            JSONArray array = new JSONArray();
            if (!((Collection<?>) object).isEmpty()) {
                if (((Collection<?>) object).iterator().next() instanceof ISerializable) {
                    array.addAll(((Collection<? extends ISerializable>) object).stream().map(ISerializable::save).collect(Collectors.toCollection(ArrayList::new)));
                } else
                    array.addAll((Collection<?>) object);
            }
            parent.put(key, array);
        } else if (object instanceof Map) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.putAll((Map<?, ?>)object);
            parent.put(key, jsonObject);
        }
    }

    public static Object loadObject(JSONObject object, Field field, String key, Object defaultValue) throws InstantiationException, IllegalAccessException {
        if (ISerializable.class.isAssignableFrom(field.getType())) {
            try {
                ISerializable ser = (ISerializable) field.getType().newInstance();
                ser.load((JSONObject) object.get(key));
                return ser;
            } catch (InstantiationException e) {
                e.printStackTrace();
            }
        } else if (field.getType().isEnum()) {
            return Enum.valueOf((Class<Enum>)field.getType(), object.get(key).toString());
        } else if (Collection.class.isAssignableFrom(field.getType())) {
            JSONArray array = (JSONArray)object.get(key);
            Collection<?> collection = field.getType().isInterface() ? new ArrayList<>() : (Collection<?>)field.getType().newInstance();
            Class<?> parameterClass = ReflectionUtil.getGenericClass(field, 0);
            if (parameterClass != null) {
                if (ISerializable.class.isAssignableFrom(parameterClass)) {
                    collection.addAll((Collection) ((List<Object>)array).stream().map(obj -> {
                        try {
                            ISerializable ser = (ISerializable) parameterClass.newInstance();
                            ser.load((JSONObject) obj);
                            return (Object) ser;
                        } catch (InstantiationException | IllegalAccessException e) {
                            e.printStackTrace();
                        }
                        return null;
                    }).filter(Objects::nonNull).collect(Collectors.toList()));
                } else {
                    collection.addAll(array);
                }
            } else
                collection.addAll(array);
            return collection;
        } else if (Map.class.isAssignableFrom(field.getType())) {
            JSONObject jsonObject = (JSONObject) object.get(key);
            Map<?, ?> map = field.getType().isInterface() ? new HashMap<>() : (Map<?, ?>)field.getType().newInstance();
            map.putAll(jsonObject);
            return map;
        } else {
            switch (field.getType().getName()) {
                case "long": return ((Long)object.getOrDefault(key, defaultValue)).longValue();
                case "int": return ((Long)object.getOrDefault(key, defaultValue)).intValue();
                case "double": return ((Double) object.getOrDefault(key, defaultValue)).doubleValue();
            }
            return field.getType().cast(object.getOrDefault(key, defaultValue));
        }
        return defaultValue;
    }

}
