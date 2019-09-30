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

package stroom.config.global.impl;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.app.AppConfig;
import stroom.config.common.DbConfig;
import stroom.config.common.HasDbConfig;
import stroom.config.global.api.ConfigProperty;
import stroom.docref.DocRef;
import stroom.util.config.FieldMapper;
import stroom.util.config.PropertyUtil;
import stroom.util.config.PropertyUtil.Prop;
import stroom.util.config.annotations.Password;
import stroom.util.config.annotations.ReadOnly;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.logging.LogUtil;
import stroom.util.shared.IsConfig;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Responsible for mapping between the AppConfig object tree and a flat set of key value pairs.
 * The key for a leaf of the tree is a dot delimited path of all the branches to get to that leaf,
 * e.g.
 */
@Singleton
public class ConfigMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigMapper.class);

    private static final List<String> DELIMITERS = List.of(
            "|", ":", ";", ",", "!", "/", "\\", "#", "@", "~", "-", "_", "=", "+", "?");
    private static final String ROOT_PROPERTY_PATH = "stroom";
    private static final String DOCREF_PREFIX = "docRef(";

    // The guice bound appConfig
    private final AppConfig appConfig;

    // A map of config properties keyed on the fully qualified prop path (i.e. stroom.path.temp)
    // This is the source of truth for all properties. It is used to update the guice injected object model
    private final ConcurrentMap<String, ConfigProperty> globalPropertiesMap = new ConcurrentHashMap<>();

    // A map of property accessor objects keyed on the fully qualified prop path (i.e. stroom.path.temp)
    private final Map<String, Prop> propertyMap = new HashMap<>();


    public ConfigMapper(final AppConfig appConfig) {

        LOGGER.debug("Initialising ConfigMapper with class {}", appConfig.getClass().getName());

        this.appConfig = appConfig;

        // The values in the passed AppConfig will have been set initially from the compile-time defaults and
        // then from the DropWizard yaml file on app boot.
        // We want to know the default values as defined by the compile-time initial values of
        // the instance variables in the AppConfig tree.  This is so we can make the default values available
        // to the config UI.  Therefore create our own vanilla AppConfig tree and walk it to populate
        // globalPropertiesMap with the defaults.
        LOGGER.debug("Building globalPropertiesMap from compile-time default values and annotations");
        try {
            final AppConfig vanillaObject = appConfig.getClass().getDeclaredConstructor().newInstance();
            // Pass in an empty hashmap because we are not parsing the actual guice bound appConfig. We are only
            // populating the globalPropertiesMap so the passed hashmap is thrown away.
            addConfigObjectMethods(vanillaObject, ROOT_PROPERTY_PATH, new HashMap<>(), this::defaultValuePropertyConsumer);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(LogUtil.message("Unable to call constructor on class {}",
                    appConfig.getClass().getName()), e);
        }
        
        // Now add in any values from the yaml
        updateConfigFromYaml(appConfig);
    }

    /**
     * Will copy the contents of the passed {@link AppConfig} into the guice bound {@link AppConfig}
     * and update the globalPropertiesMap.
     * It will also apply common database config to all other database config objects.
     */
    void updateConfigFromYaml(final AppConfig newAppConfig) {

        // Allow the use of a single set of DB conn details that get applied to all, unless they override it
        applyCommonDbConfig(newAppConfig);

        synchronized (this) {
            if (System.identityHashCode(newAppConfig) != System.identityHashCode(appConfig)) {
                // We have been passed a different object to our instance appConfig so copy all
                // the values over.
                LOGGER.debug("Copying values from newAppConfig to appConfig");
                FieldMapper.copy(newAppConfig, appConfig);
            }

            // Now walk the AppConfig object model from the DropWiz YAML updating globalPropertiesMap with the
            // YAML values where present.
            LOGGER.debug("Adding yaml config values into global property map");
            addConfigObjectMethods(newAppConfig, ROOT_PROPERTY_PATH, propertyMap, this::yamlPropertyConsumer);
        }
    }

    public boolean validatePropertyPath(final String fullPath) {
        return propertyMap.get(fullPath) != null;
    }

    Collection<ConfigProperty> getGlobalProperties() {
        return globalPropertiesMap.values();
    }

    Optional<ConfigProperty> getGlobalProperty(final String fullPath) {
        Objects.requireNonNull(fullPath);
        return Optional.ofNullable(globalPropertiesMap.get(fullPath));
    }

    /**
     * Verifies that the passed value for the property with fullPath can be converted into
     * the appropriate object type
     */
    void validateStringValue(final String fullPath, final String value) {
        final Prop prop = propertyMap.get(fullPath);
        if (prop != null) {
            final Type genericType = prop.getValueType();
            convertToObject(value, genericType);
        } else {
            throw new UnknownPropertyException(LogUtil.message("No configProperty for {}", fullPath));
        }
    }

    /**
     * @param dbConfigProperty The config property object obtained from the database
     * @return The updated typed value from the object model
     */
    ConfigProperty updateDatabaseValue(final ConfigProperty dbConfigProperty) {
        Objects.requireNonNull(dbConfigProperty);

         final String fullPath = dbConfigProperty.getName();

        synchronized (this) {
            ConfigProperty globalConfigProperty = getGlobalProperty(fullPath)
                    .orElseThrow(() -> new UnknownPropertyException(LogUtil.message("No configProperty for {}", fullPath)));

            final Prop prop = propertyMap.get(fullPath);
            if (prop != null) {
                // Now set the new effective value on our guice bound appConfig instance
                final Type genericType = prop.getValueType();
                final Object typedValue = convertToObject(
                        globalConfigProperty.getEffectiveValue().orElse(null), genericType);
                prop.setValueOnConfigObject(typedValue);

                // Update all the DB related values from the passed DB config prop
                globalConfigProperty.setId(dbConfigProperty.getId());

                globalConfigProperty.setDatabaseOverride(dbConfigProperty.getDatabaseOverrideValue());
                globalConfigProperty.setVersion(dbConfigProperty.getVersion());
                globalConfigProperty.setCreateTimeMs(dbConfigProperty.getCreateTimeMs());
                globalConfigProperty.setCreateUser(dbConfigProperty.getCreateUser());
                globalConfigProperty.setUpdateTimeMs(dbConfigProperty.getUpdateTimeMs());
                globalConfigProperty.setUpdateUser(dbConfigProperty.getUpdateUser());

                return globalConfigProperty;
            } else {
                throw new UnknownPropertyException(LogUtil.message("No prop object for {}", fullPath));
            }
        }
    }

    private void addConfigObjectMethods(final IsConfig object,
                                        final String path,
                                        final Map<String, Prop> propertyMap,
                                        final BiConsumer<String, Prop> propConsumer) {
        LOGGER.trace("addConfigObjectMethods({}, {}, .....)", object, path);

        final Map<String, Prop> properties = PropertyUtil.getProperties(object);
        properties.forEach((k, prop) -> {
            LOGGER.trace("prop: {}", prop);
            Method getter = prop.getGetter();
            String specifiedName = getNameFromAnnotation(getter);
            String name = prop.getName();
            if (specifiedName != null) {
                name = specifiedName;
            }

            final String fullPath = path + "." + name;

            final Class<?> valueType = prop.getValueClass();

            final Object value = prop.getValueFromConfigObject();
            if (isSupportedPropertyType(valueType)) {
                // This is a leaf, i.e. a property so add it to our map
                propertyMap.put(fullPath, prop);

                // Now let the consumer do something to it
                propConsumer.accept(fullPath, prop);
            } else if (IsConfig.class.isAssignableFrom(valueType)) {
                // This must be a branch, i.e. config object so recurse into that
                if (value != null) {
                    IsConfig childConfigObject = (IsConfig) value;
                    addConfigObjectMethods(childConfigObject, fullPath, propertyMap, propConsumer);
                }
            } else {
                // This is not expected
                throw  new RuntimeException(LogUtil.message(
                        "Unexpected bean property of type [{}], expecting an instance of {}, or a supported type.",
                        valueType.getName(),
                        IsConfig.class.getSimpleName()));
            }

        });
    }

    private void yamlPropertyConsumer(final String fullPath, final Prop yamlProp) {

        // We have already walked a vanilla AppConfig object tree so all compile time
        // props should be in here with a default value (and a value that matches it)
        final ConfigProperty configProperty = globalPropertiesMap.get(fullPath);

        Preconditions.checkNotNull(configProperty, "Property %s with path %s exists in the " +
                "YAML but not in the object model, this should not happen", yamlProp, fullPath);

        // Create global property.
        final String yamlValueAsStr = getStringValue(yamlProp);
        configProperty.setYamlOverrideValue(yamlValueAsStr);
//        final String defaultValue = configProperty.getDefaultValue().orElse(null);
//
//        // If yaml value is the same as the default then null it out
//        if (yamlValueAsStr == null && defaultValue == null) {
//
//        }
//        if (configProperty.getDefaultValue() != null && configProperty.getDefaultValue().orElse(null).equals(yamlValueAsStr)) {
//            configProperty.setYamlValue(null);
//        } else {
//            configProperty.setYamlValue(yamlValueAsStr);
//        }
    }

    private void defaultValuePropertyConsumer(final String fullPath, final Prop defaultProp) {

        // Create global property.
        final String defaultValueAsStr = getDefaultValue(defaultProp);

        // build a new ConfigProperty object from our Prop and our defaults
        final ConfigProperty configProperty = new ConfigProperty();
        configProperty.setName(fullPath);
        configProperty.setDefaultValue(defaultValueAsStr);
        // Add all the meta data for the prop
        updatePropertyFromConfigAnnotations(configProperty, defaultProp);

        if (defaultValueAsStr == null) {
            LOGGER.debug("Property {} has no default value", fullPath);
        }

        globalPropertiesMap.put(fullPath, configProperty);
    }

    private static boolean isSupportedPropertyType(final Class<?> type) {
        boolean isSupported = type.equals(String.class) ||
                type.equals(Byte.class) ||
                type.equals(byte.class) ||
                type.equals(Integer.class) ||
                type.equals(int.class) ||
                type.equals(Long.class) ||
                type.equals(long.class) ||
                type.equals(Short.class) ||
                type.equals(short.class) ||
                type.equals(Float.class) ||
                type.equals(float.class) ||
                type.equals(Double.class) ||
                type.equals(double.class) ||
                type.equals(Boolean.class) ||
                type.equals(boolean.class) ||
                type.equals(Character.class) ||
                type.equals(char.class) ||
                List.class.isAssignableFrom(type) ||
                Map.class.isAssignableFrom(type) ||
                DocRef.class.isAssignableFrom(type) ||
                Enum.class.isAssignableFrom(type);

        LOGGER.trace("isSupportedPropertyType({}), returning: {}", type, isSupported);
        return isSupported;
    }

    private void updatePropertyFromConfigAnnotations(final ConfigProperty configProperty, final Prop prop) {
        // Editable by default unless found otherwise below
        configProperty.setEditable(true);

        for (final Annotation declaredAnnotation : prop.getGetter().getDeclaredAnnotations()) {
            Class<? extends Annotation> annotationType = declaredAnnotation.annotationType();

            if (annotationType.equals(JsonPropertyDescription.class)) {
                configProperty.setDescription(((JsonPropertyDescription) declaredAnnotation).value());
            } else if (annotationType.equals(ReadOnly.class)) {
                configProperty.setEditable(false);
            } else if (annotationType.equals(Password.class)) {
                configProperty.setPassword(true);
            } else if (annotationType.equals(RequiresRestart.class)) {
                RequiresRestart.RestartScope scope = ((RequiresRestart) declaredAnnotation).value();
                switch (scope) {
                    case SYSTEM:
                        configProperty.setRequireRestart(true);
                        break;
                    case UI:
                        configProperty.setRequireUiRestart(true);
                        break;
                    default:
                        throw new RuntimeException("Should never get here");
                }
                configProperty.setEditable(false);
            }
        }
    }

    private String getNameFromAnnotation(final Method method) {
        for (final Annotation declaredAnnotation : method.getDeclaredAnnotations()) {
            if (declaredAnnotation.annotationType().equals(JsonProperty.class)) {
                final JsonProperty jsonProperty = (JsonProperty) declaredAnnotation;
                return jsonProperty.value();
            }
        }
        return null;
    }


    // pkg private for testing
    static String convertToString(final Object value) {
        List<String> availableDelimiters = new ArrayList<>(DELIMITERS);
        return convertToString(value, availableDelimiters);
    }

    private static String convertToString(final Object value, final List<String> availableDelimiters) {
        if (value != null) {
            if (isSupportedPropertyType(value.getClass())) {
                if (value instanceof List) {
                    return listToString((List<?>) value, availableDelimiters);
                } else if (value instanceof Map) {
                    return mapToString((Map<?, ?>) value, availableDelimiters);
                } else if (value instanceof DocRef) {
                    return docRefToString((DocRef) value, availableDelimiters);
                } else if (value instanceof Enum) {
                    return enumToString((Enum) value);
                } else {
                    return value.toString();
                }
            } else {
                throw new RuntimeException(LogUtil.message("Value [{}] of type {}, is not a supported type",
                        value, value.getClass().getName()));
            }
        } else {
            return null;
        }
    }

    private String getStringValue(final Prop prop) {
        final Object value = prop.getValueFromConfigObject();
        if (value != null) {
            return convertToString(value);
        }
        return null;
    }

    private String getDefaultValue(final Prop prop) {
        if (prop != null) {
            final Object value = prop.getValueFromConfigObject();
            if (value != null) {
                return convertToString(value);
            }
        }
        return null;
    }

    private static Object convertToObject(final String value, final Type genericType) {
        if (value == null) {
            return null;
        }

        Class<?> type = getDataType(genericType);

        if (type.equals(String.class)) {
            return value;
        } else if (type.equals(Byte.class) || type.equals(byte.class)) {
            return Byte.valueOf(value);
        } else if (type.equals(Integer.class) || type.equals(int.class)) {
            return Integer.valueOf(value);
        } else if (type.equals(Long.class) || type.equals(long.class)) {
            return Long.valueOf(value);
        } else if (type.equals(Short.class) || type.equals(short.class)) {
            return Short.valueOf(value);
        } else if (type.equals(Float.class) || type.equals(float.class)) {
            return Float.valueOf(value);
        } else if (type.equals(Double.class) || type.equals(double.class)) {
            return Double.valueOf(value);
        } else if (type.equals(Boolean.class) || type.equals(boolean.class)) {
            return Boolean.valueOf(value);
        } else if ((type.equals(Character.class) || type.equals(char.class)) && value.length() > 0) {
            return value.charAt(0);
        } else if (List.class.isAssignableFrom(type)) {
            // determine the type of the list items
            Class<?> itemType = getDataType(getGenericTypes(genericType).get(0));
            return stringToList(value, itemType);
//        } else if (type.isAssignableFrom(Map.class)) {
        } else if (Map.class.isAssignableFrom(type)) {
            // determine the types of the keys and values
            Class<?> keyType = getDataType(getGenericTypes(genericType).get(0));
            Class<?> valueType = getDataType(getGenericTypes(genericType).get(1));
            return stringToMap(value, keyType, valueType);
        } else if (type.equals(DocRef.class)) {
            return stringToDocRef(value);
        } else if (Enum.class.isAssignableFrom(type)) {
            return stringToEnum(value, type);
        }

        LOGGER.error("Unable to convert value [{}] of type [{}] to an Object", value, type);
        throw new RuntimeException(LogUtil.message("Type [{}] is not supported for value [{}]", genericType, value));
    }


    private static String listToString(final List<?> list, final List<String> availableDelimiters) {

        if (list.isEmpty()) {
            return "";
        }
        List<String> strList = list.stream()
                .map(ConfigMapper::convertToString)
                .collect(Collectors.toList());

        String allText = String.join("", strList);

        String delimiter = getDelimiter(allText, availableDelimiters);

        // prefix the delimited form with the delimiter so when we deserialise
        // we know what the delimiter is
        return delimiter + String.join(delimiter, strList);
    }

    private static String mapToString(final Map<?, ?> map, final List<String> availableDelimiters) {
        if (map.isEmpty()) {
            return "";
        }
        // convert keys/values to strings
        final List<Map.Entry<String, String>> strEntries = map.entrySet().stream()
                .map(entry -> {
                    String key = ConfigMapper.convertToString(entry.getKey());
                    String value = ConfigMapper.convertToString(entry.getValue());
                    return Map.entry(key, value);
                })
                .collect(Collectors.toList());

        // join all strings into one fat string
        final String allText = strEntries.stream()
                .map(entry -> entry.getKey() + entry.getValue())
                .collect(Collectors.joining());

        final String keyValueDelimiter = getDelimiter(allText, availableDelimiters);
        final String entryDelimiter = getDelimiter(allText, availableDelimiters);

        // prefix the delimited form with the delimiters so when we deserialise
        // we know what the delimiters are
        return entryDelimiter + keyValueDelimiter + strEntries.stream()
                .map(entry ->
                        entry.getKey() + keyValueDelimiter + entry.getValue())
                .collect(Collectors.joining(entryDelimiter));
    }

    private static String docRefToString(final DocRef docRef, final List<String> availableDelimiters) {
        String allText = String.join("", docRef.getType(), docRef.getUuid(), docRef.getName());
        String delimiter = getDelimiter(allText, availableDelimiters);

        // prefix the delimited form with the delimiter so when we deserialise
        // we know what the delimiter is
        return delimiter
                + "docRef("
                + String.join(delimiter, docRef.getType(), docRef.getUuid(), docRef.getName())
                + ")";
    }

    private static String enumToString(final Enum enumInstance) {
        return enumInstance.name();
    }

    private static <T> List<T> stringToList(final String serialisedForm, final Class<T> type) {
        try {
            if (serialisedForm == null || serialisedForm.isEmpty()) {
                return Collections.emptyList();
            }

            String delimiter = String.valueOf(serialisedForm.charAt(0));
            String delimitedValue = serialisedForm.substring(1);

            return StreamSupport.stream(Splitter.on(delimiter).split(delimitedValue).spliterator(), false)
                    .map(str -> convertToObject(str, type))
                    .map(type::cast)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException(LogUtil.message(
                    "Error de-serialising a List<?> from [{}]", serialisedForm), e);
        }
    }

    private static <K, V> Map<K, V> stringToMap(
            final String serialisedForm,
            final Class<K> keyType,
            final Class<V> valueType) {

        final String entryDelimiter = String.valueOf(serialisedForm.charAt(0));
        final String keyValueDelimiter = String.valueOf(serialisedForm.charAt(1));

        // now remove the delimiters from our value
        final String delimitedValue = serialisedForm.substring(2);

        return StreamSupport.stream(Splitter.on(entryDelimiter).split(delimitedValue).spliterator(), false)
                .map(keyValueStr -> {
                    final List<String> parts = Splitter.on(keyValueDelimiter).splitToList(keyValueStr);

                    if (parts.size() < 1 || parts.size() > 2) {
                        throw new RuntimeException(LogUtil.message("Too many parts [{}] in value [{}], whole value [{}]",
                                parts.size(), keyValueStr, serialisedForm));
                    }

                    String keyStr = parts.get(0);
                    String valueStr = parts.size() == 1 ? null : parts.get(1);

                    K key = keyType.cast(convertToObject(keyStr, keyType));
                    V value = valueStr != null ? valueType.cast(convertToObject(valueStr, valueType)) : null;

                    return Map.entry(key, value);
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static DocRef stringToDocRef(final String serialisedForm) {

        try {
            final String delimiter = String.valueOf(serialisedForm.charAt(0));
            String delimitedValue = serialisedForm.substring(1);

            delimitedValue = delimitedValue.replace(DOCREF_PREFIX, "");
            delimitedValue = delimitedValue.replace(")", "");

            final List<String> parts = Splitter.on(delimiter).splitToList(delimitedValue);

            return new DocRef.Builder()
                    .type(parts.get(0))
                    .uuid(parts.get(1))
                    .name(parts.get(2))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(LogUtil.message(
                    "Error de-serialising a docRef from [{}]", serialisedForm), e);
        }
    }

    private static Enum stringToEnum(final String serialisedForm, final Class<?> type) {
        return Enum.valueOf((Class<Enum>) type, serialisedForm.toUpperCase());
    }

    private static Class getDataType(Class clazz) {
        if (clazz.isPrimitive()) {
            return clazz;
        }

        if (clazz.isArray()) {
            return getDataType(clazz.getComponentType());
        }

        return clazz;
    }

    private static Class getDataType(Type type) {
        if (type instanceof Class) {
            return getDataType((Class) type);
        } else if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            return getDataType(pt.getRawType());
        } else {
            throw new RuntimeException(LogUtil.message("Unexpected type of type {}",
                    type.getClass().getName()));
        }
    }

    private static List<Type> getGenericTypes(Type type) {
        if (type instanceof Class) {
            return Collections.emptyList();
        } else if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            Type[] specificTypes = pt.getActualTypeArguments();

            return Arrays.asList(specificTypes);
        } else {
            throw new RuntimeException(LogUtil.message("Unexpected type of type {}",
                    type.getClass().getName()));
        }
    }

    private static String getDelimiter(final String allText, final List<String> availableDelimiters) {
        // find the first delimiter that does not appear in the text
        String chosenDelimiter = availableDelimiters.stream()
                .filter(delimiter -> !allText.contains(delimiter))
                .findFirst()
                .orElseThrow(() ->
                        new RuntimeException("Exhausted all delimiters"));
        // remove the chosen delimiter so it doesn't get re-used for another purpose
        availableDelimiters.remove(chosenDelimiter);
        return chosenDelimiter;
    }

    private static void applyCommonDbConfig(AppConfig appConfig) {
        LOGGER.debug("applyCommonDbConfig() called");

        // get an object with the hard coded defaults
        final DbConfig defaultConfig = new DbConfig();
        final DbConfig commonConfig = appConfig.getCommonDbConfig();

        if (!defaultConfig.equals(commonConfig)) {
            LOGGER.info("Common database config is non-default so will be used as fall-back configuration");

            // find all getters that return a class that implements HasDbConfig
            // Assumes db config only appears as a child of top level
            for (Method method : appConfig.getClass().getMethods()) {
                if (method.getName().startsWith("get")
                        && HasDbConfig.class.isAssignableFrom(method.getReturnType())) {
                    try {
                        HasDbConfig serviceConfig = (HasDbConfig) method.invoke(appConfig);
                        applyCommonDbConfig(defaultConfig, commonConfig, serviceConfig);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException(LogUtil.message("Unable to invoke method {}", method.getName()));
                    }
                }
            }
        } else {
            LOGGER.debug("commonConfig matches default so nothing to do");
        }
    }

    private static void applyCommonDbConfig(final DbConfig defaultConfig,
                                            final DbConfig commonConfig,
                                            final HasDbConfig serviceConfig) {

        final DbConfig serviceDbConfig = serviceConfig.getDbConfig();

        if (defaultConfig.equals(serviceDbConfig)) {
            // The service specific config has default values for its db config
            // so apply all the common values.
            LOGGER.info("Applying common DB config to {}", serviceConfig.getClass().getSimpleName());
            FieldMapper.copy(commonConfig, serviceDbConfig, FieldMapper.CopyOptions.DONT_COPY_NULLS);
        } else {
            LOGGER.debug("{} has custom DB config so leaving it as is", serviceConfig.getClass().getSimpleName());
        }
    }

    public static class ConfigMapperFactory implements Provider<ConfigMapper> {

        private final ConfigMapper configMapper;

        @Inject
        ConfigMapperFactory(final AppConfig appConfig) {
            configMapper = new ConfigMapper(appConfig);
        }

        @Override
        public ConfigMapper get() {
            return configMapper;
        }
    }

    public static class UnknownPropertyException extends RuntimeException {
        /**
         * Constructs a new runtime exception with the specified detail message.
         * The cause is not initialized, and may subsequently be initialized by a
         * call to {@link #initCause}.
         *
         * @param message the detail message. The detail message is saved for
         *                later retrieval by the {@link #getMessage()} method.
         */
        UnknownPropertyException(final String message) {
            super(message);
        }

        /**
         * Constructs a new runtime exception with the specified detail message and
         * cause.  <p>Note that the detail message associated with
         * {@code cause} is <i>not</i> automatically incorporated in
         * this runtime exception's detail message.
         *
         * @param message the detail message (which is saved for later retrieval
         *                by the {@link #getMessage()} method).
         * @param cause   the cause (which is saved for later retrieval by the
         *                {@link #getCause()} method).  (A {@code null} value is
         *                permitted, and indicates that the cause is nonexistent or
         *                unknown.)
         * @since 1.4
         */
        UnknownPropertyException(final String message, final Throwable cause) {
            super(message, cause);
        }
    };
}
