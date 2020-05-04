package stroom.dropwizard.common;

import stroom.util.ConsoleColour;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.StringUtils;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class RestResources {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RestResources.class);

    private final Environment environment;
    private final Map<RestResourcesBinder.ResourceType, Provider<RestResource>> providerMap;

    @Inject
    RestResources(final Environment environment, final Map<RestResourcesBinder.ResourceType, Provider<RestResource>> providerMap) {
        this.environment = environment;
        this.providerMap = providerMap;
    }

    public void register() {
        LOGGER.info(() -> "Adding REST resources:");

        final int maxNameLength = providerMap
                .keySet()
                .stream()
                .mapToInt(resourceType -> resourceType.getResourceClass().getName().length())
                .max()
                .orElse(0);

        final Set<String> allPaths = new HashSet<>();

        final List<ResourceProvider> resourceProviders = providerMap
                .entrySet()
                .stream()
                .map(entry -> {
                    final Class<?> resourceClass = entry.getKey().getResourceClass();
                    final Provider<RestResource> provider = entry.getValue();
                    return new ResourceProvider(
                            resourceClass,
                            provider,
                            getResourcePath(resourceClass).orElse(""));
                })
                .sorted(Comparator.comparing(ResourceProvider::getResourcePath))
                .filter(resourceProvider -> filter(maxNameLength, allPaths, resourceProvider))
                .collect(Collectors.toList());

//        resourceProviders.forEach(resourceProvider -> {
//            final Object proxy = Proxy.newProxyInstance(
//                    resourceProvider.resourceClass.getClassLoader(),
//                    resourceProvider.resourceClass.getInterfaces(),
//                    new RestResourceInvocationHandler(resourceProvider));
//
//            environment.jersey().register(proxy);
//        });

        environment.jersey().register(new HK2toGuiceModule(resourceProviders));
        resourceProviders.forEach(resourceProvider ->
                environment.jersey().register(resourceProvider.getResourceClass()));
    }

//    private static class RestResourceInvocationHandler implements InvocationHandler {
//        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(
//                RestResourceInvocationHandler.class);
//
//        private final ResourceProvider resourceProvider;
//
//        public RestResourceInvocationHandler(final ResourceProvider resourceProvider) {
//            this.resourceProvider = resourceProvider;
//        }
//
//        @Override
//        public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
//            try {
//                final long start = System.nanoTime();
//                final Object target = resourceProvider.getProvider().get();
//                final Object result = method.invoke(target, args);
//                final long elapsed = System.nanoTime() - start;
//
//                LOGGER.trace(() -> "Executing " + method.getName() + " finished in " + elapsed + "ns");
//
//                return result;
//            } catch (final InvocationTargetException e) {
//                LOGGER.trace(e::getMessage, e);
//                throw e.getTargetException();
//            } catch (final Throwable e) {
//                LOGGER.error(e::getMessage, e);
//                throw e;
//            }
//        }
//    }

    private Optional<String> getResourcePath(final Class<?> restResourceClass) {
        final Path pathAnnotation = restResourceClass.getAnnotation(Path.class);
        return Optional.ofNullable(pathAnnotation)
                .or(() ->
                        // No Path annotation on the RestResource so look for it in all interfaces
                        Arrays.stream(restResourceClass.getInterfaces())
                                .map(clazz -> clazz.getAnnotation(Path.class))
                                .filter(Objects::nonNull)
                                .findFirst())
                .map(path ->
                        ResourcePaths.buildAuthenticatedApiPath(path.value()));
    }

    private boolean filter(final int maxNameLength,
                           final Set<String> allPaths,
                           final ResourceProvider resourceProvider) {
        final String name = resourceProvider.getResourceClass().getName();
        if (allPaths.contains(resourceProvider.getResourcePath())) {
            LOGGER.error("\t{} => {}   {}",
                    StringUtils.rightPad(name, maxNameLength, " "),
                    resourceProvider.getResourcePath(),
                    ConsoleColour.red("**Duplicate path**"));
            throw new RuntimeException(LogUtil.message("Duplicate REST resource path {}", resourceProvider.getResourcePath()));
        } else {
            LOGGER.info("\t{} => {}",
                    StringUtils.rightPad(name, maxNameLength, " "),
                    resourceProvider.getResourcePath());
        }

        allPaths.add(resourceProvider.getResourcePath());
        return true;
    }

    private static class HK2toGuiceModule extends AbstractBinder {
        private final List<ResourceProvider> resourceProviders;

        public HK2toGuiceModule(final List<ResourceProvider> resourceProviders) {
            this.resourceProviders = resourceProviders;
        }

        @Override
        protected void configure() {
            resourceProviders.forEach(resourceProvider ->
                    bindFactory(new ServiceFactory<>(resourceProvider.getProvider()))
                            .to(resourceProvider.getResourceClass()));
        }

        private static class ServiceFactory<T> implements Factory<T> {
            private final Provider<T> provider;

            ServiceFactory(final Provider<T> provider) {
                this.provider = provider;
            }

            @Override
            public T provide() {
                return provider.get();
            }

            @Override
            public void dispose(T versionResource) {
            }
        }
    }

    private static class ResourceProvider {
        private final Class<?> resourceClass;
        private final Provider<RestResource> provider;
        private final String resourcePath;

        public ResourceProvider(final Class<?> resourceClass,
                                final Provider<RestResource> provider,
                                final String resourcePath) {
            this.resourceClass = resourceClass;
            this.provider = provider;
            this.resourcePath = resourcePath;
        }

        public Class<?> getResourceClass() {
            return resourceClass;
        }

        public Provider<RestResource> getProvider() {
            return provider;
        }

        public String getResourcePath() {
            return resourcePath;
        }
    }
}
