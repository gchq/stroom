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

package stroom.config;

import stroom.config.app.AppConfig;
import stroom.config.app.AppConfigModule;
import stroom.config.app.Config;
import stroom.config.app.ConfigHolder;
import stroom.config.app.StroomYamlUtil;
import stroom.config.common.AbstractDbConfig;
import stroom.config.common.CommonDbConfig;
import stroom.config.common.HasDbConfig;
import stroom.config.global.impl.ConfigProvidersModule;
import stroom.util.config.PropertyUtil;
import stroom.util.logging.LogUtil;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.google.common.reflect.ClassPath;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestAppConfigModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestAppConfigModule.class);
    private static final String MODIFIED_JDBC_DRIVER = "modified.jdbc.driver";
    private static final String STROOM_PACKAGE_PREFIX = "stroom.";

    @Test
    void testCommonDbConfig() throws IOException {
        final Path devYamlPath = getDevYamlPath();

        LOGGER.debug("dev yaml path: {}", devYamlPath.toAbsolutePath());

        // Modify the value on the common connection pool so it gets applied to all other config objects
        final Config modifiedConfig = StroomYamlUtil.readConfig(devYamlPath);

        final AppConfig modifiedAppConfig = modifiedConfig.getYamlAppConfig();

        final int currentValue = modifiedAppConfig
                .getCommonDbConfig()
                .getConnectionPoolConfig()
                .getPrepStmtCacheSize();
        final int newCacheValue = currentValue + 1000;

        modifiedAppConfig
                .getCommonDbConfig()
                .getConnectionPoolConfig()
                .setPrepStmtCacheSize(newCacheValue);

        final String newUser = modifiedAppConfig
                                       .getCommonDbConfig()
                                       .getConnectionConfig()
                                       .getUser() + "XXX";

        modifiedAppConfig
                .getCommonDbConfig()
                .getConnectionConfig()
                .setUser(newUser);

        final Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                install(new AppConfigModule(new ConfigHolder() {
                    @Override
                    public AppConfig getBootStrapConfig() {
                        return modifiedAppConfig;
                    }

                    @Override
                    public Path getConfigFile() {
                        return Path.of("DUMMY");
                    }
                }));
                install(new ConfigProvidersModule());
            }
        });

        final AppConfig appConfig = injector.getInstance(AppConfig.class);
        final CommonDbConfig commonDbConfig = injector.getInstance(CommonDbConfig.class);

        assertThat(commonDbConfig.getConnectionPoolConfig().getPrepStmtCacheSize())
                .isEqualTo(newCacheValue);
        assertThat(commonDbConfig.getConnectionConfig().getUser())
                .isEqualTo(newUser);

        // Make sure all the getters that return a HasDbConfig have the modified conn value
        final Stream<HasDbConfig> hasDbConfigsStream = Arrays.stream(appConfig.getClass().getDeclaredMethods())
                .filter(method -> method.getName().startsWith("get"))
                .filter(method -> HasDbConfig.class.isAssignableFrom(method.getReturnType()))
                .map(method -> {
                    try {
                        return (HasDbConfig) method.invoke(appConfig);
                    } catch (final IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                });

        hasDbConfigsStream
                .forEach(hasDbConfig -> {
                    LOGGER.info("Testing class: {}", hasDbConfig.getClass().getName());

                    final AbstractDbConfig mergedConfig = commonDbConfig.mergeConfig(hasDbConfig.getDbConfig());

                    // mergedConfig won't be the same as commonDbConfig as the driver class
                    // is not set in the yaml so is reliant on the default value. The default value
                    // is ignored in the jackson deserialisation so will be null in commonDbConfig
                    // but the merge factors it in so mergedConfig has it set.

                    assertThat(mergedConfig.getConnectionConfig().getClassName())
                            .isNotNull();
                    assertThat(mergedConfig.getConnectionConfig().getUser())
                            .isNotNull();
                    assertThat(mergedConfig.getConnectionConfig().getPassword())
                            .isNotNull();
                    assertThat(mergedConfig.getConnectionConfig().getUrl())
                            .isNotNull();

                    assertThat(mergedConfig.getConnectionConfig().getUser())
                            .isEqualTo(commonDbConfig.getConnectionConfig().getUser());
                    assertThat(mergedConfig.getConnectionConfig().getPassword())
                            .isEqualTo(commonDbConfig.getConnectionConfig().getPassword());
                    assertThat(mergedConfig.getConnectionConfig().getUrl())
                            .isEqualTo(commonDbConfig.getConnectionConfig().getUrl());

                    assertThat(mergedConfig.getConnectionPoolConfig())
                            .as(LogUtil.message("ConnectionPoolConfig doesn't match for class {}",
                                    hasDbConfig.getClass().getSimpleName()))
                            .isEqualTo(commonDbConfig.getConnectionPoolConfig());

                    assertThat(mergedConfig.getConnectionPoolConfig().getPrepStmtCacheSize())
                            .isEqualTo(newCacheValue);
                });
    }

    /**
     * IMPORTANT: This test must be run from stroom-app so it can see all the other modules
     */
    @Test
    void testAbstractConfigPresence() throws IOException {

        final AppConfig appConfig = new AppConfig();

        final AppConfigModule appConfigModule = new AppConfigModule(new ConfigHolder() {
            @Override
            public AppConfig getBootStrapConfig() {
                return appConfig;
            }

            @Override
            public Path getConfigFile() {
                return Paths.get("NOT USED");
            }
        });
        final Injector injector = Guice.createInjector(appConfigModule);

        final Predicate<String> packageNameFilter = name ->
                name.startsWith(STROOM_PACKAGE_PREFIX) && !name.contains("shaded");

        final Predicate<Class<?>> classFilter = clazz ->
                clazz.getSimpleName().endsWith("Config")
                && !clazz.equals(AbstractConfig.class)
                && !clazz.equals(AppConfig.class)
                && !Modifier.isPrivate(clazz.getModifiers()) // ignore local sub classes
                && IsStroomConfig.class.isAssignableFrom(clazz);

        LOGGER.info("Finding all stroom config classes");

        // Find all config classes
        final ClassLoader classLoader = getClass().getClassLoader();
        final Set<Class<?>> stroomConfigClasses = ClassPath.from(classLoader)
                .getAllClasses()
                .stream()
                .filter(classInfo -> packageNameFilter.test(classInfo.getPackageName()))
                .map(ClassPath.ClassInfo::load)
                .filter(classFilter)
                .filter(clazz -> {
                    final boolean isAbstract = Modifier.isAbstract(clazz.getModifiers());
                    if (isAbstract) {
                        LOGGER.info("Ignoring abstract class {}", clazz.getName());
                    }
                    return !isAbstract;
                }) // Ignore abstract classes, e.g. UriConfig
                .peek(clazz -> {
                    LOGGER.debug(clazz.getSimpleName());
                })
                .collect(Collectors.toSet());

        // All config classes should extend AbstractConfig and IsStroomConfig
        assertThat(stroomConfigClasses.stream()
                .filter(clazz ->
                        !AbstractConfig.class.isAssignableFrom(clazz)
                        || !IsStroomConfig.class.isAssignableFrom(clazz))
                .collect(Collectors.toList()))
                .isEmpty();

        LOGGER.info("Finding all classes in object tree");

        final Map<Class<?>, Integer> appConfigTreeClassToIdMap = new HashMap<>();

        // Find all stroom. classes in the AppConfig tree, i.e. config POJOs
        final Set<Class<?>> appConfigTreeClasses = new HashSet<>();
        PropertyUtil.walkObjectTree(
                appConfig,
                prop -> packageNameFilter.test(prop.getValueClass().getPackageName()),
                prop -> {
                    // make sure we have a getter and a setter
                    assertThat(prop.getGetter())
                            .as(LogUtil.message("{} {}",
                                    prop.getParentObject().getClass().getSimpleName(), prop.getGetter().getName()))
                            .isNotNull();

                    final Class<?> valueClass = prop.getValueClass();
                    if (classFilter.test(valueClass)) {
                        appConfigTreeClasses.add(prop.getValueClass());
                        final AbstractConfig propValue = (AbstractConfig) prop.getValueFromConfigObject();
                        // Keep a record of the instance ID of the instance in the tree
                        appConfigTreeClassToIdMap.put(valueClass, System.identityHashCode(propValue));
                    }
                });

        // Make sure all config classes extend AbstractConfig and all AbstractConfig classes are in
        // the AppConfig tree. If there is a mismatch then it may be due to the getter/setter not
        // being public in the config class, else the config class may not be a property in the
        // AppConfig object tree
        Set<Class<?>> remaining = new HashSet<>(appConfigTreeClasses);
        remaining.removeAll(stroomConfigClasses);
        assertThat(remaining)
                .describedAs("Class(es) in the config object tree but that don't implement IsStroomConfig")
                .isEmpty();

        remaining = new HashSet<>(stroomConfigClasses);
        remaining.removeAll(appConfigTreeClasses);
        assertThat(remaining)
                .describedAs("Class(es) that implement IsStroomConfig but aren't in the config object tree")
                .isEmpty();
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
