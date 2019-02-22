package stroom.app;

import io.dropwizard.jersey.setup.JerseyEnvironment;
import stroom.util.RestResource;

import javax.inject.Inject;
import java.util.Set;

class RestResources {
    private final Set<RestResource> restResources;

    @Inject
    RestResources(final Set<RestResource> restResources) {
        this.restResources = restResources;
    }

    void register(final JerseyEnvironment jersey) {
        restResources.forEach(jersey::register);
    }
}
