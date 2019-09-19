package stroom.util.reflection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.reflection.PropertyUtil.Prop;
import stroom.util.logging.LogUtil;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

public class FieldMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(FieldMapper.class);

    /**
     * Deep copy the field values of POJOs from source into dest.
     * Relies on the public getters/setters so private fields won't be copied.
     * NOTE: Only deep copies objects in packages starting with 'stroom'
     */
    public static <T> void copy(final T source, final T dest) {
        LOGGER.debug("copy called for {}", source.getClass().getSimpleName());
        try {
            final Map<String, Prop> properties = PropertyUtil.getProperties(source);
            for (Prop prop : properties.values()) {
                LOGGER.debug("copying prop {} of type {}", prop.getName(), prop.getValueClass().getSimpleName());

                final Object sourceValue = prop.getGetter().invoke(source);
                if (sourceValue != null) {
                    if (prop.getValueClass().getName().startsWith("stroom")) {
                        // property is a complex
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
                } else {
                    // setting dest value to null
                    prop.getSetter().invoke(dest, sourceValue);
                }
            }
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(LogUtil.message("Error copying fields from {} to {}", source, dest));
        }
    }
}