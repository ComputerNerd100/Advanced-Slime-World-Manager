package com.grinderwolf.swm.plugin.util;

import com.google.common.reflect.TypeToken;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.lang.reflect.Field;

@UtilityClass
public class ReflectionUtil {

    /**
     * Returns a Field object belonging to the specified instance, that matches the provided name
     * @param instance The instance to retrieve the field from
     * @param name The name of the field to return
     * @return The Field, if found
     */
    @SneakyThrows
    public Field getField(Object instance, String name) {
        return instance.getClass().getDeclaredField(name);
    }

    /**
     * Casts and populates a field in the specified instance with the provided value
     * @param instance The instance who the field belongs to
     * @param field The field to populate
     * @param value The value to be cast and set
     */
    @SuppressWarnings("UnstableApiUsage")
    @SneakyThrows
    public void populateField(Object instance, Field field, String value) {
        TypeToken<?> type = TypeToken.of(field.getType());
        field.setAccessible(true);
        field.set(instance, TypeParsers.getPARSERS().get(type).parse(type, value));
    }

}
