package com.cegesoft.jarvis.utils;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by HoxiSword on 28/04/2020 for JARVIS
 */
public class ReflectionUtil {

    public static Field getField(Class<?> tClass, String name) {
        try {
            return tClass.getField(name);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Method getMethod(Class<?> tClass, String name, Class<?>... args) {
        try {
            return tClass.getMethod(name, args);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        return null;
    }


    /**
     * Get all fields of a class (including fields of parent.). Ascending parents to exclusiveParent.
     *
     * @param startClass      target class
     * @param exclusiveParent parent class
     * @return all field declared or not of a class
     */
    public static Iterable<Field> getFieldsUpTo(Class<?> startClass, Class<?> exclusiveParent) {

        List<Field> currentClassFields = new ArrayList<>(Arrays.asList(startClass.getDeclaredFields()));
        Class<?> parentClass = startClass.getSuperclass();

        if (parentClass != null && (!(parentClass.equals(exclusiveParent)))) {
            List<Field> parentClassFields = (List<Field>) getFieldsUpTo(parentClass, exclusiveParent);
            currentClassFields.addAll(parentClassFields);
        }

        return currentClassFields;
    }

    public static <T> T newInstance(Class<T> tClass) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        return tClass.getConstructor().newInstance();
    }

    public static Class<?> getGenericClass(Field field, int index) {
        Type genericType = field.getGenericType();
        if (genericType instanceof ParameterizedType) {
            ParameterizedType type = (ParameterizedType) genericType;
            if (type.getActualTypeArguments().length <= index)
                return null;
            return ((Class<?>)type.getActualTypeArguments()[index]);
        }
        return null;
    }

}
