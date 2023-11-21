package stroom.dropwizard.common;

import stroom.util.ConsoleColour;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.dropwizard.core.setup.Environment;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.Path;
import org.apache.commons.lang3.StringUtils;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

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
    RestResources(final Environment environment,
                  final Map<RestResourcesBinder.ResourceType, Provider<RestResource>> providerMap) {
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
                .sorted(Comparator.comparing(ResourceProvider::resourcePath))
                .filter(resourceProvider -> filter(maxNameLength, allPaths, resourceProvider))
                .collect(Collectors.toList());

        environment.jersey().register(new HK2toGuiceModule(resourceProviders));
        resourceProviders.forEach(resourceProvider ->
                environment.jersey().register(resourceProvider.resourceClass()));
    }

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
        final String name = resourceProvider.resourceClass().getName();
        if (allPaths.contains(resourceProvider.resourcePath())) {
            LOGGER.error("\t{} => {}   {}",
                    StringUtils.rightPad(name, maxNameLength, " "),
                    resourceProvider.resourcePath(),
                    ConsoleColour.red("**Duplicate path**"));
            throw new RuntimeException(LogUtil.message(
                    "Duplicate REST resource path {}",
                    resourceProvider.resourcePath()));
        } else {
            LOGGER.info("\t{} => {}",
                    StringUtils.rightPad(name, maxNameLength, " "),
                    resourceProvider.resourcePath());
        }

        allPaths.add(resourceProvider.resourcePath());
        return true;
    }


    // --------------------------------------------------------------------------------


    private static class HK2toGuiceModule extends AbstractBinder {

        private final List<ResourceProvider> resourceProviders;

        public HK2toGuiceModule(final List<ResourceProvider> resourceProviders) {
            this.resourceProviders = resourceProviders;
        }

        @Override
        protected void configure() {
            resourceProviders.forEach(resourceProvider ->
                    bindFactory(new ServiceFactory<>(resourceProvider.provider()))
                            .to(resourceProvider.resourceClass()));
        }


        // --------------------------------------------------------------------------------


        private record ServiceFactory<T>(Provider<T> provider) implements Factory<T> {

            @Override
            public T provide() {
                return provider.get();
            }

            @Override
            public void dispose(T versionResource) {
            }
        }
    }


    // --------------------------------------------------------------------------------


    private record ResourceProvider(Class<?> resourceClass,
                                    Provider<RestResource> provider,
                                    String resourcePath) {

    }
}
