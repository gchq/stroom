package stroom.dropwizard.common;

import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Set;

public class ManagedServices {
    private static final Logger LOGGER = LoggerFactory.getLogger(ManagedServices.class);

    private final Environment environment;
    private final Set<Managed> managedServices;

    @Inject
    ManagedServices(final Environment environment, final Set<Managed> managedServices) {
        this.environment = environment;
        this.managedServices = managedServices;
    }

    public void register() {
        LOGGER.info("Adding managed services:");
        managedServices.forEach(managed -> {
            final String name = managed.getClass().getName();
            LOGGER.info("\t{}", name);
            environment.lifecycle().manage(managed);
        });
    }
}
