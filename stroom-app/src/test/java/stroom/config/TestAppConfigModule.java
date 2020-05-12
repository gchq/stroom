package stroom.config;

import stroom.config.app.AppConfig;
import stroom.config.app.AppConfigModule;
import stroom.config.app.AppConfigModule.ConfigHolder;
import stroom.config.app.Config;
import stroom.config.app.YamlUtil;
import stroom.config.common.CommonDbConfig;
import stroom.config.common.DbConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.config.PropertyUtil;
import stroom.util.logging.LogUtil;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.NotInjectableConfig;

import com.google.common.reflect.ClassPath;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
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

class TestAppConfigModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestAppConfigModule.class);
    private static final String MODIFIED_JDBC_DRIVER = "modified.jdbc.driver";
    private final static String STROOM_PACKAGE_PREFIX = "stroom.";

    @AfterEach
    void afterEach() {
//        FileUtil.deleteContents(tmpDir);
    }

    @Test
    void testCommonDbConfig() throws IOException {
        Path devYamlPath = getDevYamlPath();

        LOGGER.debug("dev yaml path: {}", devYamlPath.toAbsolutePath());

        // Modify the value on the common connection pool so it gets applied to all other config objects
        final Config modifiedConfig = YamlUtil.readConfig(devYamlPath);
//        modifiedConfig.getAppConfig().getCommonDbConfig().getConnectionPoolConfig().setPrepStmtCacheSize(250);
        int currentValue = modifiedConfig.getAppConfig().getCommonDbConfig().getConnectionPoolConfig().getPrepStmtCacheSize();
        int newValue = currentValue + 1000;

        modifiedConfig.getAppConfig().getCommonDbConfig().getConnectionPoolConfig().setPrepStmtCacheSize(newValue);

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

        Assertions.assertThat(commonDbConfig.getConnectionPoolConfig().getPrepStmtCacheSize())
                .isEqualTo(newValue);

        // Make sure all the getters that return a HasDbConfig have the modified conn value
        Arrays.stream(appConfig.getClass().getDeclaredMethods())
                .filter(method -> method.getName().startsWith("get"))
                .filter(method -> HasDbConfig.class.isAssignableFrom(method.getReturnType()))
                .map(method -> {
                    try {
                        return (HasDbConfig) method.invoke(appConfig);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                })
                .forEach(hasDbConfig -> {
                    final DbConfig mergedConfig = commonDbConfig.mergeConfig(hasDbConfig.getDbConfig());

                    Assertions.assertThat(mergedConfig.getConnectionConfig())
                            .isEqualTo(commonDbConfig.getConnectionConfig());

                    Assertions.assertThat(mergedConfig.getConnectionPoolConfig())
                            .isEqualTo(commonDbConfig.getConnectionPoolConfig());

                    Assertions.assertThat(mergedConfig.getConnectionPoolConfig().getPrepStmtCacheSize())
                            .isEqualTo(newValue);
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
        Injector injector = Guice.createInjector(appConfigModule);
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

        Predicate<String> packageNameFilter = name ->
                name.startsWith(STROOM_PACKAGE_PREFIX) && !name.contains("shaded");

        Predicate<Class<?>> classFilter = clazz -> {

            return clazz.getSimpleName().endsWith("Config")
                && !clazz.equals(AbstractConfig.class)
                && !clazz.equals(AppConfig.class);
        };

        LOGGER.info("Finding all AbstractConfig classes");

        // Find all classes that extend AbstractConfig
        final ClassLoader classLoader = getClass().getClassLoader();
        final Set<Class<?>> abstractConfigConcreteClasses = ClassPath.from(classLoader)
                .getAllClasses()
                .stream()
                .filter(classInfo -> packageNameFilter.test(classInfo.getPackageName()))
                .map(ClassPath.ClassInfo::load)
                .filter(classFilter)
                .filter(AbstractConfig.class::isAssignableFrom)
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

        LOGGER.info("Finding all classes in object tree");

        Map<Class<?>, Integer> appConfigTreeClassToIdMap = new HashMap<>();

        // Find all stroom. classes in the AppConfig tree, i.e. config POJOs
        final Set<Class<?>> appConfigTreeClasses = new HashSet<>();
        PropertyUtil.walkObjectTree(
                appConfig,
                prop -> packageNameFilter.test(prop.getValueClass().getPackageName()),
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
                    if (classFilter.test(valueClass)) {
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


    static Path getDevYamlPath() throws FileNotFoundException {
        // Load dev.yaml
        final String codeSourceLocation = TestAppConfigModule.class.getProtectionDomain().getCodeSource().getLocation().getPath();

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
