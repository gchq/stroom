package stroom.app;

import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.RestResource;

import javax.inject.Inject;
import java.util.Set;

class RestResources {
    private static final Logger LOGGER = LoggerFactory.getLogger(RestResources.class);

    private final Environment environment;
    private final Set<RestResource> restResources;

    @Inject
    RestResources(final Environment environment, final Set<RestResource> restResources) {
        this.environment = environment;
        this.restResources = restResources;
    }

    void register() {
        final JerseyEnvironment jersey = environment.jersey();

        LOGGER.info("Adding rest resources");
        for (RestResource restResource : restResources) {
            final String name = restResource.getClass().getName();
            LOGGER.info("\t{}", name);
            jersey.register(restResource);
        }
    }
}
