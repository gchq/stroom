/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.config.global.impl.db;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.app.AppConfig;
import stroom.config.global.impl.db.BeanUtil.Prop;
import stroom.config.global.api.ConfigProperty;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class ConfigMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigMapper.class);

    private final List<ConfigProperty> globalProperties = new ArrayList<>();
    private final Map<String, Prop> propertyMap = new HashMap<>();

    @Inject
    ConfigMapper(final AppConfig appConfig) {
        addMethods(appConfig, "stroom");
        globalProperties.sort(Comparator.naturalOrder());
    }

    List<ConfigProperty> getGlobalProperties() {
        return globalProperties;
    }

    private void addMethods(final Object object, final String path) {
        final Map<String, Prop> properties = BeanUtil.getProperties(object);
        properties.forEach((k, v) -> {
            String specifiedName = getName(v.getGetter());
            String specifiedDescription = getDescription(v.getGetter());
            String name = v.getName();
            if (specifiedName != null) {
                name = specifiedName;
            }

            final String fullPath = path + "." + name;

            try {
                final Object value = v.getGetter().invoke(v.getObject());
                if (value != null) {
                    final Class<?> type = value.getClass();
                    if (type.equals(String.class) ||
                            type.equals(Byte.class) ||
                            type.equals(Integer.class) ||
                            type.equals(Long.class) ||
                            type.equals(Short.class) ||
                            type.equals(Float.class) ||
                            type.equals(Double.class) ||
                            type.equals(Character.class)) {
                        propertyMap.put(fullPath, v);

                        // Create global property.
                        final String defaultValue = getDefaultValue(v.getObject(), v.getGetter());
                        final ConfigProperty configProperty = new ConfigProperty();
                        configProperty.setSource("Code");
                        configProperty.setName(fullPath);
                        configProperty.setDefaultValue(defaultValue);
                        configProperty.setValue(defaultValue);
                        configProperty.setDescription(specifiedDescription);
                        configProperty.setEditable(true);
                        configProperty.setPassword(fullPath.toLowerCase().contains("pass"));
                        globalProperties.add(configProperty);

                    } else {
                        addMethods(value, fullPath);
                    }
                }
            } catch (final InvocationTargetException | IllegalAccessException e) {
                LOGGER.error(e.getMessage(), e);
            }
        });
    }

    private String getName(final Method method) {
        for (final Annotation declaredAnnotation : method.getDeclaredAnnotations()) {
            if (declaredAnnotation.annotationType().equals(JsonProperty.class)) {
                final JsonProperty jsonProperty = (JsonProperty) declaredAnnotation;
                return jsonProperty.value();
            }
        }
        return null;
    }

    private String getDescription(final Method method) {
        for (final Annotation declaredAnnotation : method.getDeclaredAnnotations()) {
            if (declaredAnnotation.annotationType().equals(JsonPropertyDescription.class)) {
                final JsonPropertyDescription jsonPropertyDescription = (JsonPropertyDescription) declaredAnnotation;
                return jsonPropertyDescription.value();
            }
        }
        return null;
    }

    private String getDefaultValue(final Object object, final Method method) {
        try {
            final Object o = method.invoke(object, (Object[]) new Class[0]);
            if (o != null) {
                return o.toString();
            }
        } catch (final IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
            LOGGER.debug(e.getMessage(), e);
        }
        return null;
    }

    public void update(final String key, final String value) {
        try {
            final Prop prop = propertyMap.get(key);
            if (prop != null) {
                final Class<?> type = prop.getSetter().getParameterTypes()[0];
                prop.getSetter().invoke(prop.getObject(), convert(value, type));
            }
        } catch (final IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
            LOGGER.debug(e.getMessage(), e);
        }
    }

    private Object convert(final String value, final Class<?> type) {
        if (value == null) {
            return null;
        }

        if (type.equals(String.class)) {
            return value;
        } else if (type.equals(Byte.class)) {
            return Byte.valueOf(value);
        } else if (type.equals(Integer.class)) {
            return Integer.valueOf(value);
        } else if (type.equals(Long.class)) {
            return Long.valueOf(value);
        } else if (type.equals(Short.class)) {
            return Short.valueOf(value);
        } else if (type.equals(Float.class)) {
            return Float.valueOf(value);
        } else if (type.equals(Double.class)) {
            return Double.valueOf(value);
        } else if (type.equals(Character.class) && value.length() > 0) {
            return value.charAt(0);
        }

        return null;
    }
}
