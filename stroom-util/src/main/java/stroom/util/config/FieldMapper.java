package stroom.util.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.config.PropertyUtil.Prop;
import stroom.util.logging.LogUtil;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

public class FieldMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(FieldMapper.class);

    /**
     * Deep copy the field values of stroom POJO object trees from source into dest.
     * Relies on the public getters/setters so private fields won't be copied.
     * NOTE: Only deep copies objects in packages starting with 'stroom'
     */
    public static <T> void copy(final T source, final T dest) {
        LOGGER.debug("copy called for {}", source.getClass().getSimpleName());
        try {
            final Map<String, Prop> properties = PropertyUtil.getProperties(source);
            for (Prop prop : properties.values()) {
                LOGGER.debug("Examining prop {} of type {}", prop.getName(), prop.getValueClass().getSimpleName());

                final Object sourceValue = prop.getGetter().invoke(source);
                final Object destValue = prop.getGetter().invoke(dest);
                if (prop.getValueClass().getName().startsWith("stroom")) {
                    // property is another stroom pojo
                    if (destValue == null) {
                        // Create a new object to copy into
                        Object newInstance = prop.getValueClass().getConstructor().newInstance();

                        // no destination instance so just use the new instance
                        prop.getSetter().invoke(dest, newInstance);
                        copy(sourceValue, newInstance);
                    } else {
                        // Recurse for deep copy.
                        copy(sourceValue, destValue);
                    }
                } else {
                    // prop is a primitive or a non-stroom class, so update it if it is different
                    if ((sourceValue == null && destValue != null) ||
                            (sourceValue != null && !sourceValue.equals(destValue))) {
                        LOGGER.debug("Updating value of {} from [{}] to [{}]", prop.getName(), sourceValue, destValue);
                        prop.getSetter().invoke(dest, sourceValue);
                    }
                }
            }
        } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException | InstantiationException e) {
            throw new RuntimeException(LogUtil.message("Error copying fields from [{}] to [{}]", source, dest), e);
        }
    }
}