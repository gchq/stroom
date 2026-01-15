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

package stroom.proxy.app.guice;

import stroom.proxy.app.ProxyConfig;
import stroom.proxy.app.ProxyConfigHolder;
import stroom.util.config.PropertyUtil;
import stroom.util.config.PropertyUtil.Prop;
import stroom.util.config.annotations.RequiresProxyRestart;
import stroom.util.exception.ThrowingSupplier;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PropertyPath;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This is sort of the equivalent to stroom's ConfigMapper class.
 * It holds the de-serialised config objects in a map that is then used to
 * provide them for guice injection.
 */
@Singleton
public class ProxyConfigProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProxyConfigProvider.class);

    // The map to be used by guice to provide instances. This map will be replaced in its entirety
    // when config has changed, on the basis that config is not changed very often
    private volatile Map<Class<? extends AbstractConfig>, AbstractConfig> configInstanceMap = null;

    @SuppressWarnings("unused")
    @Inject
    ProxyConfigProvider(final ProxyConfigHolder configHolder) {
        this(configHolder.getProxyConfig());
    }

    ProxyConfigProvider(final ProxyConfig proxyConfig) {
        // Don't need to know about changes on first run as everything will be null to start with
        rebuildConfigInstances(proxyConfig, false);
    }

    <T extends AbstractConfig> T getConfigObject(final Class<T> clazz) {
        final AbstractConfig config = configInstanceMap.get(clazz);
        Objects.requireNonNull(config, "No config instance found for class " + clazz.getName());
        try {
            return clazz.cast(config);
        } catch (final Exception e) {
            throw new RuntimeException(LogUtil.message(
                    "Error casting config object to {}, found {}",
                    clazz.getName(),
                    config.getClass().getName()), e);
        }
    }

    Set<Class<? extends AbstractConfig>> getInjectableClasses() {
        return Collections.unmodifiableSet(configInstanceMap.keySet());
    }

    private void addConfigInstances(
            final AbstractConfig config,
            final Prop parentProp,
            final Map<Class<? extends AbstractConfig>, AbstractConfig> configInstanceMap,
            final ObjectMapper objectMapper,
            final PropertyPath propertyPath,
            final AtomicInteger changeCounter,
            final boolean logChanges) {

        try {
            final Class<? extends AbstractConfig> clazz = config.getClass();

            final Map<String, Prop> propMap = PropertyUtil.getProperties(objectMapper, config);

            propMap.forEach((k, prop) -> {
                final String childPropName = prop.getName();
                final PropertyPath childPropPath = propertyPath.merge(childPropName);
                final Class<?> valueType = prop.getValueClass();

                // Don't recurse into non-injectable values, treat them like a non config type
                if (AbstractConfig.class.isAssignableFrom(valueType)
                    && !valueType.isAnnotationPresent(NotInjectableConfig.class)) {

                    final AbstractConfig childValue = (AbstractConfig) prop.getValueFromConfigObject();

                    if (childValue != null) {
                        // recurse
                        addConfigInstances(
                                childValue,
                                prop,
                                configInstanceMap,
                                objectMapper,
                                childPropPath,
                                changeCounter,
                                logChanges);
                    } else {
                        if (!prop.getValueClass().isAnnotationPresent(NotInjectableConfig.class)) {
                            // We should not have null config objects that are meant to be injectable
                            throw new RuntimeException(LogUtil.message(
                                    "Prop {} is null but is injectable config",
                                    childPropPath));
                        }
                    }
                }
            });

            // Non-injectable config is only accessible by an injectable ancestor so no need to add
            // it to the providers map
            if (!clazz.isAnnotationPresent(NotInjectableConfig.class)) {
                // Add the injectable config object to the instance map so the providers can get it
                configInstanceMap.put(config.getClass(), config);
            }

            // If it is not in the map then it is not injectable
//            final AbstractConfig existingConfig = NullSafe.getOrElseGet(
//                    this.configInstanceMap,
//                    map -> map.get(clazz),
//                    ThrowingSupplier.unchecked(() -> clazz.getConstructor().newInstance()));
            final AbstractConfig existingConfig = NullSafe.get(
                    this.configInstanceMap,
                    map -> map.get(clazz));

            // This is an update so see what has changed
            propMap.forEach((propName, prop) -> {
                final PropertyPath childPropertyPath = propertyPath.merge(propName);
                final Class<?> valueType = prop.getValueClass();
                // Only log changes for values that are not config objects or config objects
                // That can't be injected.
                // Recursion into config objects will happen above
                if (!AbstractConfig.class.isAssignableFrom(valueType)
                    || valueType.isAnnotationPresent(NotInjectableConfig.class)) {
                    final Object newValue = prop.getValueFromConfigObject();
                    Object existingValue = null;
                    if (existingConfig != null) {
                        existingValue = prop.getValueFromConfigObject(existingConfig);
                    }

                    if (existingValue == null && AbstractConfig.class.isAssignableFrom(valueType)) {
                        // Create a vanilla config obj with all defaults
                        LOGGER.debug("Using vanilla object for property {}, class {}", childPropertyPath, valueType);
                        existingValue = ThrowingSupplier.unchecked(() ->
                                        valueType.getConstructor().newInstance())
                                .get();
                    }

                    NullSafe.get(prop.getParentObject(), PropertyUtil::getProperties);
                    if (!Objects.equals(existingValue, newValue)) {
                        checkForValueChanges(
                                childPropertyPath,
                                prop,
                                parentProp,
                                newValue,
                                existingValue, logChanges,
                                changeCounter);
                    }
                }
            });
        } catch (final Exception e) {
            throw new RuntimeException("Error adding config instances for " + config.getClass().getName(), e);
        }
    }

    private void checkForValueChanges(final PropertyPath propertyPath,
                                      final Prop prop,
                                      final Prop parentProp,
                                      final Object newValue,
                                      final Object existingValue,
                                      final boolean logChanges,
                                      final AtomicInteger changeCounter) {
        // Get one of them, doesn't matter which
        final Optional<Object> optConfig = NullSafe.coalesce(existingValue, newValue);
        final Optional<Class<?>> optClass = optConfig.map(Object::getClass);

        if (optClass.filter(AbstractConfig.class::isAssignableFrom).isPresent()) {
            // Config object so recurse into each prop
            final Object config = optConfig.get();

            final Map<String, Prop> propMap = PropertyUtil.getProperties(config);
            propMap.forEach((childPropName, childProp) -> {
                // Both of these could be null if their parent is null
                final Object existingChildVal = NullSafe.get(existingValue, childProp::getValueFromConfigObject);
                final Object newChildVal = NullSafe.get(newValue, childProp::getValueFromConfigObject);
                checkForValueChanges(
                        propertyPath.merge(childPropName),
                        childProp,
                        prop,
                        newChildVal,
                        existingChildVal,
                        logChanges,
                        changeCounter);
            });
        } else {
            if (!Objects.equals(existingValue, newValue)) {
                changeCounter.incrementAndGet();
                if (logChanges) {
                    final String extraText = prop.hasAnnotation(RequiresProxyRestart.class)
                                             || Prop.hasAnnotation(parentProp, RequiresProxyRestart.class)
                            ? ". NOTE: This property requires an application re-start to take effect."
                            : "";

                    LOGGER.info("Config property {} has changed from [{}] to [{}]{}",
                            propertyPath.toString(),
                            existingValue,
                            newValue,
                            extraText);
                }
            }
        }
    }

    public void rebuildConfigInstances(final ProxyConfig newProxyConfig) {
        rebuildConfigInstances(newProxyConfig, true);
    }

    private synchronized void rebuildConfigInstances(final ProxyConfig newProxyConfig,
                                                     final boolean logChanges) {
        LOGGER.debug("Rebuilding object instance map");
        final Map<Class<? extends AbstractConfig>, AbstractConfig> newInstanceMap = new HashMap<>();

        final AtomicInteger changeCounter = new AtomicInteger();

        addConfigInstances(
                newProxyConfig,
                null,
                newInstanceMap,
                JsonUtil.getMapper(),
                ProxyConfig.ROOT_PROPERTY_PATH,
                changeCounter,
                logChanges);

        if (configInstanceMap == null || changeCounter.get() > 0) {
            LOGGER.debug("Swapping out configInstanceMap, changeCounter {}", changeCounter);
            // Now swap out the current map with the new one
            if (configInstanceMap == null) {
                configInstanceMap = new HashMap<>();
            }
            configInstanceMap = newInstanceMap;
        } else {
            LOGGER.debug("No change to the map");
        }

        if (logChanges) {
            if (changeCounter.get() > 0) {
                LOGGER.info("Completed update of application config with {} changed property values",
                        changeCounter.get());
            } else {
                LOGGER.info("No changes to config");
            }
        }

        LOGGER.debug("Completed rebuild of object instance map");
    }
}
