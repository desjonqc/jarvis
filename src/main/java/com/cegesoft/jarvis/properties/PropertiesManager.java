package com.cegesoft.jarvis.properties;

import com.cegesoft.jarvis.utils.ISerializable;
import com.cegesoft.jarvis.utils.ReflectionUtil;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.cegesoft.jarvis.Jarvis.getLogger;

/**
 * Created by HoxiSword on 15/06/2020 for JARVIS
 */
public class PropertiesManager {

    private static final Map<Class<?>, List<Field>> SAVED_FIELDS = new ConcurrentHashMap<>();

    private static List<Field> getSavedFields(Class<?> type) {
        return SAVED_FIELDS.computeIfAbsent(type, key -> {
            List<Field> fields = new ArrayList<>();
            for (Field field : ReflectionUtil.getFieldsUpTo(key, null)) {
                if (field.isAnnotationPresent(SavedItem.class)) {
                    field.setAccessible(true);
                    fields.add(field);
                }
            }
            return List.copyOf(fields);
        });
    }

    /**
     * Fill the {@link SavedItem} fields from the json. A missing or unreadable key keeps the field's current (default) value.
     */
    static void load(IAutoSerializable serializable, JSONObject object) {
        for (Field field : getSavedFields(serializable.getClass())) {
            if (!object.containsKey(field.getName()))
                continue;
            try {
                Object value = loadObject(object, field, field.getName(), field.get(serializable));
                if (value == null && field.getType().isPrimitive())
                    continue;
                field.set(serializable, value);
            } catch (ReflectiveOperationException | RuntimeException e) {
                getLogger().error("Can't load field " + field.getName() + " of " + serializable.getClass().getSimpleName() + " : ", e);
            }
        }
    }

    static JSONObject save(IAutoSerializable serializable) {
        JSONObject object = new JSONObject();
        for (Field field : getSavedFields(serializable.getClass())) {
            try {
                Object value = field.get(serializable);
                if (value == null)
                    continue;
                saveObject(object, field.getName(), value);
            } catch (IllegalAccessException e) {
                getLogger().error("Can't save field " + field.getName() + " of " + serializable.getClass().getSimpleName() + " : ", e);
            }
        }
        return object;
    }

    @SuppressWarnings("unchecked")
    public static void saveObject(JSONObject parent, String key, Object object) {
        if (object.getClass().isEnum()) {
            parent.put(key, ((Enum<?>)object).name());
        } else if (object instanceof ISerializable) {
            parent.put(key, ((ISerializable) object).save());
        } else if (object instanceof Collection) {
            JSONArray array = new JSONArray();
            for (Object element : (Collection<?>) object)
                array.add(element instanceof ISerializable ? ((ISerializable) element).save() : element);
            parent.put(key, array);
        } else if (object instanceof Map) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.putAll((Map<?, ?>)object);
            parent.put(key, jsonObject);
        } else if (object instanceof Serializable) {
            parent.put(key, object);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object loadObject(JSONObject object, Field field, String key, Object defaultValue) throws ReflectiveOperationException {
        Object raw = object.get(key);
        if (raw == null)
            return defaultValue;
        Class<?> type = field.getType();
        if (ISerializable.class.isAssignableFrom(type)) {
            ISerializable ser = (ISerializable) ReflectionUtil.newInstance(type);
            ser.load((JSONObject) raw);
            return ser;
        } else if (type.isEnum()) {
            return Enum.valueOf((Class<Enum>) type, raw.toString());
        } else if (Collection.class.isAssignableFrom(type)) {
            Collection<Object> collection = type.isInterface() ? new ArrayList<>() : (Collection<Object>) ReflectionUtil.newInstance(type);
            Class<?> parameterClass = ReflectionUtil.getGenericClass(field, 0);
            for (Object element : (JSONArray) raw) {
                if (parameterClass != null && ISerializable.class.isAssignableFrom(parameterClass)) {
                    ISerializable ser = (ISerializable) ReflectionUtil.newInstance(parameterClass);
                    ser.load((JSONObject) element);
                    collection.add(ser);
                } else {
                    collection.add(element);
                }
            }
            return collection;
        } else if (Map.class.isAssignableFrom(type)) {
            Map<Object, Object> map = type.isInterface() ? new HashMap<>() : (Map<Object, Object>) ReflectionUtil.newInstance(type);
            map.putAll((JSONObject) raw);
            return map;
        }
        return convertScalar(type, raw);
    }

    /**
     * json-simple reads every integer as Long and every decimal as Double : convert them to the field's type.
     */
    static Object convertScalar(Class<?> type, Object raw) {
        if (raw instanceof Number) {
            Number number = (Number) raw;
            if (type == int.class || type == Integer.class) return number.intValue();
            if (type == long.class || type == Long.class) return number.longValue();
            if (type == double.class || type == Double.class) return number.doubleValue();
            if (type == float.class || type == Float.class) return number.floatValue();
            if (type == short.class || type == Short.class) return number.shortValue();
            if (type == byte.class || type == Byte.class) return number.byteValue();
        }
        if (type == boolean.class || type == Boolean.class)
            return raw instanceof Boolean ? raw : Boolean.parseBoolean(raw.toString());
        if (type == String.class)
            return raw.toString();
        return type.cast(raw);
    }

}
