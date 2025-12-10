/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.util.config;

import stroom.util.config.PropertyUtil.Prop;
import stroom.util.logging.LogUtil;
import stroom.util.time.StroomDuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

public class FieldMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(FieldMapper.class);

    private enum CopyOption {
        /**
         * If the source is null don't set the dest to null.
         * Default is to set dest to source even if the source is null.
         */
        DONT_COPY_NULLS,

        /**
         * If the source is a default value (i.e. the value a newly instantiated object would have)
         * then don't copy it to the dest.
         */
        DONT_COPY_DEFAULTS
    }

    public interface UpdateAction {

        void accept(final Object destParent,
                    final Prop prop,
                    final Object sourcePropValue,
                    final Object destPropValue);
    }

    /**
     * Deep copy the field values of stroom POJO object trees from source into dest.
     * Relies on the public getters/setters so private fields won't be copied.
     * NOTE: Only deep copies objects in packages starting with 'stroom'
     */
    public static <T> void copy(final T source, final T dest) {
        copy(source, dest, null, null);
    }

    public static <T> void copy(final T source, final T dest, final UpdateAction updateAction) {
        copy(source, dest, null, updateAction);
    }

    /**
     * Deep copy the field values of stroom POJO object trees from source into dest.
     * Relies on the public getters/setters so private fields won't be copied.
     * NOTE: Only deep copies objects in packages starting with 'stroom'
     * Only non null source values will be copied.
     */
    public static <T> void copyNonNulls(final T source,
                                        final T dest,
                                        final UpdateAction updateAction) {
        copy(source, dest, null, updateAction, CopyOption.DONT_COPY_NULLS);
    }

    public static <T> void copyNonNulls(final T source,
                                        final T dest) {
        copy(source, dest, null, null, CopyOption.DONT_COPY_NULLS);
    }

    /**
     * Deep copy the field values of stroom POJO object trees from source into dest.
     * Relies on the public getters/setters so private fields won't be copied.
     * NOTE: Only deep copies objects in packages starting with 'stroom'
     * Only non default source values will be copied, ie. the value in source
     * is equal to the value in vanillaObject.
     */
    public static <T> void copyNonDefaults(final T source,
                                           final T dest,
                                           final T vanillaObject,
                                           final UpdateAction updateAction) {
        copy(source, dest, vanillaObject, updateAction, CopyOption.DONT_COPY_DEFAULTS);
    }

    public static <T> void copyNonDefaults(final T source,
                                           final T dest,
                                           final T vanillaObject) {
        copy(source, dest, vanillaObject, null, CopyOption.DONT_COPY_DEFAULTS);
    }

    private static <T> void copy(final T source,
                                 final T dest,
                                 final T vanillaObject,
                                 final UpdateAction updateAction,
                                 final CopyOption... copyOptions) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(dest);

        LOGGER.trace("copy called for {}", source.getClass().getSimpleName());
        try {
            final Map<String, Prop> properties = PropertyUtil.getProperties(source);
            for (final Prop prop : properties.values()) {
                LOGGER.trace("Examining prop {} of type {}",
                        prop.getName(), prop.getValueClass().getSimpleName());

                final Object sourcePropValue = prop.getGetter().invoke(source);
                final Object destPropValue = prop.getGetter().invoke(dest);

                final Object defaultPropValue = vanillaObject != null
                        ? prop.getGetter().invoke(vanillaObject)
                        : null;

                // TODO this doesn't work for props that are collections of stroom classes,
                //  e.g. List<ForwardDestination>
//                if (AbstractConfig.class.isAssignableFrom(prop.getValueClass())) {
                if (prop.getValueClass().getName().startsWith("stroom")
                        && !StroomDuration.class.equals(prop.getValueClass())) {
                    // property is another stroom pojo
                    if (sourcePropValue == null && destPropValue == null) {
                        // nothing to do
                    } else if (sourcePropValue == null && destPropValue != null) {
//                            LOGGER.info("Updating config value of {} from [{}] to [{}]",
//                                    prop.getName(), destPropValue, sourcePropValue);
//                            prop.getSetter().invoke(dest, sourcePropValue);
                        updateValue(dest,
                                prop,
                                sourcePropValue,
                                destPropValue,
                                defaultPropValue,
                                updateAction,
                                copyOptions);
                    } else if (destPropValue == null) {
                        // Create a new object to copy into
                        final Object newInstance = prop.getValueClass().getConstructor().newInstance();

                        // no destination instance so just use the new instance
                        prop.getSetter().invoke(dest, newInstance);
                        copy(sourcePropValue, newInstance, defaultPropValue, updateAction, copyOptions);
                    } else {
                        // Recurse for deep copy.
                        copy(sourcePropValue, destPropValue, defaultPropValue, updateAction, copyOptions);
                    }
                } else {
                    // prop is a primitive or a non-stroom class, so update it if it is different
                    if ((sourcePropValue == null && destPropValue != null) ||
                            (sourcePropValue != null && !sourcePropValue.equals(destPropValue))) {
                        updateValue(dest,
                                prop,
                                sourcePropValue,
                                destPropValue,
                                defaultPropValue,
                                updateAction,
                                copyOptions);
                    }
                }
            }
        } catch (final InvocationTargetException
                       | IllegalAccessException
                       | NoSuchMethodException
                       | InstantiationException e) {
            throw new RuntimeException(LogUtil.message("Error copying fields from [{}] to [{}]", source, dest), e);
        }
    }

    private static <T> void updateValue(final T destParent,
                                        final Prop prop,
                                        final Object sourcePropValue,
                                        final Object destPropValue,
                                        final Object defaultPropValue,
                                        final UpdateAction updateAction,
                                        final CopyOption... copyOptions)
            throws IllegalAccessException, InvocationTargetException {
        if (sourcePropValue != null || !isOptionPresent(CopyOption.DONT_COPY_NULLS, copyOptions)) {
            // source not null OR are copying nulls

            boolean doCopy = false;

            if (isOptionPresent(CopyOption.DONT_COPY_DEFAULTS, copyOptions)) {
                if (Objects.equals(sourcePropValue, defaultPropValue)) {
                    LOGGER.trace("Source value of {} is a default value but we are not copying defaults",
                            sourcePropValue);
                } else {
                    doCopy = true;
                }
            } else {
                doCopy = true;
            }

            if (doCopy) {
                LOGGER.debug("Updating config value of {} from [{}] to [{}]",
                        prop.getName(),
                        destPropValue,
                        sourcePropValue);
                prop.getSetter().invoke(destParent, sourcePropValue);
                if (updateAction != null) {
                    updateAction.accept(destParent, prop, sourcePropValue, destPropValue);
                }
            }
        } else {
            LOGGER.trace("Source value of {} is null but we are not copying nulls", sourcePropValue);
        }
    }

    private static boolean isOptionPresent(final CopyOption copyOption, final CopyOption... copyOptions) {
        return Arrays.asList(copyOptions).contains(copyOption);
    }
}
