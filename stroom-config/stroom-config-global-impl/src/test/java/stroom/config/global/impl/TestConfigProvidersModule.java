package stroom.config.global.impl;

import stroom.util.logging.LogUtil;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class TestConfigProvidersModule {

    @Test
    void testProviderMethodPresence() {
        final ConfigMapper configMapper = new ConfigMapper();

        final Set<Class<?>> methodReturnClasses = Arrays.stream(ConfigProvidersModule.class.getDeclaredMethods())
                .map(Method::getReturnType)
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
}
