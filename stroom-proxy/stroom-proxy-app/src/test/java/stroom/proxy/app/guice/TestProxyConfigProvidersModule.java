package stroom.proxy.app.guice;

import stroom.proxy.app.ProxyConfig;
import stroom.proxy.app.ProxyPathConfig;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.proxy.repo.ProxyRepoDbConfig;
import stroom.proxy.repo.RepoConfig;
import stroom.proxy.repo.RepoDbConfig;
import stroom.util.io.PathConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.AbstractConfig;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class TestProxyConfigProvidersModule {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestProxyConfigProvidersModule.class);

    private static final Set<Class<?>> SPECIAL_CASE_CLASSES = Set.of(
            PathConfig.class,
            RepoConfig.class,
            RepoDbConfig.class);

    @Test
    void testProviderMethodPresence() {
        final ProxyConfigProvider proxyConfigProvider = new ProxyConfigProvider(new ProxyConfig());

        final Set<Class<?>> methodReturnClasses = Arrays.stream(ProxyConfigProvidersModule.class.getDeclaredMethods())
                .filter(method -> !method.getName().endsWith(GenerateProxyConfigProvidersModule.THROWING_METHOD_SUFFIX))
                .map(Method::getReturnType)
                .filter(clazz ->
                        !SPECIAL_CASE_CLASSES.contains(clazz))
                .collect(Collectors.toSet());

        final Set<Class<?>> notInjectableMethodReturnClasses = Arrays.stream(
                ProxyConfigProvidersModule.class.getDeclaredMethods())
                .filter(method -> method.getName().endsWith(GenerateProxyConfigProvidersModule.THROWING_METHOD_SUFFIX))
                .map(Method::getReturnType)
                .collect(Collectors.toSet());

        assertThat(methodReturnClasses)
                .doesNotContainAnyElementsOf(notInjectableMethodReturnClasses);

        final Set<Class<? extends AbstractConfig>> injectableConfigClasses = proxyConfigProvider.getInjectableClasses();

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
                            ProxyConfigProvidersModule.class.getSimpleName(),
                            unwantedMethods,
                            injectableConfigClasses.size(),
                            methodReturnClasses.size(),
                            GenerateProxyConfigProvidersModule.class.getSimpleName());
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
                            ProxyConfigProvidersModule.class.getSimpleName(),
                            missingMethods,
                            injectableConfigClasses.size(),
                            methodReturnClasses.size(),
                            GenerateProxyConfigProvidersModule.class.getSimpleName());
                })
                .isTrue();
    }

    @Test
    void testCallingProviderMethods() {
        final ProxyConfigProvidersModule configProvidersModule = new ProxyConfigProvidersModule();
        final ProxyConfigProvider proxyConfigProvider = new ProxyConfigProvider(new ProxyConfig());

        SoftAssertions.assertSoftly(softAssertions -> {
            Arrays.stream(ProxyConfigProvidersModule.class.getDeclaredMethods())
                    .filter(method ->
                            !method.getName().endsWith(GenerateProxyConfigProvidersModule.THROWING_METHOD_SUFFIX))
                    .forEach(method -> {
                        LOGGER.debug("method: {}", method.getName());

                        try {
                            final Object config = method.invoke(configProvidersModule, proxyConfigProvider);
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
                                        .isEqualTo(ProxyPathConfig.class);
                            } else if (method.getName().equals("getRepoConfig")) {
                                // StroomPathConfig is also mapped to PathConfig
                                softAssertions.assertThat(config.getClass())
                                        .isEqualTo(ProxyRepoConfig.class);
                            } else if (method.getName().equals("getRepoDbConfig")) {
                                // StroomPathConfig is also mapped to PathConfig
                                softAssertions.assertThat(config.getClass())
                                        .isEqualTo(ProxyRepoDbConfig.class);
                            } else {
                                softAssertions.assertThat(config.getClass().getSimpleName())
                                        .withFailMessage(LogUtil.message("method {} returned {}, expecting {}",
                                                method.getName(),
                                                config.getClass().getName(),
                                                className))
                                        .isEqualTo(className);
                            }

                        } catch (IllegalAccessException | InvocationTargetException e) {
                            throw new RuntimeException(e);
                        }
                    });
        });
    }

    @Test
    void testCallingNotInjectableProviderMethods() {
        final ProxyConfigProvidersModule configProvidersModule = new ProxyConfigProvidersModule();
        final ProxyConfigProvider proxyConfigProvider = new ProxyConfigProvider(new ProxyConfig());

        SoftAssertions.assertSoftly(softAssertions -> {
            Arrays.stream(ProxyConfigProvidersModule.class.getDeclaredMethods())
                    .filter(method ->
                            method.getName().endsWith(GenerateProxyConfigProvidersModule.THROWING_METHOD_SUFFIX))
                    .forEach(method -> {
                        LOGGER.debug("method: {}", method.getName());

                        softAssertions
                                .assertThatThrownBy(() -> {
                                    try {
                                        method.invoke(configProvidersModule, proxyConfigProvider);
                                    } catch (IllegalAccessException | InvocationTargetException e) {
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
