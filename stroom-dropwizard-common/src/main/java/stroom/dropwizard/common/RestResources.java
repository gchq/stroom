package stroom.dropwizard.common;

import io.dropwizard.setup.Environment;
import io.vavr.Tuple;
import io.vavr.Tuple3;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.ConsoleColour;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import javax.inject.Inject;
import javax.ws.rs.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
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

        int maxNameLength = restResources.stream()
            .mapToInt(restResource -> restResource.getClass().getName().length())
            .max()
            .orElse(0);

        final Set<String> allPaths = new HashSet<>();

        restResources.stream()
                .map(restResource ->
                    Tuple.of(
                        restResource,
                        restResource.getClass().getName(),
                        getResourcePath(restResource).orElse("")))
                .sorted(Comparator.comparing(Tuple3::_3))
                .forEach(tuple3 -> {
                    final RestResource restResource = tuple3._1();
                    final String name = tuple3._2();
                    final String resourcePath = tuple3._3();

                    addServlet(maxNameLength, allPaths, restResource, name, resourcePath);
                });
    }

    private void addServlet(final int maxNameLength,
                            final Set<String> allPaths,
                            final RestResource restResource,
                            final String name,
                            final String resourcePath) {

        if (allPaths.contains(resourcePath)) {
            LOGGER.error("\t{} => {}   {}",
                StringUtils.rightPad(name, maxNameLength, " "),
                resourcePath,
                ConsoleColour.red("**Duplicate path**"));
            // TODO uncomment this once the duplicates have been fixed
            throw new RuntimeException(LogUtil.message("Duplicate REST resource path {}", resourcePath));
        } else {
            LOGGER.info("\t{} => {}",
                StringUtils.rightPad(name, maxNameLength, " "),
                resourcePath);
        }

        environment.jersey().register(restResource);
        allPaths.add(resourcePath);
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
                        ResourcePaths.buildAuthenticatedApiPath(path.value()));
    }
}
