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

package stroom.proxy.app;

import stroom.proxy.app.guice.ProxyConfigModule;
import stroom.util.config.PropertyUtil;
import stroom.util.logging.LogUtil;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.NotInjectableConfig;

import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

class TestProxyConfigModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestProxyConfigModule.class);
    private static final String STROOM_PACKAGE_PREFIX = "stroom.";

    /**
     * IMPORTANT: This test must be run from stroom-proxy-app so it can see all the other modules
     */
    @Test
    void testIsProxyConfigPresence() throws IOException {

        final ProxyConfig proxyConfig = new ProxyConfig();
        final ProxyConfigModule proxyConfigModule = new ProxyConfigModule(new ProxyConfigHolder(
                proxyConfig,
                Path.of("/dummy/path/to/config.yml")));

        final Injector injector = Guice.createInjector(
                proxyConfigModule);

        final Predicate<String> packageNameFilter = name ->
                name.startsWith(STROOM_PACKAGE_PREFIX) && !name.contains("shaded");

        final Predicate<Class<?>> classFilter = clazz ->
                clazz.getSimpleName().endsWith("Config")
                && !clazz.equals(AbstractConfig.class)
                && !clazz.equals(ProxyConfig.class)
                && IsProxyConfig.class.isAssignableFrom(clazz)
                && !clazz.getSimpleName().startsWith("Test");

        LOGGER.info("Finding all IsProxyConfig classes");

        // Find all classes that extend IsProxyConfig
        final ClassLoader classLoader = getClass().getClassLoader();
        final Set<Class<?>> isProxyConfigConcreteClasses = ClassPath.from(classLoader)
                .getAllClasses()
                .stream()
                .filter(classInfo -> packageNameFilter.test(classInfo.getPackageName()))
                .map((final ClassInfo classInfo2) -> {
                    try {
                        return classInfo2.load();
                    } catch (final Throwable e) {
                        throw new RuntimeException(LogUtil.message(
                                "Unable to load class {}", classInfo2), e);
                    }
                })
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

        LOGGER.info("Finding all classes in object tree");

        final Map<Class<?>, Integer> appConfigTreeClassToIdMap = new HashMap<>();

        // Find all stroom. classes in the config tree, i.e. config POJOs
        final Set<Class<?>> appConfigTreeClasses = new HashSet<>();
        PropertyUtil.walkObjectTree(
                proxyConfig,
                prop -> packageNameFilter.test(prop.getValueClass().getPackageName()),
                prop -> {
                    // make sure we have a getter and a setter
                    Assertions.assertThat(prop.getGetter())
                            .as(LogUtil.message("{} {}",
                                    prop.getParentObject().getClass().getSimpleName(), prop.getGetter().getName()))
                            .isNotNull();

//                    Assertions.assertThat(prop.getSetter())
//                            .as(LogUtil.message("{} {}",
//                                    prop.getParentObject().getClass().getSimpleName(), prop.getSetter().getName()))
//                            .isNotNull();

                    final Class<?> valueClass = prop.getValueClass();
                    if (classFilter.test(valueClass)) {
                        final IsProxyConfig propValue = (IsProxyConfig) prop.getValueFromConfigObject();
                        appConfigTreeClasses.add(prop.getValueClass());
                        // Keep a record of the instance ID of the instance in the tree
                        appConfigTreeClassToIdMap.put(valueClass, System.identityHashCode(propValue));
                    }
                });

        final Map<Class<?>, Integer> injectedInstanceIdMap = isProxyConfigConcreteClasses.stream()
                .filter(clazz -> !clazz.isAnnotationPresent(NotInjectableConfig.class))
                .collect(Collectors.toMap(
                        clazz -> clazz,
                        clazz -> {
                            final Object object = injector.getInstance(clazz);
                            return System.identityHashCode(object);
                        }));

        // Make sure all config classes extend IsProxyConfig and all IsProxyConfig classes are in
        // the AppConfig tree. If there is a mismatch then it may be due to the getter/setter not
        // being public in the config class, else the config class may not be a property in the
        // AppConfig object tree
        Assertions.assertThat(isProxyConfigConcreteClasses)
                .containsAll(appConfigTreeClasses);

        // We can't check that all sub classes of IsProxyConfig are in the tree as some
        // branches have a null default or an empty list, ForwardStreamConfig#getForwardDestinations
//        Assertions.assertThat(appConfigTreeClasses)
//                .containsAll(isProxyConfigConcreteClasses);

        // Now we know the appConfig tree contains all the concrete IsProxyConfig classes
        // check that guice will give us the right instance. This ensures

        final List<Class<?>> classesWithMultipleInstances = appConfigTreeClassToIdMap.entrySet()
                .stream()
                .filter(entry -> {
                    final Integer appConfigTreeInstanceId = entry.getValue();
                    final Integer injectedInstanceId = injectedInstanceIdMap.get(entry.getKey());

                    // Some IsProxyConfig classes are shared so can't be injected themselves
                    // so filter them out
                    final boolean isInjectableClass = entry.getKey().getAnnotation(NotInjectableConfig.class) == null;

                    return isInjectableClass && !injectedInstanceId.equals(appConfigTreeInstanceId);
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (!classesWithMultipleInstances.isEmpty()) {
            LOGGER.error("The following IsProxyConfig classes have a different injected instance to the " +
                         "instance in the AppConfig tree.\n" +
                         "You need to add Guice bindings for them in AppConfigModule");
            classesWithMultipleInstances.stream()
                    .sorted(Comparator.comparing(Class::getName))
                    .forEach(clazz -> {
                        final AbstractConfig config = (AbstractConfig) injector.getInstance(clazz);
                        LOGGER.info("  {}", config.getBasePathStr());
                    });
        }

        Assertions.assertThat(classesWithMultipleInstances)
                .isEmpty();
    }
}
