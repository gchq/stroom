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

package stroom.config.global.impl;


import stroom.config.app.AppConfig;
import stroom.config.app.ConfigHolder;
import stroom.config.app.SuperDevUtil;
import stroom.config.common.AbstractDbConfig;
import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.ConfigProperty.SourceType;
import stroom.config.global.shared.ConfigPropertyValidationException;
import stroom.config.global.shared.OverrideValue;
import stroom.docref.DocRef;
import stroom.util.config.PropertyPathDecorator;
import stroom.util.config.PropertyUtil;
import stroom.util.config.PropertyUtil.ObjectInfo;
import stroom.util.config.PropertyUtil.Prop;
import stroom.util.config.annotations.Password;
import stroom.util.config.annotations.ReadOnly;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.io.ByteSize;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.BootStrapConfig;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PropertyPath;
import stroom.util.time.StroomDuration;
import stroom.util.xml.ParserConfig;
import stroom.util.xml.SAXParserSettings;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.CaseFormat;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

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
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;


/**
 * Responsible for mapping between the AppConfig object tree and a flat set of key value pairs.
 * The key for a leaf of the tree is a dot delimited path of all the branches to get to that leaf,
 * with 'stroom' as the root, e.g. 'stroom.pipeline.referenceData.purgeAge'.
 * <p>
 * The guice bound {@link AppConfig} object was de-serialised from the config.yml on boot.
 * {@link stroom.config.app.AppConfigModule} is responsible for ensuring that the tree is not
 * sparse and all branches are present with either their default compile times values or a value from
 * the yaml file. A null branch in the yaml will be replaced by a default object for that branch. A
 * missing leaf in the yaml will be replaced by the compile time default. A null value in the yaml will
 * be a null value.
 * <p>
 * Each branch of the tree (unless annotated with {@link stroom.util.shared.NotInjectableConfig} can be
 * injected into other classes to access the config. Config can be changed either by updating the config.yml
 * file or by setting a database override value. The effective value from the config is based on the following
 * precedence; yaml > database > default. The instance of this class is the source of truth for the config in
 * the system. If another node updates a property in the DB then a period refresh of the DB props will pick this
 * up and re-compute the effective config.
 * <p>
 * The guice binding is done via providers. This class holds a map of config object instances keyed by their
 * class. We have a set of generated provider methods to get the appropriate instance from the map. Classes
 * should inject config using Providers to ensure they always get the latest version. The map is replaced each
 * time config is changed as config objects are almost all immutable.
 * <p>
 * To make life more complicated the database override values are held as strings so need to be converted to/from
 * the types known to the {@link Prop} and {@link AppConfig} tree. This is a legacy from the properties UI pre-dating
 * yaml based config. At some point the db property values and the UI will be fully type aware. A map of
 * {@link ConfigProperty} objects (one for each prop) is used to hold the yaml, db and default values and contains
 * the logic for determining the effective value. These objects are the source of truth and are used to update the
 * {@link AppConfig} tree.
 */
@Singleton
public class ConfigMapper {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ConfigMapper.class);

    private static final boolean DEFAULT_BOOLEAN = false;
    private static final byte DEFAULT_BYTE = 0;
    private static final short DEFAULT_SHORT = 0;
    private static final int DEFAULT_INT = 0;
    private static final long DEFAULT_LONG = 0;
    private static final float DEFAULT_FLOAT = 0;
    private static final double DEFAULT_DOUBLE = 0;

    // In order of preference
    private static final List<String> VALID_DELIMITERS_LIST = List.of(
            "|", ":", ";", ",", "!", "/", "\\", "#", "@", "~", "-", "_", "=", "+", "?");
    private static final Set<String> VALID_DELIMITERS_SET = new HashSet<>(VALID_DELIMITERS_LIST);

    private static final String DOCREF_PREFIX = "docRef";
    private static final Pattern DOCREF_PATTERN = Pattern.compile("^" + DOCREF_PREFIX + "\\([^)]+\\)$");
    public static final String LIST_EXAMPLE = "|item1|item2|item3";
    public static final String MAP_EXAMPLE = "|:key1:value1|:key2:value2|:key3:value3";
    public static final String DOCREF_EXAMPLE =
            ","
            + DOCREF_PREFIX
            + "(StatisticStore,934a1600-b456-49bf-9aea-f1e84025febd,Heap Histogram Bytes)";

    // The de-serialised form of the config.yml with defaults merged in.
    // As it contains sensible defaults for most properties, each prop will either
    // be the hard coded default or a value set in the yaml.
    private final AppConfig bootStrapConfig;

    // A map of config properties keyed on the fully qualified prop path (i.e. stroom.path.temp)
    // This is the source of truth for all properties. It is used to update the guice injected object model
    private final ConcurrentMap<PropertyPath, ConfigProperty> globalPropertiesMap = new ConcurrentHashMap<>();

    // A map of property accessor objects keyed on the fully qualified prop path (i.e. stroom.path.temp)
    // Each Prop provides access set/get access to a default appConfig object tree.
    // The content never changes as the defaults are compile time values.
    private final Map<PropertyPath, Prop> defaultPropertiesMap = new HashMap<>();

    // All property paths that exist in this.appConfig
    private final Set<PropertyPath> allPropertyPaths;

    // The map to be used by guice to provide instances. This map will be replaced in its entirety
    // when config has changed, on the basis that config is not changed very often
    private volatile Map<Class<?>, AbstractConfig> configInstanceMap;

    // Holds the details about how to construct an instance of each branch of the prop tree
    private final Map<PropertyPath, ObjectInfo<? extends AbstractConfig>> objectInfoMap = new HashMap<>();

    private volatile boolean haveYamlOverridesBeenInitialised = false;
    private volatile boolean haveDbOverridesBeenInitialised = false;

    private final Supplier<AppConfig> defaultAppConfigSupplier;

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
    public ConfigMapper(final ConfigHolder configHolder) {
        this(NullSafe.getAsOptional(configHolder, ConfigHolder::getBootStrapConfig)
                        .orElseGet(AppConfig::new),
                AppConfig::new);
    }

    public ConfigMapper() {
        this(new AppConfig(), AppConfig::new);
    }

    public ConfigMapper(final AppConfig bootstrapConfig) {
        this(bootstrapConfig, AppConfig::new);
    }

    // pkg private for testing
    ConfigMapper(final AppConfig bootStrapConfig,
                 final Supplier<AppConfig> defaultAppConfigSupplier) {

        // This is the de-serialised form of the config.yaml so should contain all compile time defaults except
        // where set to other values in the yaml file. AppConfigModule should have ensured that all null
        // branches have been replaced with a default instance to ensure we have a full tree.
        this.bootStrapConfig = bootStrapConfig;
        this.defaultAppConfigSupplier = defaultAppConfigSupplier;
        SuperDevUtil.showSuperDevBannerIfRequired();

        LOGGER.info("Initialising application configuration");

//        LOGGER.debug(() -> LogUtil.message("Initialising ConfigMapper with class {}, id {}, propertyMap id {}",
//                appConfig.getClass().getName(),
//                System.identityHashCode(appConfig),
//                System.identityHashCode(propertyMap)));

        // We want to know the default values as defined by the compile-time initial values of
        // the instance variables in the AppConfig tree.  This is so we can make the default values available
        // to the config UI.  Therefore create our own vanilla AppConfig tree and walk it to populate
        // globalPropertiesMap with the defaults.
        LOGGER.debug("Building globalPropertiesMap from compile-time default values and annotations");
        this.allPropertyPaths = initialiseMaps();

        updateConfigFromYaml(bootStrapConfig);

        // decorateAllDbConfigProperty will be called as soon as GlobalPropertyService
        // is initialised to update the globalPropertiesMap with the DB overrides and update
        // the appConfig tree with effective values accordingly.

        if (LOGGER.isDebugEnabled()) {
            final Set<PropertyPath> onlyInGlobal = new HashSet<>(globalPropertiesMap.keySet());
            onlyInGlobal.removeAll(defaultPropertiesMap.keySet());
            onlyInGlobal.forEach(propertyPath ->
                    LOGGER.debug("Only in globalPropertiesMap - [{}]", propertyPath));

            final Set<PropertyPath> onlyInPropertyMap = new HashSet<>(defaultPropertiesMap.keySet());
            onlyInPropertyMap.removeAll(globalPropertiesMap.keySet());
            onlyInPropertyMap.forEach(propertyPath ->
                    LOGGER.debug("Only in propertyMap -         [{}]", propertyPath));
        }
    }

    private Set<PropertyPath> initialiseMaps() {

        // Init the tree containing all the hard coded default values
        final AppConfig defaultAppConfig = defaultAppConfigSupplier.get();

        // build a map of prop objects that give us access to the default AppConfig tree
        // to get default values
        addConfigObjectMethods(
                defaultAppConfig,
                AppConfig.ROOT_PROPERTY_PATH,
                defaultPropertiesMap,
                this::defaultValuePropertyConsumer,
                false);

        final Set<PropertyPath> allPropertyPaths = new HashSet<>(defaultPropertiesMap.keySet());
//        throwAwayPropertyMap.clear();

        buildObjectInfoMap(
                JsonUtil.getMapper(),
                defaultAppConfig,
                PropertyPath.fromParts("stroom"),
                objectInfoMap);

        // set/unset the yaml overrides in the glob prop map
        allPropertyPaths.forEach(propertyPath ->
                yamlOverridePropertyConsumer(propertyPath, defaultPropertiesMap.get(propertyPath)));

        return allPropertyPaths;
    }

    /**
     * Creates a config object from a map of property paths to values. If a value is not supplied for
     * a property then the compile time default will be used.
     */
    public AbstractConfig createObject(final Map<PropertyPath, Object> valueMap) {
        final List<Optional<PropertyPath>> parents = valueMap.keySet()
                .stream()
                .map(PropertyPath::getParent)
                .distinct()
                .collect(Collectors.toList());
        if (parents.size() > 1) {
            throw new RuntimeException(LogUtil.message(
                    "valueMap contains properties with different parents {}", parents));
        }
        final PropertyPath parent = parents.get(0)
                .orElseThrow();

        final ObjectInfo<? extends AbstractConfig> objectInfo = objectInfoMap.get(parent);
        final Map<String, Prop> propertyMap = objectInfo.getPropertyMap();

        return objectInfo.createInstance(propName -> {
            final PropertyPath propertyPath = parent.merge(propName);
            final Object value;
            if (valueMap.containsKey(propertyPath)) {
                value = valueMap.get(propertyPath);
            } else {
                final Prop prop = propertyMap.get(propName);
                // Get the default value
                value = prop.getValueFromConfigObject();
            }
            return value;
        });
    }

    public synchronized void updateConfigFromYaml(final AppConfig newAppConfig) {

        final int changeCount = refreshGlobalPropYamlOverrides(newAppConfig);

        if (changeCount > 0 || configInstanceMap == null) {
            if (configInstanceMap == null) {
                configInstanceMap = new HashMap<>();
            }
            rebuildObjectInstanceMap();
        }
    }

    private synchronized void rebuildObjectInstanceMap() {
        LOGGER.debug("Rebuilding object instance map");
        final Map<Class<?>, AbstractConfig> newInstanceMap = new HashMap<>();
        final AbstractConfig newConfigTree = rebuildObjectInstance(AppConfig.ROOT_PROPERTY_PATH, newInstanceMap);

        // Decorate all the config instances with their property path information, so they know what their own
        // path is.
        PropertyPathDecorator.decoratePaths(newConfigTree, AppConfig.ROOT_PROPERTY_PATH);

        // Now swap out the current map with the new one
        this.configInstanceMap = newInstanceMap;

        updateXmlSecureProcessing();

        LOGGER.debug("Completed rebuild of object instance map");
    }

    /**
     * This is a bit of nastiness is due to the fact that the {@link javax.xml.parsers.SAXParserFactory}
     * is held statically
     */
    private void updateXmlSecureProcessing() {
        final ParserConfig parserConfig = getConfigObject(ParserConfig.class);
        SAXParserSettings.setSecureProcessingEnabled(parserConfig.isSecureProcessing());
    }

    private synchronized AbstractConfig rebuildObjectInstance(
            final PropertyPath propertyPath,
            final Map<Class<?>, AbstractConfig> newInstanceMap) {

        final ObjectInfo<? extends AbstractConfig> objectInfo = objectInfoMap.get(propertyPath);
        if (objectInfo == null) {
            return null;
        }

        AbstractConfig instance = objectInfo.createInstance(argPropName -> {
            final PropertyPath childPropertyPath = propertyPath.merge(argPropName);
            final Prop childProp = objectInfo.getPropertyMap().get(argPropName);
            if (AbstractConfig.class.isAssignableFrom(childProp.getValueClass())) {
                // branch so recurse
                return rebuildObjectInstance(childPropertyPath, newInstanceMap);
            } else {
                // leaf
                return Optional.ofNullable(globalPropertiesMap.get(childPropertyPath))
                        .flatMap(ConfigProperty::getEffectiveValue)
                        .map(strVal ->
                                convertToObject(childProp, strVal, childProp.getValueType()))
                        .orElse(null);
            }
        });

        // Nasty hack to tweak the config to relax the security when we are in superdev mode
        instance = SuperDevUtil.relaxSecurityInSuperDevMode(instance);

        // We only want to hold the injectable instances
        // DB config objects are bound separately in AppConfigModule
        final Class<?> configClass = instance.getClass();
        if (!configClass.isAnnotationPresent(NotInjectableConfig.class)
            && !AbstractDbConfig.class.isAssignableFrom(configClass)) {
            newInstanceMap.put(configClass, instance);
        }

        return instance;
    }

    /**
     * Only for use in testing as this does not wait for DB props to be included
     * in the effective values
     */
    public void updateConfigInstances(final AppConfig appConfig) {
        final int changeCount = refreshGlobalPropYamlOverrides(appConfig);
        if (changeCount > 0) {
            rebuildObjectInstanceMap();
        }
    }

    synchronized int refreshGlobalPropYamlOverrides(final AppConfig yamlAppConfig) {

        final Map<PropertyPath, Optional<String>> currentEffectiveValues = globalPropertiesMap.values()
                .stream()
                .collect(Collectors.toMap(ConfigProperty::getName, ConfigProperty::getEffectiveValue));

        final Map<PropertyPath, SourceType> currentSources = globalPropertiesMap.values()
                .stream()
                .collect(Collectors.toMap(ConfigProperty::getName, ConfigProperty::getSource));

        final Map<PropertyPath, Prop> newPropertyMap = new HashMap<>();
        // walk the yamlAppConfig tree and update the globalPropertiesMap with the yaml overrides
        addConfigObjectMethods(
                yamlAppConfig,
                AppConfig.ROOT_PROPERTY_PATH,
                newPropertyMap,
                null,
                true);

        allPropertyPaths.forEach(propertyPath ->
                yamlOverridePropertyConsumer(propertyPath, newPropertyMap.get(propertyPath)));

        // We assume that the all the db overrides in the glob props are up to date at this point
        final int changeCount = haveAnyEffectiveValuesChanged(
                currentEffectiveValues, currentSources);

        if (!haveYamlOverridesBeenInitialised) {
            haveYamlOverridesBeenInitialised = true;
        }

        return changeCount;
    }

    /**
     * @return True if fullPath is a valid path to a config value
     */
    public boolean validatePropertyPath(final PropertyPath fullPath) {
        return defaultPropertiesMap.get(fullPath) != null;
    }

    Collection<ConfigProperty> getGlobalProperties() {
        return globalPropertiesMap.values();
    }

    Optional<ConfigProperty> getGlobalProperty(final PropertyPath propertyPath) {
        Objects.requireNonNull(propertyPath);
        return Optional.ofNullable(globalPropertiesMap.get(propertyPath));
    }

    public Optional<Prop> getProp(final PropertyPath propertyPath) {
        return Optional.ofNullable(defaultPropertiesMap.get(propertyPath));
    }

    /**
     * Verifies that the passed value for the property with fullPath can be converted into
     * the appropriate object type
     */
    Object validateValueSerialisation(final PropertyPath fullPath, final String valueAsString) {
        // If the string form can't be converted then an exception will be thrown
        final Prop prop = defaultPropertiesMap.get(fullPath);
        if (prop != null) {
            final Type genericType = prop.getValueType();
            return convertToObject(prop, valueAsString, genericType, NullBehaviourMode.VALIDATE);
        } else {
            throw new UnknownPropertyException(LogUtil.message("No configProperty for {}", fullPath));
        }
    }

    public Object convertValue(final PropertyPath fullPath, final String valueAsString) {
        final Prop prop = defaultPropertiesMap.get(fullPath);
        if (prop != null) {
            final Type genericType = prop.getValueType();
            return convertToObject(prop, valueAsString, genericType);
        } else {
            throw new UnknownPropertyException(LogUtil.message("No configProperty for {}", fullPath));
        }
    }

    synchronized void decorateAllDbConfigProperties(final Collection<ConfigProperty> dbConfigProperties) {

        int changeCount = dbConfigProperties.stream()
                .mapToInt(configProperty -> {
                    final Tuple2<ConfigProperty, Boolean> tuple2 = decorateDbConfigProperty(
                            configProperty,
                            defaultPropertiesMap);

                    final boolean hasChanged = tuple2._2;
                    return hasChanged
                            ? 1
                            : 0;
                })
                .sum();

        LOGGER.debug("Change count A: {}", changeCount);

        // Now ensure all propertyMap props not in the list of db props have no db override set.
        // I.e. another node could have removed the db override.
        final Map<PropertyPath, ConfigProperty> dbPropsMap = dbConfigProperties.stream()
                .collect(Collectors.toMap(ConfigProperty::getName, Function.identity()));

        changeCount += globalPropertiesMap.entrySet()
                .stream()
                .filter(entry -> entry.getValue().hasDatabaseOverride())
                .filter(entry -> !dbPropsMap.containsKey(entry.getKey()))
                .mapToInt(entry -> {
//                    try {
                    final ConfigProperty globalProp = entry.getValue();
                    final Optional<String> effectiveValueBefore = globalProp.getEffectiveValue();
                    final SourceType sourceBefore = globalProp.getSource();

                    globalProp.setDatabaseOverrideValue(OverrideValue.unSet(String.class));
                    // Not in the DB so make sure it has no ID, e.g. if the prop is in the db on boot
                    // then get changed back to default val (which removes it from the db) we would hold
                    // an old ID for it, which would break future updates
                    globalProp.setId(null);

                    final boolean hasChanged = hasEffectiveValueChanged(
                            globalProp.getName(), effectiveValueBefore, sourceBefore);

                    return hasChanged
                            ? 1
                            : 0;
                })
                .sum();

        LOGGER.debug("Change count B: {}", changeCount);

        // This may be the first call after booting the app so ensure the yaml is set up too
        if (!haveYamlOverridesBeenInitialised) {
            changeCount += refreshGlobalPropYamlOverrides(bootStrapConfig);
        }

        LOGGER.debug("Change count C: {}", changeCount);

        if (changeCount > 0) {
            rebuildObjectInstanceMap();
        }

        if (!haveDbOverridesBeenInitialised) {
            haveDbOverridesBeenInitialised = true;
        }
    }

    ConfigProperty decorateDbConfigProperty(final ConfigProperty dbConfigProperty) {
        final Tuple2<ConfigProperty, Boolean> tuple2 = decorateDbConfigProperty(
                dbConfigProperty,
                defaultPropertiesMap);

        final boolean hasChanged = tuple2._2;
        if (hasChanged) {
            rebuildObjectInstanceMap();
        }
        return tuple2._1;
    }

    /**
     * @param dbConfigProperty The config property object obtained from the database
     * @return The updated typed value from the object model
     */
    Tuple2<ConfigProperty, Boolean> decorateDbConfigProperty(final ConfigProperty dbConfigProperty,
                                                             final Map<PropertyPath, Prop> propertyMap) {
        Objects.requireNonNull(dbConfigProperty);

        LOGGER.debug("decorateDbConfigProperty() called for {}", dbConfigProperty.getName());

        final PropertyPath fullPath = dbConfigProperty.getName();

        synchronized (this) {
            final ConfigProperty globalConfigProperty = getGlobalProperty(fullPath)
                    .orElseThrow(() ->
                            new UnknownPropertyException(LogUtil.message("No configProperty for {}", fullPath)));

            final Prop prop = propertyMap.get(fullPath);
            if (prop != null) {

                final Optional<String> effectiveValueBefore = globalConfigProperty.getEffectiveValue();
                final SourceType sourceBefore = globalConfigProperty.getSource();

                // Update all the DB related values from the passed DB config prop
                globalConfigProperty.setId(dbConfigProperty.getId());
                globalConfigProperty.setDatabaseOverrideValue(dbConfigProperty.getDatabaseOverrideValue());
                globalConfigProperty.setVersion(dbConfigProperty.getVersion());
                globalConfigProperty.setCreateTimeMs(dbConfigProperty.getCreateTimeMs());
                globalConfigProperty.setCreateUser(dbConfigProperty.getCreateUser());
                globalConfigProperty.setUpdateTimeMs(dbConfigProperty.getUpdateTimeMs());
                globalConfigProperty.setUpdateUser(dbConfigProperty.getUpdateUser());

                final boolean hasChanged = hasEffectiveValueChanged(
                        fullPath, effectiveValueBefore, sourceBefore);

                return Tuple.of(globalConfigProperty, hasChanged);
            } else {
                throw new UnknownPropertyException(LogUtil.message("No prop object for {}", fullPath));
            }
        }
    }

    private void addConfigObjectMethods(final AbstractConfig config,
                                        final PropertyPath path,
                                        final Map<PropertyPath, Prop> propertyMap,
                                        final PropertyConsumer propConsumer,
                                        final boolean populateNullBranches) {
        LOGGER.trace("addConfigObjectMethods({}, {}, .....)", config, path);

        config.setBasePath(path);

        final Map<String, Prop> properties = PropertyUtil.getProperties(config);
        properties.forEach((k, prop) -> {
            LOGGER.trace("prop: {}", prop);

            // The prop may have a JsonPropery annotation that defines its name
            final String specifiedName = getNameFromAnnotation(prop);
            final String name = Strings.isNullOrEmpty(specifiedName)
                    ? prop.getName()
                    : specifiedName;

            final PropertyPath fullPath = path.merge(name);

            final Class<?> valueType = prop.getValueClass();

            final Object value = prop.getValueFromConfigObject();

            if (isSupportedPropertyType(valueType)) {

                // This is a leaf, i.e. a property so add it to our map
                propertyMap.put(fullPath, prop);

                if (propConsumer != null) {
                    // Now let the consumer do something to it
                    propConsumer.accept(fullPath, prop);
                }
            } else if (AbstractConfig.class.isAssignableFrom(valueType)) {
                // This must be a branch, i.e. config object so recurse into that
                if (value != null || populateNullBranches) {

                    final AbstractConfig childConfigObject;
                    if (value == null) {
                        try {
                            // if you have yaml like
                            //   pipeline:
                            //     referenceData:
                            // then referenceData will be null so we need to make one from the no args ctor
                            // to ensure we have a full tree
                            LOGGER.debug(() -> LogUtil.message("Constructing new default instance of {} on {}",
                                    valueType.getSimpleName(), config.getClass().getSimpleName()));
                            childConfigObject = (AbstractConfig) valueType.getConstructor().newInstance();
                            prop.setValueOnConfigObject(childConfigObject);
                        } catch (final Exception e) {
                            throw new RuntimeException("Error constructing new instance of " + valueType, e);
                        }
                    } else {
                        childConfigObject = (AbstractConfig) value;
                    }

                    // Recurse into the child
                    addConfigObjectMethods(
                            childConfigObject,
                            fullPath,
                            propertyMap,
                            propConsumer,
                            populateNullBranches);
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

    private int haveAnyEffectiveValuesChanged(final Map<PropertyPath, Optional<String>> currentEffectiveValues,
                                              final Map<PropertyPath, SourceType> currentSources) {

        final int changeCount = allPropertyPaths.stream()
                .mapToInt(propertyPath -> {

                    final Optional<String> effectiveValueBefore = currentEffectiveValues
                            .getOrDefault(propertyPath, Optional.empty());
                    final SourceType sourceBefore = currentSources.get(propertyPath);

                    final boolean wasChanged = hasEffectiveValueChanged(
                            propertyPath,
                            effectiveValueBefore,
                            sourceBefore);

                    return wasChanged
                            ? 1
                            : 0;
                })
                .sum();

        return changeCount;
    }

    private boolean hasEffectiveValueChanged(final PropertyPath fullPath,
                                             final Optional<String> effectiveValueBefore,
                                             final SourceType sourceBefore) {

        final ConfigProperty configProperty = globalPropertiesMap.get(fullPath);
        final Optional<String> effectiveValueAfter = configProperty.getEffectiveValue();
        final SourceType sourceAfter = configProperty.getSource();

        LOGGER.trace(() -> LogUtil.message("effectiveValueBefore: [{}] ({}), effectiveValueAfter [{}] ({})",
                effectiveValueBefore,
                sourceBefore,
                effectiveValueAfter,
                sourceAfter));

        if (!Objects.equals(effectiveValueBefore, effectiveValueAfter)) {

            // Stop it logging 'changes' when we boot up and there are differences from the default
            // which there likely always will be
            if (haveYamlOverridesBeenInitialised && haveDbOverridesBeenInitialised) {
                LOGGER.info(
                        "Effective value of {} has changed on this node from [{}] ({}) to [{}] ({})",
                        fullPath,
                        effectiveValueBefore.orElse(""),
                        sourceBefore,
                        effectiveValueAfter.orElse(""),
                        sourceAfter);
            }
//            }
            return true;
        } else {
            LOGGER.trace("Values are equal for {}", fullPath);
            return false;
        }
    }

    private void yamlOverridePropertyConsumer(final PropertyPath fullPath,
                                              final Prop yamlProp) {
        // We have already walked a vanilla AppConfig object tree so all compile time
        // props should be in here with a default value (and a value that matches it)
        final ConfigProperty configProperty = globalPropertiesMap.get(fullPath);

        Preconditions.checkNotNull(
                configProperty,
                "Property %s with path %s exists in the " +
                "YAML but not in the object model, this should not happen",
                yamlProp,
                fullPath);

        final Object newValue = yamlProp.getValueFromConfigObject();
        final Object defaultValue = defaultPropertiesMap.get(fullPath).getValueFromConfigObject();

        // Update yaml override in global property.
        if (Objects.equals(defaultValue, newValue)) {
            configProperty.setYamlOverrideValue(OverrideValue.unSet(String.class));
        } else {
            final String yamlValueAsStr = getStringValue(yamlProp);
            configProperty.setYamlOverrideValue(yamlValueAsStr);
        }
    }

    private void defaultValuePropertyConsumer(final PropertyPath fullPath,
                                              final Prop defaultProp) {

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
        final boolean isSupported = type.equals(String.class) ||
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
                                    Set.class.isAssignableFrom(type) ||
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

    private void updatePropertyFromConfigAnnotations(final ConfigProperty configProperty,
                                                     final Prop prop) {
        // Editable by default unless found otherwise below
        configProperty.setEditable(true);

        prop.getAnnotation(JsonPropertyDescription.class)
                .ifPresent(jsonPropertyDescription ->
                        configProperty.setDescription(jsonPropertyDescription.value()));

        if (prop.hasAnnotation(ReadOnly.class)) {
            configProperty.setEditable(false);
        }

        if (prop.hasAnnotation(Password.class)) {
            configProperty.setPassword(true);
        }

        prop.getAnnotation(RequiresRestart.class)
                .ifPresent(requiresRestart -> {
                    final RequiresRestart.RestartScope scope = requiresRestart.value();
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
                });

        configProperty.setDataTypeName(getDataTypeName(prop.getValueType()));
    }

    private static String getDataTypeName(final Type type) {
        try {
            if (type instanceof Class) {
                final Class<?> valueClass = (Class<?>) type;
                final String dataTypeName;

                if (valueClass.equals(int.class)) {
                    dataTypeName = "Integer";
                } else if (valueClass.equals(Enum.class)) {
                    dataTypeName = "Enumeration";
                } else if (valueClass.equals(List.class) || valueClass.equals(Map.class)) {
                    dataTypeName = CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL,
                            valueClass.getSimpleName()) + " of ";
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
        } catch (final Exception e) {
            throw new RuntimeException(LogUtil.message(
                    "Error getting type name for {}: {}", type, e.getMessage()));
        }
    }

    private String getNameFromAnnotation(final Prop prop) {
        return prop.getAnnotation(JsonProperty.class)
                .map(JsonProperty::value)
                .orElse(null);
    }

    // pkg private for testing
    static String convertToString(final Object value) {
        final List<String> availableDelimiters = new ArrayList<>(VALID_DELIMITERS_LIST);
        return convertToString(value, availableDelimiters);
    }

    static Function<Object, String> createDelimitedConversionFunc(
            final BiFunction<Object, List<String>, String> conversionFunc) {

        final List<String> availableDelimiters = new ArrayList<>(VALID_DELIMITERS_LIST);
        return object ->
                conversionFunc.apply(object, availableDelimiters);
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
                } else if (value instanceof Set) {
                    return setToString((Set<?>) value, availableDelimiters);
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

    static Object convertToObject(
            final Prop prop,
            final String value,
            final Type genericType) {
        return convertToObject(prop, value, genericType, NullBehaviourMode.DEFAULT);
    }

    // pkg private for testing
    static Object convertToObject(
            final Prop prop,
            final String value,
            final Type genericType,
            final NullBehaviourMode nullBehaviourMode) {
        final Class<?> type = PropertyUtil.getDataType(genericType);

        try {
            if (value == null || value.isBlank()) {
                if (type.isPrimitive()) {
                    if (NullBehaviourMode.DEFAULT.equals(nullBehaviourMode)) {
                        return getDefaultValue(type);
                    } else {
                        throw new RuntimeException(LogUtil.message(
                                "A null or empty value is not a valid value for this property which has " +
                                "a primitive type of {}", type.getName()));
                    }
                } else {
                    return null;
                }
            }

            // If we reach here the value is non-null and non-blank
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
                return stringToList(prop, value, itemType);
            } else if (Set.class.isAssignableFrom(type)) {
                // determine the type of the list items
                final Class<?> itemType = getGenericsParam(genericType, 0);
                return stringToSet(prop, value, itemType);
            } else if (Map.class.isAssignableFrom(type)) {
                // determine the types of the keys and values
                final Class<?> keyType = getGenericsParam(genericType, 0);
                final Class<?> valueType = getGenericsParam(genericType, 1);
                return stringToMap(prop, value, keyType, valueType);
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
        } catch (final Exception e) {
            // Don't include the original exception else gwt uses the msg of the
            // original which is not very user friendly. Enable debug to see the stack
            final String propName = (prop.getParentObject() == null
                    ? "null"
                    : prop.getParentObject().getClass().getSimpleName())
                                    + "."
                                    + prop.getName();

            LOGGER.debug(() -> LogUtil.message(
                    "Unable to convert value [{}] of property [{}] to type [{}] due to: {}",
                    value, propName, getDataTypeName(genericType), e.getMessage()), e);
            throw new ConfigPropertyValidationException(LogUtil.message(
                    "Unable to convert value [{}] of property [{}] to type [{}] due to: {}",
                    value, propName, getDataTypeName(genericType), e.getMessage()), e);
        }

        LOGGER.error("Unable to convert value [{}] of type [{}] to an Object", value, type);
        throw new ConfigPropertyValidationException(LogUtil.message(
                "Type [{}] is not supported for value [{}]", genericType, value));
    }

    private static Object getDefaultValue(final Class<?> clazz) {
        if (clazz.equals(boolean.class)) {
            return DEFAULT_BOOLEAN;
        } else if (clazz.equals(byte.class)) {
            return DEFAULT_BYTE;
        } else if (clazz.equals(short.class)) {
            return DEFAULT_SHORT;
        } else if (clazz.equals(int.class)) {
            return DEFAULT_INT;
        } else if (clazz.equals(long.class)) {
            return DEFAULT_LONG;
        } else if (clazz.equals(float.class)) {
            return DEFAULT_FLOAT;
        } else if (clazz.equals(double.class)) {
            return DEFAULT_DOUBLE;
        } else {
            throw new IllegalArgumentException(
                    "Class type " + clazz + " not supported");
        }
    }

    private static Class<?> getGenericsParam(final Type typeWithGenerics, final int index) {
        final List<Type> genericsParamTypes = PropertyUtil.getGenericTypes(typeWithGenerics);
        if (genericsParamTypes.isEmpty()) {
            throw new RuntimeException(LogUtil.message(
                    "Unable to get generics parameter {} for type {} as it has no parameterised types",
                    index, typeWithGenerics));
        }
        if (index >= genericsParamTypes.size()) {
            throw new IllegalArgumentException(LogUtil.message("Index {} is out of bounds for types {}",
                    index, genericsParamTypes));
        }

        return PropertyUtil.getDataType(genericsParamTypes.get(index));
    }

    private static Boolean parseBoolean(final String str) {
        if (str.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        } else if (str.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        } else {
            throw new ConfigPropertyValidationException(
                    LogUtil.message(
                            "Cannot convert [{}] into a boolean. Valid values are [true|false] ignoring case.",
                            str));
        }
    }


    private static String listToString(final List<?> list,
                                       final List<String> availableDelimiters) {

        if (list.isEmpty()) {
            return "";
        }
        final List<String> strList = list.stream()
                .map(ConfigMapper::convertToString)
                .collect(Collectors.toList());

        final String allText = String.join("", strList);

        final String delimiter = getDelimiter(allText, availableDelimiters);

        // prefix the delimited form with the delimiter so when we deserialise
        // we know what the delimiter is
        return delimiter + String.join(delimiter, strList);
    }

    private static String setToString(final Set<?> set,
                                      final List<String> availableDelimiters) {

        if (set.isEmpty()) {
            return "";
        }
        final List<String> strList = set.stream()
                .sorted() // ensure consistent serialisation
                .map(ConfigMapper::convertToString)
                .collect(Collectors.toList());

        final String allText = String.join("", strList);

        final String delimiter = getDelimiter(allText, availableDelimiters);

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
                    final String key = ConfigMapper.convertToString(entry.getKey());
                    final String value = ConfigMapper.convertToString(entry.getValue());
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
        final String allText = String.join(
                "", docRef.getType(), docRef.getUuid(), docRef.getName());
        final String delimiter = getDelimiter(allText, availableDelimiters);

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

    private static <T> Set<T> stringToSet(
            final Prop prop,
            final String serialisedForm,
            final Class<T> type) {
        if (serialisedForm == null || serialisedForm.isEmpty()) {
            return Collections.emptySet();
        } else {
            return new HashSet<>(stringToList(prop, serialisedForm, type));
        }
    }

    private static <T> List<T> stringToList(
            final Prop prop,
            final String serialisedForm,
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

                final String delimitedValue = serialisedForm.substring(1);

                return StreamSupport.stream(
                                Splitter
                                        .on(delimiter)
                                        .split(delimitedValue)
                                        .spliterator(), false)
                        .map(str -> convertToObject(prop, str, type))
                        .map(type::cast)
                        .collect(Collectors.toList());
            } catch (final Exception e) {
                throw new RuntimeException(LogUtil.message(
                        "Error de-serialising a List<?> from [{}]", serialisedForm), e);
            }
        }
    }

    private static <K, V> Map<K, V> stringToMap(
            final Prop prop,
            final String serialisedForm,
            final Class<K> keyType,
            final Class<V> valueType) {

        if (serialisedForm == null || serialisedForm.isEmpty()) {
            return Collections.emptyMap();
        } else {

            final String entryDelimiter = String.valueOf(serialisedForm.charAt(0));
            validateDelimiter(serialisedForm, 0, "first", MAP_EXAMPLE);

            final String keyValueDelimiter = String.valueOf(serialisedForm.charAt(1));
            validateDelimiter(serialisedForm, 1, "second", MAP_EXAMPLE);

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
                        final String valueStr = parts.size() == 1
                                ? null
                                : parts.get(1);

                        final K key = keyType.cast(convertToObject(prop, keyStr, keyType));
                        final V value = valueStr != null
                                ? valueType.cast(convertToObject(prop, valueStr, valueType))
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

            return DocRef.builder()
                    .type(parts.get(0))
                    .uuid(parts.get(1))
                    .name(parts.get(2))
                    .build();
        } catch (final Exception e) {
            throw new RuntimeException(LogUtil.message(
                    "Error de-serialising a docRef from [{}] due to: {}", serialisedForm, e.getMessage()), e);
        }
    }

    private static Enum<?> stringToEnum(final String serialisedForm, final Class<?> type) {
        return Enum.valueOf((Class<Enum>) type, serialisedForm.toUpperCase());
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

    public <T extends AbstractConfig> T getConfigObject(final Class<T> clazz) {
        final AbstractConfig config = configInstanceMap.get(clazz);
        Objects.requireNonNull(
                config,
                "No config instance found for class " + clazz.getName());
        try {
            return clazz.cast(config);
        } catch (final Exception e) {
            throw new RuntimeException(LogUtil.message(
                    "Error casting config object to {}, found {}",
                    clazz.getName(),
                    config.getClass().getName()), e);
        }
    }

    /**
     * @return All the classes that can be obtained from ConfigMapper
     */
    public List<Class<? extends AbstractConfig>> getInjectableConfigClasses() {

        final Map<PropertyPath, ObjectInfo<? extends AbstractConfig>> objectInfoMap;
        if (this.objectInfoMap.isEmpty()) {
            objectInfoMap = new HashMap<>();

            buildObjectInfoMap(
                    JsonUtil.getMapper(),
                    new AppConfig(),
                    PropertyPath.fromParts("stroom"),
                    objectInfoMap);
        } else {
            objectInfoMap = this.objectInfoMap;
        }

        return objectInfoMap.values()
                .stream()
                .map(ObjectInfo::getObjectClass)
                .filter(clazz ->
                        !clazz.isAnnotationPresent(NotInjectableConfig.class))
                .filter(clazz ->
                        !clazz.isAnnotationPresent(BootStrapConfig.class))
                .collect(Collectors.toList());
    }

    private static void buildObjectInfoMap(
            final ObjectMapper objectMapper,
            final AbstractConfig config,
            final PropertyPath path,
            final Map<PropertyPath, ObjectInfo<? extends AbstractConfig>> objectInfoMap) {

        LOGGER.trace("addConfigObjectMethods({}, {}, .....)", config, path);

        config.setBasePath(path);

        final ObjectInfo<AbstractConfig> objectInfo = PropertyUtil.getObjectInfo(
                objectMapper,
                path.getPropertyName(),
                config);

        if (objectInfo.getConstructor() == null) {
            throw new RuntimeException("No JsonCreator constructor for " + config.getClass().getName());
        }

        objectInfoMap.put(path, objectInfo);

        objectInfo.getPropertyMap()
                .forEach((k, prop) -> {
                    final PropertyPath fullPath = path.merge(prop.getName());

                    final Class<?> valueType = prop.getValueClass();

                    LOGGER.trace(() -> LogUtil.message("prop: {}, class: {}", prop, prop.getValueClass()));

                    if (AbstractConfig.class.isAssignableFrom(valueType)) {
                        final AbstractConfig childConfigObject = (AbstractConfig) prop.getValueFromConfigObject();
                        // This must be a branch, i.e. config object so recurse into that
                        if (childConfigObject != null) {
                            // Recurse into the child
                            buildObjectInfoMap(
                                    objectMapper,
                                    childConfigObject,
                                    fullPath,
                                    objectInfoMap);
                        }
//                    } else {
//                        // This is not expected
//                        throw new RuntimeException(LogUtil.message(
//                                "Unexpected bean property of type [{}], expecting an instance of {}, " +
//                                        "or a supported type.",
//                                valueType.getName(),
//                                AbstractConfig.class.getSimpleName()));
                    }
                });
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

    @FunctionalInterface
    private interface PropertyConsumer {

        void accept(final PropertyPath fullPath, final Prop yamlProp);
    }

    enum NullBehaviourMode {
        // throw if the value cannot be parsed, e.g. a null into an int
        VALIDATE,
        // default the value if null, eg. a null int becomes 0
        DEFAULT
    }
}
