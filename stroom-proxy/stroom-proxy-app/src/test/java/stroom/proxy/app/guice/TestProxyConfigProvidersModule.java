package stroom.proxy.app.guice;

import stroom.proxy.app.ProxyConfig;
import stroom.proxy.app.ProxyPathConfig;
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

public class TestProxyConfigProvidersModule {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestProxyConfigProvidersModule.class);

    @Test
    void testProviderMethodPresence() {
        final ProxyConfigProvider proxyConfigProvider = new ProxyConfigProvider(new ProxyConfig());

        final Set<Class<?>> methodReturnClasses = Arrays.stream(ProxyConfigProvidersModule.class.getDeclaredMethods())
                .map(Method::getReturnType)
                .filter(clazz ->
                        !clazz.equals(PathConfig.class)) // PathConfig is a special case
                .collect(Collectors.toSet());

        final Set<Class<? extends AbstractConfig>> injectableConfigClasses = proxyConfigProvider.getInjectableClasses();

        SoftAssertions.assertSoftly(softAssertions -> {
            softAssertions.assertThat(methodReturnClasses)
                    .containsExactlyInAnyOrderElementsOf(injectableConfigClasses);

            softAssertions.assertThat(methodReturnClasses.containsAll(injectableConfigClasses))
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

            softAssertions.assertThat(injectableConfigClasses.containsAll(methodReturnClasses))
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
        });
    }

    @Test
    void testCallingProviderMethods() {
        final ProxyConfigProvidersModule configProvidersModule = new ProxyConfigProvidersModule();
        final ProxyConfigProvider proxyConfigProvider = new ProxyConfigProvider(new ProxyConfig());

        SoftAssertions.assertSoftly(softAssertions -> {
            Arrays.stream(ProxyConfigProvidersModule.class.getDeclaredMethods())
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
}
