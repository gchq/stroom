package stroom.dropwizard.common;

import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.shared.RestResource;

import javax.inject.Inject;
import java.util.Comparator;
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
                    LOGGER.info("\t{}", name);
                    environment.jersey().register(restResource);
                });
    }
}
