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
import stroom.config.app.AppConfigModule;
import stroom.config.app.ConfigHolder;
import stroom.security.impl.StroomOpenIdConfig;
import stroom.security.openid.api.AbstractOpenIdConfig;
import stroom.util.io.PathConfig;
import stroom.util.io.StroomPathConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class TestConfigProvidersModule {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestConfigProvidersModule.class);

    private static final Set<Class<?>> SPECIAL_CASE_CLASSES = Set.of(
            PathConfig.class,
            AbstractOpenIdConfig.class);

    @Test
    void testProviderMethodPresence() {
        final ConfigMapper configMapper = new ConfigMapper();

        final Set<Class<?>> methodReturnClasses = Arrays.stream(ConfigProvidersModule.class.getDeclaredMethods())
                .filter(method -> !method.getName().endsWith(GenerateConfigProvidersModule.THROWING_METHOD_SUFFIX))
                .map(Method::getReturnType)
                .filter(clazz ->
                        !SPECIAL_CASE_CLASSES.contains(clazz))
                .collect(Collectors.toSet());

        final Set<Class<?>> notInjectableMethodReturnClasses = Arrays.stream(
                ConfigProvidersModule.class.getDeclaredMethods())
                .filter(method -> method.getName().endsWith(GenerateConfigProvidersModule.THROWING_METHOD_SUFFIX))
                .map(Method::getReturnType)
                .collect(Collectors.toSet());

        assertThat(methodReturnClasses)
                .doesNotContainAnyElementsOf(notInjectableMethodReturnClasses);

        final Set<Class<?>> injectableConfigClasses = new HashSet<>(
                configMapper.getInjectableConfigClasses());

        assertThat(methodReturnClasses)
                .containsExactlyInAnyOrderElementsOf(injectableConfigClasses);

        assertThat(methodReturnClasses.containsAll(injectableConfigClasses))
                .withFailMessage(() -> {
                    final Set<Class<?>> unwantedMethods = new HashSet<>(methodReturnClasses);
                    unwantedMethods.removeAll(injectableConfigClasses);

                    return LogUtil.message(
                            "{} should contain an @Provides method for each injectable config class. " +
                                    "Found the following unwanted method return types {}. " +
                                    "injectableConfigClasses: {}, methodReturnClasses: {}. " +
                                    "See {}.",
                            ConfigProvidersModule.class.getSimpleName(),
                            unwantedMethods,
                            injectableConfigClasses.size(),
                            methodReturnClasses.size(),
                            GenerateConfigProvidersModule.class.getSimpleName());
                })
                .isTrue();

        assertThat(injectableConfigClasses.containsAll(methodReturnClasses))
                .withFailMessage(() -> {
                    final Set<Class<?>> missingMethods = new HashSet<>(injectableConfigClasses);
                    missingMethods.removeAll(methodReturnClasses);

                    return LogUtil.message(
                            "{} should contain an @Provides method for each injectable config class. " +
                                    "Found the following missing method return types {}. " +
                                    "injectableConfigClasses: {}, methodReturnClasses: {}. " +
                                    "See {}.",
                            ConfigProvidersModule.class.getSimpleName(),
                            missingMethods,
                            injectableConfigClasses.size(),
                            methodReturnClasses.size(),
                            GenerateConfigProvidersModule.class.getSimpleName());
                })
                .isTrue();
    }

    @Test
    void testCallingProviderMethods() {
        final ConfigProvidersModule configProvidersModule = new ConfigProvidersModule();
        final ConfigHolder configHolder = new ConfigHolder() {
            @Override
            public AppConfig getBootStrapConfig() {
                return new AppConfig();
            }

            @Override
            public Path getConfigFile() {
                return Path.of("DUMMY");
            }
        };

        final Injector injector = Guice.createInjector(
                configProvidersModule,
                new AppConfigModule(configHolder));

        final ConfigMapper configMapper = injector.getInstance(ConfigMapper.class);
//        configMapper.updateConfigInstances(new AppConfig());

        SoftAssertions.assertSoftly(softAssertions -> {
            Arrays.stream(ConfigProvidersModule.class.getDeclaredMethods())
                    .filter(method ->
                            !method.getName().endsWith(GenerateConfigProvidersModule.THROWING_METHOD_SUFFIX))
                    .forEach(method -> {
                        LOGGER.debug("method: {}", method.getName());

                        try {
                            final Object config = method.invoke(configProvidersModule, configMapper);
                            softAssertions.assertThat(config)
                                    .withFailMessage(() ->
                                            LogUtil.message("method {} returned null", method.getName()))
                                    .isNotNull();

                            final String className = method.getName()
                                    .replaceAll("^get", "")
                                    .replaceAll("[0-9]$", "");

                            if (method.getName().equals("getPathConfig")) {
                                // StroomPathConfig is also mapped to PathConfig
                                softAssertions.assertThat(config.getClass())
                                        .isEqualTo(StroomPathConfig.class);
                            } else if (method.getName().equals("getAbstractOpenIdConfig")) {
                                softAssertions.assertThat(config.getClass())
                                        .isEqualTo(StroomOpenIdConfig.class);
//                            } else if (method.getName().equals("getRepoConfig")) {
//                                // StroomPathConfig is also mapped to PathConfig
//                                softAssertions.assertThat(config.getClass())
//                                        .isEqualTo(ProxyAggregationConfig.class);
//                            } else if (method.getName().equals("getRepoDbConfig")) {
//                                // StroomPathConfig is also mapped to PathConfig
//                                softAssertions.assertThat(config.getClass())
//                                        .isEqualTo(ProxyAggregationRepoDbConfig.class);
                            } else {
                                softAssertions.assertThat(config.getClass().getSimpleName())
                                        .withFailMessage(LogUtil.message("method {} returned {}, expecting {}",
                                                method.getName(),
                                                config.getClass().getName(),
                                                className))
                                        .isEqualTo(className);
                            }

                            // Make sure guice gives us the same instance as ConfigMapper
                            final Object guiceObject = injector.getInstance(config.getClass());
                            softAssertions.assertThat(System.identityHashCode(guiceObject))
                                    .isEqualTo(System.identityHashCode(config));

                        } catch (final IllegalAccessException | InvocationTargetException e) {
                            throw new RuntimeException(e);
                        }
                    });
        });

    }

    @Test
    void testCallingNotInjectableProviderMethods() {
        final ConfigProvidersModule configProvidersModule = new ConfigProvidersModule();
        final ConfigHolder configHolder = new ConfigHolder() {
            @Override
            public AppConfig getBootStrapConfig() {
                return new AppConfig();
            }

            @Override
            public Path getConfigFile() {
                return Path.of("DUMMY");
            }
        };

        final Injector injector = Guice.createInjector(
                configProvidersModule,
                new AppConfigModule(configHolder));

        final ConfigMapper configMapper = injector.getInstance(ConfigMapper.class);

        SoftAssertions.assertSoftly(softAssertions -> {
            Arrays.stream(ConfigProvidersModule.class.getDeclaredMethods())
                    .filter(method ->
                            method.getName().endsWith(GenerateConfigProvidersModule.THROWING_METHOD_SUFFIX))
                    .forEach(method -> {
                        LOGGER.debug("method: {}", method.getName());

                        softAssertions
                                .assertThatThrownBy(() -> {
                                    try {
                                        method.invoke(configProvidersModule, configMapper);
                                    } catch (final IllegalAccessException | InvocationTargetException e) {
                                        if (e.getCause().getClass().equals(UnsupportedOperationException.class)) {
                                            throw e.getCause();
                                        }
                                        LOGGER.error("Error: {}", e.getMessage(), e);
                                        throw new RuntimeException(e);
                                    }
                                })
                                .withFailMessage(() -> LogUtil.message("Method {} should throw {}",
                                        method.getName(), UnsupportedOperationException.class.getSimpleName()))

                                .isInstanceOf(UnsupportedOperationException.class);
                    });
        });
    }
}
