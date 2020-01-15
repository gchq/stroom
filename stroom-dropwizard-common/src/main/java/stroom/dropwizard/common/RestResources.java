package stroom.dropwizard.common;

import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.guice.ResourcePaths;
import stroom.util.shared.RestResource;

import javax.inject.Inject;
import javax.ws.rs.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class RestResources {
    private static final Logger LOGGER = LoggerFactory.getLogger(RestResources.class);

    private final Environment environment;
    private final Set<RestResource> restResources;

    @Inject
    RestResources(final Environment environment, final Set<RestResource> restResources) {
        this.environment = environment;
        this.restResources = restResources;
    }

    public void register() {
        LOGGER.info("Adding REST resources:");
        restResources.stream()
                .sorted(Comparator.comparing(restResource -> restResource.getClass().getName()))
                .forEach(restResource -> {
                    final String name = restResource.getClass().getName();
                    LOGGER.info("\t{} => {}", name, getResourcePath(restResource).orElse(""));
                    environment.jersey().register(restResource);
                });
    }

    private Optional<String> getResourcePath(final RestResource restResource) {
        final Path pathAnnotation = restResource.getClass().getAnnotation(Path.class);
        return Optional.ofNullable(pathAnnotation)
                .or(() ->
                        // No Path annotation on the RestResource so look for it in all interfaces
                        Arrays.stream(restResource.getClass().getInterfaces())
                                .map(clazz -> clazz.getAnnotation(Path.class))
                                .filter(Objects::nonNull)
                                .findFirst())
                .map(path ->
                        ResourcePaths.API_PATH + path.value());
    }
}
