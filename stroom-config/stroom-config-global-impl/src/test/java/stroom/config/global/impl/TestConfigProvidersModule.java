package stroom.config.global.impl;

import stroom.config.app.AppConfig;
import stroom.util.io.PathConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class TestConfigProvidersModule {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestConfigProvidersModule.class);

    @Test
    void testProviderMethodPresence() {
        final ConfigMapper configMapper = new ConfigMapper();

        final Set<Class<?>> methodReturnClasses = Arrays.stream(ConfigProvidersModule.class.getDeclaredMethods())
                .map(Method::getReturnType)
                .filter(clazz ->
                        !clazz.equals(PathConfig.class)) // PathConfig is a special case
                .collect(Collectors.toSet());

        final Set<Class<?>> injectableConfigClasses = new HashSet<>(
                configMapper.getInjectableConfigClasses());

        SoftAssertions.assertSoftly(softAssertions -> {
            softAssertions.assertThat(methodReturnClasses.containsAll(injectableConfigClasses))
                    .withFailMessage(() -> {
                        final Set<Class<?>> unwantedMethods = new HashSet<>(methodReturnClasses);
                        unwantedMethods.removeAll(injectableConfigClasses);

                        return LogUtil.message(
                                "{} should contain an @Provides method for each injectable config class. " +
                                        "Found the following unwanted method return types {}. See {}.",
                                ConfigProvidersModule.class.getSimpleName(),
                                unwantedMethods,
                                GenerateConfigProvidersModule.class.getSimpleName());
                    })
                    .isTrue();

            softAssertions.assertThat(injectableConfigClasses.containsAll(methodReturnClasses))
                    .withFailMessage(() -> {
                        final Set<Class<?>> missingMethods = new HashSet<>(injectableConfigClasses);
                        missingMethods.removeAll(methodReturnClasses);

                        return LogUtil.message(
                                "{} should contain an @Provides method for each injectable config class. " +
                                        "Found the following missing method return types {}. See {}.",
                                ConfigProvidersModule.class.getSimpleName(),
                                missingMethods,
                                GenerateConfigProvidersModule.class.getSimpleName());
                    })
                    .isTrue();
        });
    }

    @Test
    void testCallingProviderMethods() {
        final ConfigProvidersModule configProvidersModule = new ConfigProvidersModule();
        final ConfigMapper configMapper = new ConfigMapper();
        configMapper.updateConfigInstances(new AppConfig());

        SoftAssertions.assertSoftly(softAssertions -> {
            Arrays.stream(ConfigProvidersModule.class.getDeclaredMethods())
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

                            softAssertions.assertThat(config.getClass().getSimpleName())
                                    .withFailMessage(LogUtil.message("method {} returned {}, expecting {}",
                                            method.getName(),
                                            config.getClass().getName(),
                                            className))
                                    .isEqualTo(className);

                        } catch (IllegalAccessException | InvocationTargetException e) {
                            throw new RuntimeException(e);
                        }
                    });
        });
    }
}
