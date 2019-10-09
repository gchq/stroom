package stroom.config;

import com.google.common.reflect.ClassPath;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.app.AppConfig;
import stroom.config.app.Config;
import stroom.config.app.YamlUtil;
import stroom.config.common.CommonDbConfig;
import stroom.config.common.HasDbConfig;
import stroom.test.CoreTestModule;
import stroom.util.config.PropertyUtil;
import stroom.util.logging.LogUtil;
import stroom.util.shared.IsConfig;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

class TestAppConfigModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestAppConfigModule.class);
    private static final String MODIFIED_JDBC_DRIVER = "modified.jdbc.driver";
    private final static String STROOM_PACKAGE_PREFIX = "stroom.";

//    private final Path tmpDir = FileUtil.createTempDir(this.getClass().getSimpleName());

    @AfterEach
    void afterEach() {
//        FileUtil.deleteContents(tmpDir);
    }

    @Test
    void configure() throws IOException {

        Path devYamlPath = getDevYamlPath();

        LOGGER.debug("dev yaml path: {}", devYamlPath.toAbsolutePath());

        // Modify the value on the common connection pool so it gets applied to all other config objects
        final Config modifiedConfig = YamlUtil.readConfig(devYamlPath);
        int currentValue = modifiedConfig.getAppConfig().getCommonDbConfig().getConnectionPoolConfig().getPrepStmtCacheSize();
        int newValue = currentValue + 1000;

        modifiedConfig.getAppConfig().getCommonDbConfig().getConnectionPoolConfig().setPrepStmtCacheSize(newValue);

        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                install(new CoreTestModule(modifiedConfig.getAppConfig()));
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
                    Assertions.assertThat(hasDbConfig.getDbConfig().getConnectionConfig())
                            .isEqualTo(commonDbConfig.getConnectionConfig());
                    Assertions.assertThat(hasDbConfig.getDbConfig().getConnectionPoolConfig())
                            .isEqualTo(commonDbConfig.getConnectionPoolConfig());
                    Assertions.assertThat(hasDbConfig.getDbConfig().getConnectionPoolConfig().getPrepStmtCacheSize())
                            .isEqualTo(newValue);
                });
    }

    /**
     * IMPORTANT: This test must be run from stroom-app so it can see all the other modules
     */
    @Test
    void testIsConfigPresence() throws IOException {
        final AppConfig appConfig = new AppConfig();

        Predicate<String> packageNameFilter = name ->
                name.startsWith(STROOM_PACKAGE_PREFIX) && !name.contains("shaded");

        Predicate<Class<?>> classFilter = clazz ->
                !clazz.equals(IsConfig.class) && !clazz.equals(AppConfig.class);


        // Find all classes that implement IsConfig
        final ClassLoader classLoader = getClass().getClassLoader();
        final Set<Class<?>> isConfigClasses = ClassPath.from(classLoader).getAllClasses()
                .stream()
                .filter(classInfo -> packageNameFilter.test(classInfo.getPackageName()))
                .map(ClassPath.ClassInfo::load)
                .filter(classFilter)
                .filter(IsConfig.class::isAssignableFrom)
                .peek(clazz -> {
                    LOGGER.debug(clazz.getSimpleName());
                })
                .collect(Collectors.toSet());

        // Find all stroom. classes in the AppConfig tree, i.e. config POJOs
        final Set<Class<?>> configClasses = new HashSet<>();
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
                        configClasses.add(prop.getValueClass());
                    }
                });

        Assertions.assertThat(isConfigClasses)
                .contains(CommonDbConfig.class);
        Assertions.assertThat(configClasses)
                .contains(CommonDbConfig.class);

        // Make sure all config classes implement IsConfig and all IsConfig classes are in
        // the AppConfig tree
        Assertions.assertThat(isConfigClasses)
                .containsAll(configClasses);
        Assertions.assertThat(configClasses)
                .containsAll(isConfigClasses);
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