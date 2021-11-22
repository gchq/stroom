package stroom.config;

import stroom.config.app.AppConfig;
import stroom.config.app.AppConfigModule;
import stroom.config.app.Config;
import stroom.config.app.ConfigHolder;
import stroom.config.app.YamlUtil;
import stroom.config.common.CommonDbConfig;
import stroom.config.common.DbConfig;
import stroom.config.common.HasDbConfig;
import stroom.event.logging.impl.LoggingConfig;
import stroom.legacy.db.LegacyDbConfig;
import stroom.lifecycle.impl.LifecycleConfig;
import stroom.util.config.PropertyUtil;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LogUtil;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.NotInjectableConfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.reflect.ClassPath;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class TestAppConfigModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestAppConfigModule.class);
    private static final String MODIFIED_JDBC_DRIVER = "modified.jdbc.driver";
    private static final String STROOM_PACKAGE_PREFIX = "stroom.";
    private static final Predicate<String> STROOM_PACKAGE_NAME_FILTER = name ->
            name.startsWith(STROOM_PACKAGE_PREFIX) && !name.contains("shaded");
    private static final Predicate<Class<?>> CONFIG_CLASS_FILTER = clazz -> {

        return clazz.getSimpleName().endsWith("Config")
                && !clazz.equals(AbstractConfig.class)
                && !clazz.equals(AppConfig.class);
    };

    @AfterEach
    void afterEach() {
//        FileUtil.deleteContents(tmpDir);
    }

//    @Test
//    void testCommonDbConfigDeser() throws IOException {
//        final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
//
//        final String yaml = """
//                connection:
//                  jdbcDriverUrl: myURL
//                  jdbcDriverUsername: myUser
//                  jdbcDriverPassword: myPword""";
//
//        final CommonDbConfig commonDbConfig = objectMapper.readValue(yaml, CommonDbConfig.class);
//
//        Assertions.assertThat(commonDbConfig.getConnectionConfig().getClassName())
//                .isNotNull();
//    }
//
//    @Test
//    void testNullJdbcDriverClass() throws IOException {
//        Path devYamlPath = getDevYamlPath();
//
//        LOGGER.debug("dev yaml path: {}", devYamlPath.toAbsolutePath());
//
//        // Modify the value on the common connection pool so it gets applied to all other config objects
//        final Config config = YamlUtil.readConfig(devYamlPath);
//
//        Assertions.assertThat(config.getAppConfig()
//                .getCommonDbConfig()
//                .getConnectionConfig()
//                .getClassName())
//                .isNotNull();
//    }

    @Test
    void testCommonDbConfig() throws IOException {
        Path devYamlPath = getDevYamlPath();

        LOGGER.debug("dev yaml path: {}", devYamlPath.toAbsolutePath());

        // Modify the value on the common connection pool so it gets applied to all other config objects
        final Config modifiedConfig = YamlUtil.readConfig(devYamlPath);
//        modifiedConfig.getAppConfig().getCommonDbConfig().getConnectionPoolConfig().setPrepStmtCacheSize(250);
        final int currentValue = modifiedConfig.getAppConfig()
                .getCommonDbConfig()
                .getConnectionPoolConfig()
                .getPrepStmtCacheSize();
        final int newCacheValue = currentValue + 1000;

        modifiedConfig.getAppConfig()
                .getCommonDbConfig()
                .getConnectionPoolConfig()
                .setPrepStmtCacheSize(newCacheValue);

        final String newUser = modifiedConfig.getAppConfig()
                .getCommonDbConfig()
                .getConnectionConfig()
                .getUser() + "XXX";

        modifiedConfig.getAppConfig()
                .getCommonDbConfig()
                .getConnectionConfig()
                .setUser(newUser);

        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                install(new AppConfigModule(new ConfigHolder() {
                    @Override
                    public AppConfig getAppConfig() {
                        return modifiedConfig.getAppConfig();
                    }

                    @Override
                    public Path getConfigFile() {
                        return devYamlPath;
                    }
                }));
            }
        });

        AppConfig appConfig = injector.getInstance(AppConfig.class);
        CommonDbConfig commonDbConfig = injector.getInstance(CommonDbConfig.class);

//        Assertions.assertThat(commonDbConfig.getConnectionConfig().getClassName())
//                .isNotNull();

        Assertions.assertThat(commonDbConfig.getConnectionPoolConfig().getPrepStmtCacheSize())
                .isEqualTo(newCacheValue);
        Assertions.assertThat(commonDbConfig.getConnectionConfig().getUser())
                .isEqualTo(newUser);

        // Make sure all the getters that return a HasDbConfig have the modified conn value
        final Stream<HasDbConfig> hasDbConfigsStream = Arrays.stream(appConfig.getClass().getDeclaredMethods())
                .filter(method -> method.getName().startsWith("get"))
                .filter(method -> HasDbConfig.class.isAssignableFrom(method.getReturnType()))
                .map(method -> {
                    try {
                        return (HasDbConfig) method.invoke(appConfig);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                });

        Stream.concat(
                Stream.of(new LegacyDbConfig()), // This is not in the tree but we want to test it
                hasDbConfigsStream)
                .forEach(hasDbConfig -> {
                    LOGGER.info("Testing class: {}", hasDbConfig.getClass().getName());

                    final DbConfig mergedConfig = commonDbConfig.mergeConfig(hasDbConfig.getDbConfig());

                    // mergedConfig won't be the same as commonDbConfig as the driver class
                    // is not set in the yaml so is reliant on the default value. The default value
                    // is ignored in the jackson deserialisation so will be null in commonDbConfig
                    // but the merge factors it in so mergedConfig has it set.

                    Assertions.assertThat(mergedConfig.getConnectionConfig().getClassName())
                            .isNotNull();
                    Assertions.assertThat(mergedConfig.getConnectionConfig().getUser())
                            .isNotNull();
                    Assertions.assertThat(mergedConfig.getConnectionConfig().getPassword())
                            .isNotNull();
                    Assertions.assertThat(mergedConfig.getConnectionConfig().getUrl())
                            .isNotNull();

                    Assertions.assertThat(mergedConfig.getConnectionConfig().getUser())
                            .isEqualTo(commonDbConfig.getConnectionConfig().getUser());
                    Assertions.assertThat(mergedConfig.getConnectionConfig().getPassword())
                            .isEqualTo(commonDbConfig.getConnectionConfig().getPassword());
                    Assertions.assertThat(mergedConfig.getConnectionConfig().getUrl())
                            .isEqualTo(commonDbConfig.getConnectionConfig().getUrl());

                    Assertions.assertThat(mergedConfig.getConnectionPoolConfig())
                            .as(LogUtil.message("ConnectionPoolConfig doesn't match for class {}",
                                    hasDbConfig.getClass().getSimpleName()))
                            .isEqualTo(commonDbConfig.getConnectionPoolConfig());

                    Assertions.assertThat(mergedConfig.getConnectionPoolConfig().getPrepStmtCacheSize())
                            .isEqualTo(newCacheValue);
                });
    }

    /**
     * IMPORTANT: This test must be run from stroom-app so it can see all the other modules
     */
    @Test
    void testAbstractConfigPresence() throws IOException {

        AppConfig appConfig = new AppConfig();
        AppConfigModule appConfigModule = new AppConfigModule(new ConfigHolder() {
            @Override
            public AppConfig getAppConfig() {
                return appConfig;
            }

            @Override
            public Path getConfigFile() {
                return Paths.get("NOT USED");
            }
        });
        final Injector injector = Guice.createInjector(appConfigModule);
//        Injector injector = Guice.createInjector(new AbstractModule() {
//            @Override
//            protected void configure() {
//                install(new AppConfigModule(new ConfigHolder() {
//                    @Override
//                    public AppConfig getAppConfig() {
//                        return new AppConfig();
//                    }
//
//                    @Override
//                    public Path getConfigFile() {
//                        return Paths.get("NOT USED");
//                    }
//                }));
//            }
//        });


        LOGGER.info("Finding all AbstractConfig classes");

        // Find all classes that extend AbstractConfig
        final ClassLoader classLoader = getClass().getClassLoader();
        final Set<Class<?>> abstractConfigConcreteClasses = getAbstractConfigConcreteClasses(classLoader);

        LOGGER.info("Finding all classes in object tree");

        Map<Class<?>, Integer> appConfigTreeClassToIdMap = new HashMap<>();

        // Find all stroom. classes in the AppConfig tree, i.e. config POJOs
        final Set<Class<?>> appConfigTreeClasses = new HashSet<>();
        PropertyUtil.walkObjectTree(
                appConfig,
                prop -> STROOM_PACKAGE_NAME_FILTER.test(prop.getValueClass().getPackageName()),
                prop -> {
                    // make sure we have a getter and a setter
                    Assertions.assertThat(prop.getGetter())
                            .as(LogUtil.message("{} {}",
                                    prop.getParentObject().getClass().getSimpleName(), prop.getGetter().getName()))
                            .isNotNull();

                    Assertions.assertThat(prop.getSetter())
                            .as(LogUtil.message("{} {}",
                                    prop.getParentObject().getClass().getSimpleName(), prop.getSetter().getName()))
                            .isNotNull();

                    Class<?> valueClass = prop.getValueClass();
                    if (CONFIG_CLASS_FILTER.test(valueClass)) {
                        appConfigTreeClasses.add(prop.getValueClass());
                        AbstractConfig propValue = (AbstractConfig) prop.getValueFromConfigObject();
                        // Keep a record of the instance ID of the instance in the tree
                        appConfigTreeClassToIdMap.put(valueClass, System.identityHashCode(propValue));
                    }
                });

        Map<Class<?>, Integer> injectedInstanceIdMap = abstractConfigConcreteClasses.stream()
                .collect(Collectors.toMap(
                        clazz -> clazz,
                        clazz -> {
                            Object object = injector.getInstance(clazz);
                            return System.identityHashCode(object);
                        }));

        // Make sure all config classes extend AbstractConfig and all AbstractConfig classes are in
        // the AppConfig tree. If there is a mismatch then it may be due to the getter/setter not
        // being public in the config class, else the config class may not be a property in the
        // AppConfig object tree
        Assertions.assertThat(abstractConfigConcreteClasses)
                .containsAll(appConfigTreeClasses);

        Assertions.assertThat(appConfigTreeClasses)
                .containsAll(abstractConfigConcreteClasses);

        // Now we know the appConfig tree contains all the concrete AbstractConfig classes
        // check that guice will give us the right instance. This ensures

        List<Class<?>> classesWithMultipleInstances = appConfigTreeClassToIdMap.entrySet()
                .stream()
                .filter(entry -> {
                    Integer appConfigTreeInstanceId = entry.getValue();
                    Integer injectedInstanceId = injectedInstanceIdMap.get(entry.getKey());

                    // Some AbstractConfig classes are shared so can't be injected themselves
                    // so filter them out
                    boolean isInjectableClass = entry.getKey().getAnnotation(NotInjectableConfig.class) == null;

                    return !injectedInstanceId.equals(appConfigTreeInstanceId) && isInjectableClass;
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (!classesWithMultipleInstances.isEmpty()) {
            LOGGER.error("The following AbstractConfig classes have a different injected instance to the " +
                    "instance in the AppConfig tree.\n" +
                    "You need to add Guice bindings for them in AppConfigModule");
            classesWithMultipleInstances.stream()
                    .sorted(Comparator.comparing(Class::getName))
                    .forEach(clazz -> {
                        AbstractConfig config = (AbstractConfig) injector.getInstance(clazz);
                        LOGGER.info("  {}", clazz.getName());
                    });
        }

        Assertions.assertThat(classesWithMultipleInstances).isEmpty();
    }

    @Test
    void findPrimitiveBooleanSetters() throws IOException {
        final ClassLoader classLoader = getClass().getClassLoader();
        final Set<Class<?>> abstractConfigConcreteClasses = getAbstractConfigConcreteClasses(classLoader);
        final List<Tuple2<? extends Class<?>, Method>> settersWithLittleBoolean = abstractConfigConcreteClasses.stream()
                .flatMap(clazz ->
                        Arrays.stream(clazz.getMethods())
                                .filter(method -> method.getName().startsWith("set"))
                                .filter(method -> Arrays.asList(method.getParameterTypes()).contains(boolean.class))
                                .map(method -> Tuple.of(clazz, method)))
                .peek(classAndMethod ->
                        LOGGER.info("class: {}, method: {}",
                                classAndMethod._1,
                                classAndMethod._2.getName()))
                .collect(Collectors.toList());
        // Config classes with (b|B)oolean values should have:
        //   a Boolean variable initialised to a default
        //   a getter that returns a boolean or the default if null
        //   a setter that accepts a Boolean and stores the default if null
        Assertions.assertThat(settersWithLittleBoolean)
                .isEmpty();
    }

    private Set<Class<?>> getAbstractConfigConcreteClasses(final ClassLoader classLoader) throws IOException {

        final Set<Class<?>> abstractConfigConcreteClasses = ClassPath.from(classLoader)
                .getAllClasses()
                .stream()
                .filter(classInfo -> STROOM_PACKAGE_NAME_FILTER.test(classInfo.getPackageName()))
                .map(ClassPath.ClassInfo::load)
                .filter(CONFIG_CLASS_FILTER)
                .filter(AbstractConfig.class::isAssignableFrom)
                .filter(clazz -> !IsProxyConfig.class.isAssignableFrom(clazz)) // ignore proxy classes
                .filter(clazz -> {
                    boolean isAbstract = Modifier.isAbstract(clazz.getModifiers());
                    if (isAbstract) {
                        LOGGER.info("Ignoring abstract class {}", clazz.getName());
                    }
                    return !isAbstract;
                }) // Ignore abstract classes, e.g. UriConfig
                .peek(clazz -> {
                    LOGGER.debug(clazz.getSimpleName());
                })
                .collect(Collectors.toSet());
        return abstractConfigConcreteClasses;
    }

    /**
     * Verify behaviour of Boolean (de)ser with a default value
     */
    @Test
    void testBooleanSerDeSer() throws IOException {
        final ObjectMapper mapper = JsonUtil.getMapper();

        final LifecycleConfig lifecycleConfigEnabled = new LifecycleConfig();
        lifecycleConfigEnabled.setEnabled(true);
        Assertions.assertThat(lifecycleConfigEnabled.isEnabled())
                .isTrue();

        final LifecycleConfig lifecycleConfigDisabled = new LifecycleConfig();
        lifecycleConfigDisabled.setEnabled(false);
        Assertions.assertThat(lifecycleConfigDisabled.isEnabled())
                .isFalse();

        final LifecycleConfig lifecycleConfigNull = new LifecycleConfig();
        lifecycleConfigNull.setEnabled(null);
        Assertions.assertThat(lifecycleConfigNull.isEnabled())
                .isEqualTo((new LifecycleConfig()).isEnabled());

        String json;
        json = mapper.writeValueAsString(lifecycleConfigDisabled);

        Assertions.assertThat(json)
                .contains("false");

        Assertions.assertThat(mapper.readValue(json, LifecycleConfig.class))
                .isEqualTo(lifecycleConfigDisabled);


        json = mapper.writeValueAsString(lifecycleConfigEnabled);

        Assertions.assertThat(json)
                .contains("true");

        Assertions.assertThat(mapper.readValue(json, LifecycleConfig.class))
                .isEqualTo(lifecycleConfigEnabled);
    }

    /**
     * Verify behaviour of Boolean (de)ser with a default value
     */
    @Test
    void testDefaultValueSerDeSer() throws IOException {
        final ObjectMapper mapper = YamlUtil.getMapper();

        // Use LoggingConfig as it has a true and a false default and a child object

        final LoggingConfig loggingConfigVanilla = new LoggingConfig();

        Assertions.assertThat(loggingConfigVanilla.isOmitRecordDetailsLoggingEnabled())
                .isEqualTo(LoggingConfig.OMIT_RECORD_DETAILS_LOGGING_ENABLED_DEFAULT);
        Assertions.assertThat(loggingConfigVanilla.isLogEveryRestCallEnabled())
                .isEqualTo(LoggingConfig.LOG_EVERY_REST_CALL_ENABLED_DEFAULT);
        Assertions.assertThat(loggingConfigVanilla.getMaxListElements())
                .isEqualTo(LoggingConfig.MAX_LIST_ELEMENTS_DEFAULT);

        doLoggingConfigTest(mapper, false, false, false);
        doLoggingConfigTest(mapper, true, true, true);
//        doLoggingConfigTest(
//                mapper,
//                null,
//                LoggingConfig.OMIT_RECORD_DETAILS_LOGGING_ENABLED_DEFAULT,
//                LoggingConfig.LOG_EVERY_REST_CALL_ENABLED_DEFAULT);
    }

    @Test
    void testDefaultValueSerDeSer2() throws IOException {
        // empty cache branch
        final String yaml = """
                maxDataElementStringLength: 0
                deviceCache:
                """;

        final LoggingConfig loggingConfig2 = YamlUtil.getMapper()
                .readValue(yaml, LoggingConfig.class);
        Assertions.assertThat(loggingConfig2.isOmitRecordDetailsLoggingEnabled())
                .isEqualTo(LoggingConfig.OMIT_RECORD_DETAILS_LOGGING_ENABLED_DEFAULT);
        Assertions.assertThat(loggingConfig2.isLogEveryRestCallEnabled())
                .isEqualTo(LoggingConfig.LOG_EVERY_REST_CALL_ENABLED_DEFAULT);
        Assertions.assertThat(loggingConfig2.getMaxListElements())
                .isEqualTo(LoggingConfig.MAX_LIST_ELEMENTS_DEFAULT);
        Assertions.assertThat(loggingConfig2.getMaxDataElementStringLength())
                .isEqualTo(0);
        Assertions.assertThat(loggingConfig2.getDeviceCache())
                .isNotNull();
        Assertions.assertThat(loggingConfig2.getDeviceCache().getMaximumSize())
                .isEqualTo(LoggingConfig.DEVICE_CACHE_DEFAULT.getMaximumSize());

    }

    @Test
    void testDefaultValueSerDeSer3() throws IOException {

        // sparse cache branch
        final String yaml2 = """
                maxDataElementStringLength: 0
                deviceCache:
                  maximumSize: 99
                """;

        final LoggingConfig loggingConfig3 = YamlUtil.getMapper()
                .readValue(yaml2, LoggingConfig.class);
        Assertions.assertThat(loggingConfig3.getDeviceCache().getMaximumSize())
                .isEqualTo(99);
        Assertions.assertThat(loggingConfig3.getDeviceCache().getExpireAfterWrite())
                .isEqualTo(LoggingConfig.DEVICE_CACHE_DEFAULT.getExpireAfterWrite());
    }

    @Test
    void testDefaultValueSerDeSer4() throws IOException {

        // null leaf values
        final String yaml2 = """
                maxListElements:
                logEveryRestCallEnabled:
                omitRecordDetailsLoggingEnabled:
                """;

        final LoggingConfig loggingConfig3 = YamlUtil.getMapper()
                .readValue(yaml2, LoggingConfig.class);

        SoftAssertions.assertSoftly(softAssertions -> {
            softAssertions.assertThat(loggingConfig3.getMaxListElements())
                    .isEqualTo(LoggingConfig.MAX_LIST_ELEMENTS_DEFAULT);
            softAssertions.assertThat(loggingConfig3.isOmitRecordDetailsLoggingEnabled())
                    .isEqualTo(LoggingConfig.OMIT_RECORD_DETAILS_LOGGING_ENABLED_DEFAULT);
            softAssertions.assertThat(loggingConfig3.isLogEveryRestCallEnabled())
                    .isEqualTo(LoggingConfig.LOG_EVERY_REST_CALL_ENABLED_DEFAULT);
        });
    }


    private void doLoggingConfigTest(final ObjectMapper objectMapper,
                                     final Boolean value,
                                     final boolean expectedOmitValue,
                                     final boolean expectedLogValue) throws IOException {

        final LoggingConfig loggingConfig = new LoggingConfig();
        loggingConfig.setOmitRecordDetailsLoggingEnabled(value);
        loggingConfig.setLogEveryRestCallEnabled(value);
        Assertions.assertThat(loggingConfig.isOmitRecordDetailsLoggingEnabled())
                .isEqualTo(expectedOmitValue);
        Assertions.assertThat(loggingConfig.isLogEveryRestCallEnabled())
                .isEqualTo(expectedLogValue);

        String yaml;
        yaml = objectMapper.writeValueAsString(loggingConfig);

        LOGGER.info("yaml:\n{}", yaml);

        final LoggingConfig loggingConfig2 = objectMapper.readValue(yaml, LoggingConfig.class);

        Assertions.assertThat(loggingConfig2)
                .isEqualTo(loggingConfig);
    }

    static Path getDevYamlPath() throws FileNotFoundException {
        // Load dev.yaml
        final String codeSourceLocation = TestAppConfigModule.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .getPath();

        Path path = Paths.get(codeSourceLocation);
        while (path != null && !path.getFileName().toString().equals("stroom-app")) {
            path = path.getParent();
        }
        if (path != null) {
            path = path.getParent();
            path = path.resolve("stroom-app");
            path = path.resolve("dev.yml");
        }

        if (path == null) {
            throw new FileNotFoundException("Unable to find dev.yml");
        }
        return path;
    }
}
