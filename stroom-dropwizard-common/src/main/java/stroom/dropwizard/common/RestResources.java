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

package stroom.dropwizard.common;

import stroom.util.ConsoleColour;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.Unauthenticated;

import io.dropwizard.core.setup.Environment;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.Path;
import org.apache.commons.lang3.StringUtils;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RestResources {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RestResources.class);

    private final Environment environment;
    private final Map<RestResourcesBinder.ResourceType, Provider<RestResource>> providerMap;
    private final AuthenticationBypassCheckerImpl authenticationBypassCheckerImpl;

    @Inject
    RestResources(final Environment environment,
                  final Map<RestResourcesBinder.ResourceType, Provider<RestResource>> providerMap,
                  final AuthenticationBypassCheckerImpl authenticationBypassCheckerImpl) {
        this.environment = environment;
        this.providerMap = providerMap;
        this.authenticationBypassCheckerImpl = authenticationBypassCheckerImpl;
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
                .filter(resourceProvider ->
                        filter(maxNameLength, allPaths, resourceProvider))
                .collect(Collectors.toList());

        registerUnauthenticatedPaths(resourceProviders);

        // Binds a guice provider for the resource class to the class using jersey's HK2 dependency
        // injection framework
        environment.jersey().register(new HK2toGuiceModule(resourceProviders));

        // Now register all the resource classes
        resourceProviders.forEach(resourceProvider ->
                environment.jersey().register(resourceProvider.resourceClass()));
    }

    private void registerUnauthenticatedPaths(final List<ResourceProvider> resourceProviders) {
        NullSafe.list(resourceProviders)
                .stream()
                .flatMap(this::getAllMethodPathsForResource)
                .forEach(path -> {
                    LOGGER.debug("Registering API path {} as unauthenticated", path);
                    authenticationBypassCheckerImpl.registerUnauthenticatedApiPath(path);
                });
    }

    private Stream<String> getAllMethodPathsForResource(final ResourceProvider resourceProvider) {
        final Class<?> clazz = resourceProvider.resourceClass;
        final String resourcePath = resourceProvider.resourcePath;

        final boolean isWholeClassUnauthenticated = NullSafe.isTrue(getFromClassOrSuper(clazz, clazz2 ->
                clazz2.isAnnotationPresent(Unauthenticated.class)
                        ? true
                        : null));

        final List<String> unauthenticatedPaths = new ArrayList<>();
        for (final Method method : clazz.getMethods()) {
            final boolean isMethodUnauthenticated;
            if (isWholeClassUnauthenticated) {
                isMethodUnauthenticated = true;
            } else {
                isMethodUnauthenticated = NullSafe.isTrue(getFromMethodOrSuper(clazz, method, method2 -> {
                    final boolean hasAnnotation = method2.isAnnotationPresent(Unauthenticated.class);
                    return hasAnnotation
                            ? true
                            : null;
                }));
            }

            if (isMethodUnauthenticated) {
                final Path pathAnno = getFromMethodOrSuper(clazz, method, method2 ->
                        method2.getAnnotation(Path.class));
                if (pathAnno != null) {
                    String methodPath = pathAnno.value();
                    final int braceIdx = methodPath.indexOf("{");
                    if (braceIdx != -1) {
                        // e.g. 'noauth/reset/{email}' becomes 'noauth/reset/'
                        methodPath = methodPath.substring(0, braceIdx);
                    }
                    final String path = ResourcePaths.buildPath(resourcePath, methodPath);
                    unauthenticatedPaths.add(path);
                }
            }
        }
        return unauthenticatedPaths.stream();
    }

    static <T> T getFromClassOrSuper(final Class<?> clazz, final Function<Class<?>, T> getter) {
        Objects.requireNonNull(getter);

        T val = getter.apply(clazz);

        if (val == null) {
            // try each iface in turn
            val = Arrays.stream(clazz.getInterfaces())
                    .map(getter)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }
        return val;
    }

    static <T> T getFromMethodOrSuper(final Class<?> clazz,
                                      final Method method,
                                      final Function<Method, T> getter) {
        Objects.requireNonNull(getter);
        T val = getter.apply(method);
        if (val == null) {
            final Class<?> restInterface = Arrays.stream(clazz.getInterfaces())
                    .filter(iface ->
                            Arrays.asList(iface.getInterfaces()).contains(RestResource.class))
                    .findAny()
                    .orElse(null);
            if (restInterface == null) {
                return null;
            } else {
                // now find the same method on the interface
                final Optional<Method> optIfaceMethod = Arrays.stream(restInterface.getMethods())
                        .filter(ifaceMethod -> areMethodsEqual(method, ifaceMethod))
                        .findAny();

                val = optIfaceMethod.map(getter)
                        .orElse(null);
            }
        }
        return val;
    }

    private static boolean areMethodsEqual(final Method method1, final Method method2) {
        if (method1.equals(method2)) {
            return true;
        } else {
            return method1.getName().equals(method2.getName())
                   && method1.getReturnType().equals(method2.getReturnType())
                   && method1.getGenericReturnType().equals(method2.getGenericReturnType())
                   && Arrays.equals(method1.getParameterTypes(), method2.getParameterTypes())
                   && Arrays.equals(method1.getGenericParameterTypes(), method2.getGenericParameterTypes());
        }
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
            public void dispose(final T versionResource) {
            }
        }
    }


    // --------------------------------------------------------------------------------


    private record ResourceProvider(Class<?> resourceClass,
                                    Provider<RestResource> provider,
                                    String resourcePath) {

    }
}
