package com.cegesoft.jarvis.utils;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by HoxiSword on 28/04/2020 for JARVIS
 */
public class ReflectionUtil {

    /**
     * Get all fields of a class (including fields of parent.). Ascending parents to exclusiveParent.
     *
     * @param startClass      target class
     * @param exclusiveParent parent class
     * @return all field declared or not of a class
     */
    public static List<Field> getFieldsUpTo(Class<?> startClass, Class<?> exclusiveParent) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = startClass; current != null && !current.equals(exclusiveParent); current = current.getSuperclass())
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
        return fields;
    }

    public static <T> T newInstance(Class<T> tClass) throws ReflectiveOperationException {
        Constructor<T> constructor = tClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    public static Class<?> getGenericClass(Field field, int index) {
        Type genericType = field.getGenericType();
        if (genericType instanceof ParameterizedType) {
            ParameterizedType type = (ParameterizedType) genericType;
            if (type.getActualTypeArguments().length <= index)
                return null;
            Type argument = type.getActualTypeArguments()[index];
            return argument instanceof Class ? (Class<?>) argument : null;
        }
        return null;
    }

}
