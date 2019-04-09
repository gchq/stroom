package stroom.config.global.impl;

import stroom.config.global.impl.PropertyUtil.Prop;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

public class FieldMapper {
    public static void copy(final Object source, final Object dest) {
        try {
            final Map<String, Prop> properties = PropertyUtil.getProperties(source);
            for (Prop prop : properties.values()) {
                final Object sourceValue = prop.getGetter().invoke(source);
                if (sourceValue != null) {
                    if (sourceValue.getClass().getName().startsWith("stroom")) {
                        final Object destValue = prop.getGetter().invoke(dest);
                        if (destValue == null) {
                            prop.getSetter().invoke(dest, sourceValue);
                        } else {
                            // Recurse for deep copy.
                            copy(sourceValue, destValue);
                        }

                    } else {
                        prop.getSetter().invoke(dest, sourceValue);
                    }
                }
            }
        } catch (InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}