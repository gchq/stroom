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

package stroom.properties.global.impl.db;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.StroomConfig;
import stroom.properties.global.api.GlobalProperty;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
class ConfigMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigMapper.class);

    private final List<GlobalProperty> globalProperties = new ArrayList<>();
    private final Map<String, Prop> propertyMap = new HashMap<>();

    @Inject
    ConfigMapper(final StroomConfig stroomConfig) {
        addMethods(stroomConfig.getClass(), "stroom");
    }

    List<GlobalProperty> getGlobalProperties() {
        return globalProperties;
    }

    private void addMethods(final Object object, final String path) {
        final Class<?> clazz = object.getClass();
        final Method[] methods = clazz.getMethods();
        final Map<String, Prop> propMap = new HashMap<>();
        for (final Method method : methods) {
            final String methodName = method.getName();
            if (methodName.length() > 3 && !methodName.equals("getClass")) {
                String name = methodName.substring(3);
                name = name.substring(0, 1).toLowerCase() + name.substring(1);
                Prop prop = null;

                if (methodName.startsWith("get") && method.getParameterTypes().length == 0 && !method.getReturnType().equals(Void.TYPE)) {
                    // Getter.
                    prop = propMap.computeIfAbsent(name, k -> new Prop(object));
                    prop.getter = method;
                } else if (methodName.startsWith("set") && method.getParameterTypes().length == 1 && method.getReturnType().equals(Void.TYPE)) {
                    // Setter.
                    prop = propMap.computeIfAbsent(name, k -> new Prop(object));
                    prop.setter = method;
                }

                if (prop != null) {
                    final String specifiedName = getName(method);
                    final String specifiedDescription = getDescription(method);
                    if (specifiedName != null) {
                        prop.name = specifiedName;
                    }
                    if (prop.name == null) {
                        prop.name = name;
                    }
                    if (specifiedDescription != null) {
                        prop.description = specifiedDescription;
                    }
                }
            }
        }

        propMap.forEach((k, v) -> {
            if (v.getter == null || v.setter == null) {
                LOGGER.warn("Invalid property " + k + " on " + clazz.getName());
            } else {
                final String fullPath = path + "." + v.name;

                final Class<?> type = v.getter.getReturnType();
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
                    final String defaultValue = getDefaultValue(v.object, v.getter);
                    final GlobalProperty globalProperty = new GlobalProperty();
                    globalProperty.setSource("Code");
                    globalProperty.setName(fullPath);
                    globalProperty.setDefaultValue(defaultValue);
                    globalProperty.setValue(defaultValue);
                    globalProperty.setDescription(v.description);
                    globalProperty.setEditable(true);
                    globalProperty.setPassword(fullPath.toLowerCase().contains("pass"));
                    globalProperties.add(globalProperty);

                } else {
                    addMethods(type, fullPath);
                }
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

    void update(final String key, final String value) {
        try {
//            if (value != null) {
//                StroomProperties.setProperty(key, value, source);
//            }

            final Prop prop = propertyMap.get(key);
            if (prop != null) {
                final Class<?> type = prop.setter.getParameterTypes()[0];
                prop.setter.invoke(prop.object, convert(value, type));
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

//    private void initialise() {
//        // Setup DB properties.
//        LOGGER.info("Adding global properties to the DB");
//        loadDefaultProperties();
//        loadDBProperties();
//    }
//
//    @SuppressWarnings("resource")
//    private void loadDefaultProperties() {
//        try {
//            final List<GlobalProperty> globalPropertyList = DefaultProperties.getList();
//            for (final GlobalProperty globalProperty : globalPropertyList) {
//                globalProperty.setSource("Default");
//                globalProperty.setDefaultValue(globalProperty.getValue());
//                globalProperties.put(globalProperty.getName(), globalProperty);
//
//                update(globalProperty.getName(), globalProperty.getValue(), Source.GUICE);
//            }
//        } catch (final RuntimeException e) {
//            e.printStackTrace();
//        }
//    }
//
//    private void loadDBProperties() {
//        try (final Connection connection = connectionProvider.getConnection()) {
//            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
//            create
//                    .selectFrom(PROPERTY)
//                    .fetch()
//                    .forEach(record -> {
//                        if (record.getName() != null && record.getVal() != null) {
//                            final GlobalProperty globalProperty = globalProperties.get(record.getName());
//                            if (globalProperty != null) {
//                                globalProperty.setId(record.getId());
//                                globalProperty.setValue(record.getVal());
//                                globalProperty.setSource("Database");
//
//                                update(record.getName(), record.getVal(), Source.DB);
//                            } else {
//                                // Delete old property.
//                                delete(record.getName());
//                            }
//                        }
//                    });
//        } catch (final SQLException e) {
//            LOGGER.error(e.getMessage(), e);
//        }
//    }
//
//    /**
//     * Refresh in background
//     */
//    @StroomFrequencySchedule("1m")
//    @JobTrackedSchedule(jobName = "Property Cache Reload", description = "Reload properties in the cluster")
//    public void update() {
//        loadDBProperties();
//    }
//
//    public GlobalProperty getGlobalProperty(final String name) {
//        return globalProperties.get(name);
//    }
//
//    public Map<String, GlobalProperty> getGlobalProperties() {
//        return globalProperties;
//    }
//
//    @Override
//    public List<GlobalProperty> list() {
//        return security.secureResult(PermissionNames.MANAGE_PROPERTIES_PERMISSION, () -> {
//            loadDBProperties();
//
//            final List<GlobalProperty> list = new ArrayList<>(globalProperties.values());
//            list.sort(Comparator.comparing(GlobalProperty::getName));
//
//            return list;
//        });
//    }
//
//    @Override
//    public GlobalProperty load(final GlobalProperty globalProperty) {
//        return security.secureResult(PermissionNames.MANAGE_PROPERTIES_PERMISSION, () -> {
//            final GlobalProperty loaded = globalProperties.get(globalProperty.getName());
//
//            try (final Connection connection = connectionProvider.getConnection()) {
//                final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
//                create
//                        .selectFrom(PROPERTY)
//                        .where(PROPERTY.ID.eq(globalProperty.getId()))
//                        .fetchOptional()
//                        .ifPresent(record -> {
//                            if (record.getName() != null && record.getVal() != null) {
//                                if (loaded != null) {
//                                    loaded.setId(record.getId());
//                                    loaded.setValue(record.getVal());
//                                    loaded.setSource("Database");
//                                }
//                            }
//                        });
//            } catch (final SQLException e) {
//                LOGGER.error(e.getMessage(), e);
//            }
//
//            return loaded;
//        });
//    }
//
//    @Override
//    public GlobalProperty save(final GlobalProperty globalProperty) {
//        return security.secureResult(PermissionNames.MANAGE_PROPERTIES_PERMISSION, () -> {
//            try (final Connection connection = connectionProvider.getConnection()) {
//                final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
//
//                // Change value.
//                create
//                        .update(PROPERTY)
//                        .set(PROPERTY.VAL, globalProperty.getValue())
//                        .where(PROPERTY.NAME.eq(globalProperty.getName()));
//
//                // Record history.
//                create
//                        .insertInto(PROPERTY_HISTORY, PROPERTY_HISTORY.UPDATE_TIME, PROPERTY_HISTORY.UPDATE_USER, PROPERTY_HISTORY.NAME, PROPERTY_HISTORY.VAL)
//                        .values(System.currentTimeMillis(), securityContext.getUserId(), globalProperty.getName(), globalProperty.getValue())
//                        .execute();
//
//                // Update property.
//                update(globalProperty.getName(), globalProperty.getValue(), Source.DB);
//            } catch (final SQLException e) {
//                LOGGER.error(e.getMessage(), e);
//            }
//
//            return globalProperty;
//        });
//    }
//
//    private void delete(final String name) {
//        security.secure(PermissionNames.MANAGE_PROPERTIES_PERMISSION, () -> {
//            try (final Connection connection = connectionProvider.getConnection()) {
//                final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
//                create
//                        .deleteFrom(PROPERTY)
//                        .where(PROPERTY.NAME.eq(name));
//            } catch (final SQLException e) {
//                LOGGER.error(e.getMessage(), e);
//            }
//        });
//    }
//
//    @Override
//    public String toString() {
//        final StringBuilder sb = new StringBuilder();
//        for (final Entry<String, GlobalProperty> entry : globalProperties.entrySet()) {
//            sb.append(entry.getKey());
//            sb.append("=");
//            sb.append(entry.getValue());
//            sb.append("\n");
//        }
//        return sb.toString();
//    }

    private static class Prop {
        private final Object object;
        private String name;
        private String description;
        private Method getter;
        private Method setter;

        Prop(final Object object) {
            this.object = object;
        }
    }
}
