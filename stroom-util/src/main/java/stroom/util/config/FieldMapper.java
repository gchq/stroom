package stroom.util.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.config.PropertyUtil.Prop;
import stroom.util.logging.LogUtil;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

public class FieldMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(FieldMapper.class);

    public enum CopyOption {

        /**
         * If the source is null don't set the dest to null.
         * Default is to set dest to source even if the source is null.
         */
        DONT_COPY_NULLS;
    }

    public interface UpdateAction {
        void accept(final Object destParent,
                    final Prop prop,
                    final Object sourcePropValue,
                    final Object destPropValue);
    }

    public static <T> void copy(final T source, final T dest, final CopyOption... copyOptions) {
        copy(source, dest, null, copyOptions);
    }

    /**
     * Deep copy the field values of stroom POJO object trees from source into dest.
     * Relies on the public getters/setters so private fields won't be copied.
     * NOTE: Only deep copies objects in packages starting with 'stroom'
     *
     */
    public static <T> void copy(final T source,
                                final T dest,
                                final UpdateAction updateAction,
                                final CopyOption... copyOptions) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(dest);

        LOGGER.trace("copy called for {}", source.getClass().getSimpleName());
        try {
            final Map<String, Prop> properties = PropertyUtil.getProperties(source);
            for (Prop prop : properties.values()) {
                LOGGER.trace("Examining prop {} of type {}",
                        prop.getName(), prop.getValueClass().getSimpleName());

                final Object sourcePropValue = prop.getGetter().invoke(source);
                final Object destPropValue = prop.getGetter().invoke(dest);
                if (prop.getValueClass().getName().startsWith("stroom")) {
                    // property is another stroom pojo
                    if (sourcePropValue == null && destPropValue == null) {
                        // nothing to do
                    } else if (sourcePropValue == null && destPropValue != null) {
//                            LOGGER.info("Updating config value of {} from [{}] to [{}]",
//                                    prop.getName(), destPropValue, sourcePropValue);
//                            prop.getSetter().invoke(dest, sourcePropValue);
                            updateValue(dest, prop, sourcePropValue, destPropValue, updateAction, copyOptions);
                    } else if (destPropValue == null) {
                        // Create a new object to copy into
                        final Object newInstance = prop.getValueClass().getConstructor().newInstance();

                        // no destination instance so just use the new instance
                        prop.getSetter().invoke(dest, newInstance);
                        copy(sourcePropValue, newInstance, updateAction, copyOptions);
                    } else {
                        // Recurse for deep copy.
                        copy(sourcePropValue, destPropValue, updateAction, copyOptions);
                    }
                } else {
                    // prop is a primitive or a non-stroom class, so update it if it is different
                    if ((sourcePropValue == null && destPropValue != null) ||
                            (sourcePropValue != null && !sourcePropValue.equals(destPropValue))) {
                        updateValue(dest, prop, sourcePropValue, destPropValue, updateAction, copyOptions);
                    }
                }
            }
        } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException | InstantiationException e) {
            throw new RuntimeException(LogUtil.message("Error copying fields from [{}] to [{}]", source, dest), e);
        }
    }

    private static <T> void updateValue(final T destParent,
                                        final Prop prop,
                                        final Object sourcePropValue,
                                        final Object destPropValue,
                                        final UpdateAction updateAction,
                                        final CopyOption... copyOptions) throws IllegalAccessException, InvocationTargetException {
        if (sourcePropValue != null || !isOptionPresent(CopyOption.DONT_COPY_NULLS, copyOptions)) {

            LOGGER.debug("Updating config value of {} from [{}] to [{}]", prop.getName(), destPropValue, sourcePropValue);
            prop.getSetter().invoke(destParent, sourcePropValue);
            if (updateAction != null) {
                updateAction.accept(destParent, prop, sourcePropValue, destPropValue);
            }
        } else {
            LOGGER.trace("Source value of {} is null but we are not copying nulls", sourcePropValue);
        }
    }

    private static boolean isOptionPresent(final CopyOption copyOption, CopyOption... copyOptions) {
        return Arrays.asList(copyOptions).contains(copyOption);
    }
}