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
import com.google.common.base.CaseFormat;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.app.AppConfig;
import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.ConfigPropertyValidationException;
import stroom.config.global.shared.OverrideValue;
import stroom.docref.DocRef;
import stroom.util.config.FieldMapper;
import stroom.util.config.PropertyUtil;
import stroom.util.config.PropertyUtil.Prop;
import stroom.util.config.annotations.Password;
import stroom.util.config.annotations.ReadOnly;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.io.ByteSize;
import stroom.util.logging.LogUtil;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.PropertyPath;
import stroom.util.time.StroomDuration;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;
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

    // In order of preference
    private static final List<String> VALID_DELIMITERS_LIST = List.of(
            "|", ":", ";", ",", "!", "/", "\\", "#", "@", "~", "-", "_", "=", "+", "?");
    private static final Set<String> VALID_DELIMITERS_SET = new HashSet<>(VALID_DELIMITERS_LIST);

    private static final PropertyPath ROOT_PROPERTY_PATH = PropertyPath.fromParts("stroom");
    private static final String DOCREF_PREFIX = "docRef";
    private static final Pattern DOCREF_PATTERN = Pattern.compile("^" + DOCREF_PREFIX + "\\([^)]+\\)$");
    public static final String LIST_EXAMPLE = "|item1|item2|item3";
    public static final String MAP_EXAMPLE = "|:key1:value1|:key2:value2|:key3:value3";
    public static final String DOCREF_EXAMPLE = ","
        + DOCREF_PREFIX
        + "(StatisticStore,934a1600-b456-49bf-9aea-f1e84025febd,Heap Histogram Bytes)";

    // The guice bound appConfig
    private final AppConfig appConfig;

    // A map of config properties keyed on the fully qualified prop path (i.e. stroom.path.temp)
    // This is the source of truth for all properties. It is used to update the guice injected object model
    private final ConcurrentMap<PropertyPath, ConfigProperty> globalPropertiesMap = new ConcurrentHashMap<>();

    // A map of property accessor objects keyed on the fully qualified prop path (i.e. stroom.path.temp)
    private final Map<PropertyPath, Prop> propertyMap = new HashMap<>();

    // TODO Created this with a view to improving the ser/deser of the values but it needs
    //   more thought.  Leaving it here for now.
//    private static final Map<Class<?>, Mapping> MAPPERS = new HashMap<>();
//
//    static {
//        map(boolean.class, StroomDuration::parse);
//        map(StroomDuration.class, StroomDuration::parse, Object::toString);
//        map(List.class, (list, genericType) -> {
//            final Class<?> itemType = getDataType(getGenericTypes(genericType).get(0));
//            return createDelimitedConversionFunc(ConfigMapper::listToString);
//        }, Object::toString);
//
//    }

    @Inject
    public ConfigMapper(final AppConfig appConfig) {
        LOGGER.info("Initialising ConfigMapper with class {}", appConfig.getClass().getName());

        this.appConfig = appConfig;

        // The values in the passed AppConfig will have been set initially from the compile-time defaults and
        // then from the DropWizard yaml file on app boot.
        // We want to know the default values as defined by the compile-time initial values of
        // the instance variables in the AppConfig tree.  This is so we can make the default values available
        // to the config UI.  Therefore create our own vanilla AppConfig tree and walk it to populate
        // globalPropertiesMap with the defaults.
        LOGGER.debug("Building globalPropertiesMap from compile-time default values and annotations");

        // Pass in an empty hashmap because we are not parsing the actual guice bound appConfig. We are only
        // populating the globalPropertiesMap so the passed hashmap is thrown away.
        addConfigObjectMethods(
                getVanillaAppConfig(),
                ROOT_PROPERTY_PATH,
                new HashMap<>(),
                this::defaultValuePropertyConsumer);

        // Now add in any values from the yaml
        updateConfigFromYaml(appConfig);

        if (LOGGER.isDebugEnabled()) {
            final Set<PropertyPath> onlyInGlobal = new HashSet<>(globalPropertiesMap.keySet());
            onlyInGlobal.removeAll(propertyMap.keySet());
            onlyInGlobal.forEach(propertyPath -> LOGGER.info("Only in globalPropertiesMap - [{}]", propertyPath));

            final Set<PropertyPath> onlyInPropertyMap = new HashSet<>(propertyMap.keySet());
            onlyInPropertyMap.removeAll(globalPropertiesMap.keySet());
            onlyInPropertyMap.forEach(propertyPath -> LOGGER.info("Only in propertyMap -         [{}]", propertyPath));
        }
    }

    private AppConfig getVanillaAppConfig() {
        try {
           return appConfig.getClass().getDeclaredConstructor().newInstance();
        } catch (InstantiationException
                | IllegalAccessException
                | InvocationTargetException
                | NoSuchMethodException e) {
            throw new RuntimeException(LogUtil.message("Unable to call constructor on class {}",
                    appConfig.getClass().getName()), e);
        }
    }

    /**
     * Will copy the contents of the passed {@link AppConfig} into the guice bound {@link AppConfig}
     * and update the globalPropertiesMap.
     * It will also apply common database config to all other database config objects.
     */
    private void updateConfigFromYaml(final AppConfig newAppConfig) {
        synchronized (this) {
            // The object will be different when we are re-reading the yaml into AppConfig
            // as part of AppConfigMonitor
            if (System.identityHashCode(newAppConfig) != System.identityHashCode(appConfig)) {
                // We have been passed a different object to our instance appConfig so copy all
                // the values over.
                LOGGER.debug("Copying values from newAppConfig to appConfig");
                FieldMapper.copy(newAppConfig, appConfig);
            }

            // Now walk the AppConfig object model from the DropWiz YAML updating globalPropertiesMap with the
            // YAML values where present.
            LOGGER.debug("Adding yaml config values into global property map");
            addConfigObjectMethods(
                    newAppConfig,
                    ROOT_PROPERTY_PATH,
                    propertyMap,
                    this::yamlPropertyConsumer);
        }
    }

    /**
     * @return True if fullPath is a valid path to a config value
     */
    public boolean validatePropertyPath(final PropertyPath fullPath) {
        return propertyMap.get(fullPath) != null;
    }

    Collection<ConfigProperty> getGlobalProperties() {
        return globalPropertiesMap.values();
    }

    Optional<ConfigProperty> getGlobalProperty(final PropertyPath propertyPath) {
        Objects.requireNonNull(propertyPath);
        return Optional.ofNullable(globalPropertiesMap.get(propertyPath));
    }

    public Optional<Prop> getProp(final PropertyPath propertyPath) {
        return Optional.ofNullable(propertyMap.get(propertyPath));
    }

    /**
     * Verifies that the passed value for the property with fullPath can be converted into
     * the appropriate object type
     */
    void validateValueSerialisation(final PropertyPath fullPath, final String valueAsString) {
        // If the string form can't be converted then an exception will be thrown
        convertValue(fullPath, valueAsString);
    }

    Object convertValue(final PropertyPath fullPath, final String valueAsString) {
        final Prop prop = propertyMap.get(fullPath);
        if (prop != null) {
            final Type genericType = prop.getValueType();
            return convertToObject(valueAsString, genericType);
        } else {
            throw new UnknownPropertyException(LogUtil.message("No configProperty for {}", fullPath));
        }
    }

    void decorateAllDbConfigProperty(final Collection<ConfigProperty> dbConfigProperties) {

        synchronized (this) {
            // Ensure our in memory global prop
            dbConfigProperties.forEach(this::decorateDbConfigProperty);

            // Now ensure all propertyMap props not in the list of db props have no db override set.
            // I.e. another node could have removed the db override.

            Map<PropertyPath, ConfigProperty> dbPropsMap = dbConfigProperties.stream()
                .collect(Collectors.toMap(ConfigProperty::getName, Function.identity()));

            globalPropertiesMap.entrySet()
                .stream()
                .filter(entry -> entry.getValue().hasDatabaseOverride())
                .filter(entry -> !dbPropsMap.containsKey(entry.getKey()))
                .forEach(entry -> {
                    final ConfigProperty globalProp = entry.getValue();
                    globalProp.setDatabaseOverrideValue(OverrideValue.unSet(String.class));

                    final Prop prop = propertyMap.get(entry.getKey());
                    if (prop != null) {
                        // Now set the new effective value on our guice bound appConfig instance
                        final Type genericType = prop.getValueType();
                        final Object typedValue = convertToObject(
                            globalProp.getEffectiveValue().orElse(null), genericType);
                        prop.setValueOnConfigObject(typedValue);
                    } else {
                        throw new RuntimeException(LogUtil.message(
                            "Not expecting prop to be null for {}", entry.getKey().toString()));
                    }
                });
        }
    }

    /**
     * @param dbConfigProperty The config property object obtained from the database
     * @return The updated typed value from the object model
     */
    ConfigProperty decorateDbConfigProperty(final ConfigProperty dbConfigProperty) {
        Objects.requireNonNull(dbConfigProperty);

        LOGGER.debug("decorateDbConfigProperty() called for {}", dbConfigProperty.getName());

        final PropertyPath fullPath = dbConfigProperty.getName();

        synchronized (this) {
            ConfigProperty globalConfigProperty = getGlobalProperty(fullPath)
                    .orElseThrow(() ->
                            new UnknownPropertyException(LogUtil.message("No configProperty for {}", fullPath)));

            final Prop prop = propertyMap.get(fullPath);
            if (prop != null) {

                // Update all the DB related values from the passed DB config prop
                globalConfigProperty.setId(dbConfigProperty.getId());
                globalConfigProperty.setDatabaseOverrideValue(dbConfigProperty.getDatabaseOverrideValue());
                globalConfigProperty.setVersion(dbConfigProperty.getVersion());
                globalConfigProperty.setCreateTimeMs(dbConfigProperty.getCreateTimeMs());
                globalConfigProperty.setCreateUser(dbConfigProperty.getCreateUser());
                globalConfigProperty.setUpdateTimeMs(dbConfigProperty.getUpdateTimeMs());
                globalConfigProperty.setUpdateUser(dbConfigProperty.getUpdateUser());

                // Now we have updated the globalConfigProperty with the value from the DB
                // we can ask it what the effective value is now and set that on our
                // guice bound appConfig instance.
                final Type genericType = prop.getValueType();
                final Object typedValue = convertToObject(
                        globalConfigProperty.getEffectiveValue().orElse(null), genericType);
                prop.setValueOnConfigObject(typedValue);

                return globalConfigProperty;
            } else {
                throw new UnknownPropertyException(LogUtil.message("No prop object for {}", fullPath));
            }
        }
    }

    private void addConfigObjectMethods(final AbstractConfig config,
                                        final PropertyPath path,
                                        final Map<PropertyPath, Prop> propertyMap,
                                        final BiConsumer<PropertyPath, Prop> propConsumer) {
        LOGGER.trace("addConfigObjectMethods({}, {}, .....)", config, path);

        // Add this ConfigMapper instance to the IsConfig so it can do name resolution
//        config.setConfigPathResolver(this);
        config.setBasePath(path);

        // Add our object with its path to the map
//        configInstanceToPathMap.put(config, path);

        final Map<String, Prop> properties = PropertyUtil.getProperties(config);
        properties.forEach((k, prop) -> {
            LOGGER.trace("prop: {}", prop);
            final Method getter = prop.getGetter();

            // The prop may have a JsonPropery annotation that defines its name
            final String specifiedName = getNameFromAnnotation(getter);
            final String name = Strings.isNullOrEmpty(specifiedName)
                    ? prop.getName()
                    : specifiedName;

            final PropertyPath fullPath = path.merge(name);

            final Class<?> valueType = prop.getValueClass();

            final Object value = prop.getValueFromConfigObject();
            if (isSupportedPropertyType(valueType)) {
                // This is a leaf, i.e. a property so add it to our map
                propertyMap.put(fullPath, prop);

                // Now let the consumer do something to it
                propConsumer.accept(fullPath, prop);
            } else if (AbstractConfig.class.isAssignableFrom(valueType)) {
                // This must be a branch, i.e. config object so recurse into that
                if (value != null) {
                    AbstractConfig childConfigObject = (AbstractConfig) value;
                    addConfigObjectMethods(
                        childConfigObject, fullPath, propertyMap, propConsumer);
                }
            } else {
                // This is not expected
                throw new RuntimeException(LogUtil.message(
                        "Unexpected bean property of type [{}], expecting an instance of {}, or a supported type.",
                        valueType.getName(),
                        AbstractConfig.class.getSimpleName()));
            }
        });
    }

    private void yamlPropertyConsumer(final PropertyPath fullPath, final Prop yamlProp) {

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

    private void defaultValuePropertyConsumer(final PropertyPath fullPath, final Prop defaultProp) {

        // Create global property.
        final String defaultValueAsStr = getDefaultValue(defaultProp);

        // build a new ConfigProperty object from our Prop and our defaults
        final ConfigProperty configProperty = new ConfigProperty(fullPath, defaultValueAsStr);
        // Add all the meta data for the prop
        updatePropertyFromConfigAnnotations(configProperty, defaultProp);

        if (defaultValueAsStr == null) {
            LOGGER.trace("Property {} has no default value", fullPath);
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
                Enum.class.isAssignableFrom(type) ||
                Path.class.isAssignableFrom(type) ||
                StroomDuration.class.isAssignableFrom(type) ||
                ByteSize.class.isAssignableFrom(type);

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
            }
        }
        configProperty.setDataTypeName(getDataTypeName(prop.getValueType()));
    }

    private static String getDataTypeName(final Type type) {
        try {
            if (type instanceof Class) {
                final Class<?> valueClass = (Class<?>) type;
                String dataTypeName;

                if (valueClass.equals(int.class)) {
                    dataTypeName = "Integer";
                } else if (valueClass.equals(Enum.class)) {
                    dataTypeName = "Enumeration";
                } else if (valueClass.equals(List.class) || valueClass.equals(Map.class)) {
                    dataTypeName = CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, valueClass.getSimpleName()) + " of ";
                } else {
                    dataTypeName = CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, valueClass.getSimpleName());
                }
                return dataTypeName;
            } else if (type instanceof ParameterizedType) {
                final ParameterizedType parameterizedType = (ParameterizedType) type;
                final String rawTypeName = getDataTypeName(parameterizedType.getRawType());

                if (parameterizedType.getActualTypeArguments() != null) {
                    final String genericTypes = Arrays.stream(parameterizedType.getActualTypeArguments())
                            .map(ConfigMapper::getDataTypeName)
                            .collect(Collectors.joining(", "));
                    return rawTypeName + genericTypes;
                } else {
                    return rawTypeName;
                }
            } else {
                return "";
            }
        } catch (Exception e) {
            throw new RuntimeException(LogUtil.message(
                "Error getting type name for {}: {}", type, e.getMessage()));
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
        List<String> availableDelimiters = new ArrayList<>(VALID_DELIMITERS_LIST);
        return convertToString(value, availableDelimiters);
    }

    static Function<Object, String> createDelimitedConversionFunc(
            final BiFunction<Object, List<String>, String> conversionFunc) {

        List<String> availableDelimiters = new ArrayList<>(VALID_DELIMITERS_LIST);
        return object -> conversionFunc.apply(object, availableDelimiters);
    }

    static void validateDelimiter(final String serialisedForm,
                                  final int delimiterPosition,
                                  final String positionName,
                                  final String exampleText) {
        if (serialisedForm.length() < delimiterPosition + 1) {
            throw new RuntimeException(LogUtil.message("Delimiter position {} is out of bounds for {}",
                delimiterPosition, serialisedForm));
        }
        final String delimiter = String.valueOf(serialisedForm.charAt(delimiterPosition));

        if (!VALID_DELIMITERS_SET.contains(delimiter)) {
            throw new RuntimeException(LogUtil.message(
                "[{}] does not contain a valid delimiter as its {} character. " +
                    "Valid delimiters are [{}]. " +
                    "For example [{}]",
                serialisedForm, positionName, String.join("", VALID_DELIMITERS_LIST), exampleText));
        }
    }

    private static String convertToString(final Object value,
                                          final List<String> availableDelimiters) {
        if (value != null) {
            if (isSupportedPropertyType(value.getClass())) {
                if (value instanceof List) {
                    return listToString((List<?>) value, availableDelimiters);
                } else if (value instanceof Map) {
                    return mapToString((Map<?, ?>) value, availableDelimiters);
                } else if (value instanceof DocRef) {
                    return docRefToString((DocRef) value, availableDelimiters);
                } else if (value instanceof Enum) {
                    return enumToString((Enum<?>) value);
                } else {
                    return value.toString();
                }
            } else {
                throw new RuntimeException(LogUtil.message(
                    "Value [{}] of type {}, is not a supported type",
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

    // pkg private for testing
    static Object convertToObject(final String value, final Type genericType) {
        if (value == null) {
            return null;
        }

        Class<?> type = getDataType(genericType);

        try {
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
                return parseBoolean(value);
            } else if ((type.equals(Character.class) || type.equals(char.class)) && value.length() > 0) {
                return value.charAt(0);
            } else if (List.class.isAssignableFrom(type)) {
                // determine the type of the list items
                final Class<?> itemType = getGenericsParam(genericType, 0);
                return stringToList(value, itemType);
            } else if (Map.class.isAssignableFrom(type)) {
                // determine the types of the keys and values
                final Class<?> keyType = getGenericsParam(genericType, 0);
                final Class<?> valueType = getGenericsParam(genericType, 1);
                return stringToMap(value, keyType, valueType);
            } else if (type.equals(DocRef.class)) {
                return stringToDocRef(value);
            } else if (Enum.class.isAssignableFrom(type)) {
                return stringToEnum(value, type);
            } else if (Path.class.isAssignableFrom(type)) {
                return Path.of(value);
            } else if (StroomDuration.class.isAssignableFrom(type)) {
                return StroomDuration.parse(value);
            } else if (ByteSize.class.isAssignableFrom(type)) {
                return ByteSize.parse(value);
            }
        } catch (Exception e) {
            // Don't include the original exception else gwt uses the msg of the
            // original which is not very user friendly. Enable debug to see the stack
            LOGGER.debug(LogUtil.message(
                "Unable to convert value [{}] to type {} due to: {}",
                    value, genericType, e.getMessage()), e);
            throw new ConfigPropertyValidationException(LogUtil.message(
                "Unable to convert value [{}] to type {} due to: {}",
                    value, getDataTypeName(genericType), e.getMessage()), e);
        }

        LOGGER.error("Unable to convert value [{}] of type [{}] to an Object", value, type);
        throw new ConfigPropertyValidationException(LogUtil.message(
            "Type [{}] is not supported for value [{}]", genericType, value));
    }

    private static Class<?> getGenericsParam(final Type typeWithGenerics, final int index) {
        List<Type> genericsParamTypes = getGenericTypes(typeWithGenerics);
        if (genericsParamTypes.isEmpty()) {
           throw new RuntimeException(LogUtil.message(
               "Unable to get generics parameter {} for type {} as it has no parameterised types",
               index, typeWithGenerics));
        }
        if (index >= genericsParamTypes.size()) {
            throw new IllegalArgumentException(LogUtil.message("Index {} is out of bounds for types {}",
                index, genericsParamTypes));
        }

        return getDataType(genericsParamTypes.get(index));
    }

    private static Boolean parseBoolean(final String str) {
       if (str.equalsIgnoreCase("true")) {
           return Boolean.TRUE;
       } else if (str.equalsIgnoreCase("false")) {
           return Boolean.FALSE;
       } else {
           throw new ConfigPropertyValidationException(
               LogUtil.message("Cannot convert [{}] into a boolean. Valid values are [true|false] ignoring case.", str));
       }
    }


    private static String listToString(final List<?> list,
                                       final List<String> availableDelimiters) {

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

    private static String docRefToString(final DocRef docRef,
                                         final List<String> availableDelimiters) {
        String allText = String.join(
            "", docRef.getType(), docRef.getUuid(), docRef.getName());
        String delimiter = getDelimiter(allText, availableDelimiters);

        // prefix the delimited form with the delimiter so when we deserialise
        // we know what the delimiter is
        return delimiter
            + "docRef("
            + String.join(
                delimiter,
                docRef.getType(),
                docRef.getUuid(),
                docRef.getName())
            + ")";
    }

    private static String enumToString(final Enum<?> enumInstance) {
        return enumInstance.name();
    }

    private static <T> List<T> stringToList(final String serialisedForm,
                                            final Class<T> type) {
        if (serialisedForm == null || serialisedForm.isEmpty()) {
            return Collections.emptyList();
        } else {
            final String delimiter = String.valueOf(serialisedForm.charAt(0));
            validateDelimiter(
                serialisedForm,
                0,
                "first",
                LIST_EXAMPLE);

            try {

                String delimitedValue = serialisedForm.substring(1);

                return StreamSupport.stream(
                    Splitter
                        .on(delimiter)
                        .split(delimitedValue)
                        .spliterator(), false)
                    .map(str -> convertToObject(str, type))
                    .map(type::cast)
                    .collect(Collectors.toList());
            } catch (Exception e) {
                throw new RuntimeException(LogUtil.message(
                    "Error de-serialising a List<?> from [{}]", serialisedForm), e);
            }
        }
    }

    private static <K, V> Map<K, V> stringToMap(
            final String serialisedForm,
            final Class<K> keyType,
            final Class<V> valueType) {

        if (serialisedForm == null || serialisedForm.isEmpty()) {
            return Collections.emptyMap();
        } else {

            final String entryDelimiter = String.valueOf(serialisedForm.charAt(0));
            validateDelimiter(serialisedForm,0,"first", MAP_EXAMPLE);

            final String keyValueDelimiter = String.valueOf(serialisedForm.charAt(1));
            validateDelimiter(serialisedForm,1,"second", MAP_EXAMPLE);

            // now remove the delimiters from our value
            final String delimitedValue = serialisedForm.substring(2);

            return StreamSupport.stream(
                Splitter
                    .on(entryDelimiter)
                    .split(delimitedValue)
                    .spliterator(), false)
                .map(keyValueStr -> {
                    final List<String> parts = Splitter.on(keyValueDelimiter)
                        .splitToList(keyValueStr);

                    if (parts.size() < 1 || parts.size() > 2) {
                        throw new RuntimeException(LogUtil.message(
                            "Too many parts [{}] in value [{}], whole value [{}]",
                            parts.size(), keyValueStr, serialisedForm));
                    }

                    final String keyStr = parts.get(0);
                    final String valueStr = parts.size() == 1 ? null : parts.get(1);

                    final K key = keyType.cast(convertToObject(keyStr, keyType));
                    final V value = valueStr != null
                        ? valueType.cast(convertToObject(valueStr, valueType))
                        : null;

                    return Map.entry(key, value);
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
    }

    private static DocRef stringToDocRef(final String serialisedForm) {

        try {
            final String delimiter = String.valueOf(serialisedForm.charAt(0));
            validateDelimiter(serialisedForm, 0, "first", DOCREF_EXAMPLE);

            // Remove the delimiter off the front
            String delimitedValue = serialisedForm.substring(1);

            if (!DOCREF_PATTERN.matcher(delimitedValue).matches()) {
                throw new RuntimeException(LogUtil.message("Expecting [{}] to match [{}]",
                    delimitedValue, DOCREF_PATTERN.pattern()));
            }

            delimitedValue = delimitedValue.replace(DOCREF_PREFIX + "(", "");
            delimitedValue = delimitedValue.replace(")", "");

            final List<String> parts = Splitter.on(delimiter).splitToList(delimitedValue);
            if (parts.size() != 3) {
                throw new RuntimeException(LogUtil.message(
                    "Expecting three parts to a docRef: type, UUID and name. Found {}", parts.size()));
            }

            return new DocRef.Builder()
                    .type(parts.get(0))
                    .uuid(parts.get(1))
                    .name(parts.get(2))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(LogUtil.message(
                    "Error de-serialising a docRef from [{}] due to: {}", serialisedForm, e.getMessage()), e);
        }
    }

    private static Enum<?> stringToEnum(final String serialisedForm, final Class<?> type) {
        return Enum.valueOf((Class<Enum>) type, serialisedForm.toUpperCase());
    }

    private static Class<?> getDataType(Class<?> clazz) {
        if (clazz.isPrimitive()) {
            return clazz;
        }

        if (clazz.isArray()) {
            return getDataType(clazz.getComponentType());
        }

        return clazz;
    }

    private static Class<?> getDataType(Type type) {
        if (type instanceof Class) {
            return getDataType((Class<?>) type);
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

    private static String getDelimiter(final String allText,
                                       final List<String> availableDelimiters) {
        // find the first delimiter that does not appear in the text
        final String chosenDelimiter = availableDelimiters.stream()
                .filter(delimiter -> !allText.contains(delimiter))
                .findFirst()
                .orElseThrow(() ->
                        new RuntimeException("Exhausted all delimiters"));
        // remove the chosen delimiter so it doesn't get re-used for another purpose
        availableDelimiters.remove(chosenDelimiter);
        return chosenDelimiter;
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
    }

    // TODO Created these with a view to improving the ser/deser of the values but it needs
    //   more thought.  Leaving them here for now.
//    private Optional<Method> getParseMethod(final Class<?> clazz) {
//        return Arrays.stream(clazz.getDeclaredMethods())
//            .filter(method ->
//                method.getName().equals("parse")
//                    && method.getParameterCount() == 1
//                    && method.getParameterTypes()[0].equals(String.class)
//                    && method.getReturnType().equals(clazz))
//            .findFirst();
//    }
//
//    private static void map(final Class<?> propertyType,
//                            final Function<String, Object> deSerialiseFunc,
//                            final Function<Object, String> serialiseFunc) {
//        if (MAPPERS.containsKey(propertyType)) {
//            throw new RuntimeException(LogUtil.message("Class {} is already mapped", propertyType));
//        }
//        MAPPERS.put(propertyType, Mapping.of(propertyType, deSerialiseFunc, serialiseFunc));
//    }
//
//    private static void map(final Class<?> propertyType,
//                            final Function<String, Object> deSerialiseFunc) {
//        if (MAPPERS.containsKey(propertyType)) {
//            throw new RuntimeException(LogUtil.message("Class {} is already mapped", propertyType));
//        }
//        MAPPERS.put(propertyType, Mapping.of(propertyType, deSerialiseFunc));
//    }
//
//    private static class Mapping {
//        private final Class<?> propertyType;
//        private final BiFunction<String, Type, Object> deSerialiseFunc;
//        private final Function<Object, String> serialiseFunc;
//
//        private Mapping(final Class<?> propertyType,
//                        final BiFunction<String, Type, Object> deSerialiseFunc,
//                        final Function<Object, String> serialiseFunc) {
//            this.propertyType = propertyType;
//            this.deSerialiseFunc = deSerialiseFunc;
//            this.serialiseFunc = serialiseFunc;
//        }
//
//        static Mapping of(final Class<?> propertyType,
//                          final BiFunction<String, Type, Object> deSerialiseFunc,
//                          final Function<Object, String> serialiseFunc) {
//            return new Mapping(propertyType, deSerialiseFunc, serialiseFunc);
//        }
//
//        static Mapping of(final Class<?> propertyType,
//                          final BiFunction<String, Type, Object> deSerialiseFunc) {
//            return new Mapping(propertyType, deSerialiseFunc, obj ->
//                obj == null ? null : obj.toString()
//            );
//        }
//
//        Class<?> getPropertyType() {
//            return propertyType;
//        }
//
//        Function<Object, String> getSerialiseFunc() {
//            return serialiseFunc;
//        }
//
//        BiFunction<String, Type, Object> getDeSerialiseFunc() {
//            return deSerialiseFunc;
//        }
//    }
}
