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

package stroom.test;

import stroom.config.app.ConfigHolder;
import stroom.config.global.impl.ConfigMapper;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Intercepts any calls to getConfigObject and allows the returned values to be replaced
 * with different values for testing.
 */
public class ConfigMapperSpy extends ConfigMapper {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ConfigMapperSpy.class);

    // This is here, so we can create config mappers before guice binding happens.
    private static final Map<Class<?>, UnaryOperator<?>> STATIC_VALUE_MAPPERS = new ConcurrentHashMap<>();
    private Map<Class<?>, UnaryOperator<?>> valueMappers;

    @Inject
    public ConfigMapperSpy(final ConfigHolder configHolder) {
        super(configHolder);
        this.valueMappers = new HashMap<>();
        Objects.requireNonNull(valueMappers);
    }

    @Override
    public <T extends AbstractConfig> T getConfigObject(final Class<T> clazz) {
        final T config = super.getConfigObject(clazz);
        return mapConfigValue(clazz, config);
    }

    private <T extends AbstractConfig> T mapConfigValue(final Class<T> clazz, final T value) {
        // TODO: 25/05/2023 Not sure this null check is needed
        if (NullSafe.isEmptyMap(STATIC_VALUE_MAPPERS) && NullSafe.isEmptyMap(valueMappers)) {
            // when getConfigObject is called as part of the super ctor, valueMappers will be null
            return value;
        } else if (value == null) {
            return null;
        } else {
            T newValue = value;
            boolean isMapped = false;
            final UnaryOperator<T> staticValueMapper = getStaticValueMapper(clazz);
            if (staticValueMapper != null) {
                newValue = staticValueMapper.apply(newValue);
                isMapped = true;
            }
            final UnaryOperator<T> valueMapper = getValueMapper(clazz);
            if (valueMapper != null) {
                newValue = valueMapper.apply(newValue);
                isMapped = true;
            }
            if (isMapped) {
                LOGGER.debug("Modifying value of config object {}\nbefore: {}\nafter:  {}",
                        clazz.getSimpleName(), value, newValue);
            }
            return newValue;
        }
    }

    private <T> UnaryOperator<T> getValueMapper(final Class<T> clazz) {
        return (UnaryOperator<T>) NullSafe.map(valueMappers).get(clazz);
    }

    private <T> UnaryOperator<T> getStaticValueMapper(final Class<T> clazz) {
        return (UnaryOperator<T>) STATIC_VALUE_MAPPERS.get(clazz);
    }

    private Map<Class<?>, UnaryOperator<?>> getMappers() {
        final Map<Class<?>, UnaryOperator<?>> combinedMap = new HashMap<>();
        combinedMap.putAll(STATIC_VALUE_MAPPERS);
        combinedMap.putAll(valueMappers);
        return combinedMap;
    }

    public <T extends AbstractConfig> void setConfigValueMappers(final Map<Class<T>, UnaryOperator<T>> valueMappers) {
        this.valueMappers.clear();
        this.valueMappers.putAll(valueMappers);
    }

    public <T extends AbstractConfig> void setConfigValueMapper(final Class<T> clazz,
                                                                final UnaryOperator<T> mutator) {
        this.valueMappers.put(clazz, mutator);
    }

    /**
     * Use this with care as it will set mappers that are held statically on the class so will
     * impact other tests running in the same JVM.
     * Needed for when you want to set config values that are used in eager singleton construction.
     */
    public static <T extends AbstractConfig> void setStaticConfigValueMappers(
            final Map<Class<T>, UnaryOperator<T>> valueMappers) {
        STATIC_VALUE_MAPPERS.clear();
        STATIC_VALUE_MAPPERS.putAll(valueMappers);
    }

    /**
     * Use this with care as it will set a mapper that is held statically on the class so will
     * impact other tests running in the same JVM.
     * Needed for when you want to set config values that are used in eager singleton construction.
     */
    public static <T extends AbstractConfig> void setStaticConfigValueMapper(final Class<T> clazz,
                                                                             final UnaryOperator<T> mutator) {
        STATIC_VALUE_MAPPERS.put(clazz, mutator);
    }

    public void clearConfigValueMappers() {
        STATIC_VALUE_MAPPERS.clear();
        this.valueMappers.clear();
    }

    public String dumpClassesWithMappers() {
        return LogUtil.message("static mappers: [{}], mappers: [{}]",
                NullSafe.map(STATIC_VALUE_MAPPERS)
                        .keySet()
                        .stream()
                        .map(Class::getSimpleName)
                        .sorted()
                        .collect(Collectors.joining(", ")),
                NullSafe.map(valueMappers)
                        .keySet()
                        .stream()
                        .map(Class::getSimpleName)
                        .sorted()
                        .collect(Collectors.joining(", ")));
    }
}
