package stroom.startup;

import com.codahale.metrics.health.HealthCheckRegistry;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.HasHealthCheck;

import javax.inject.Inject;
import java.util.Set;

class HealthChecks {
    private static final Logger LOGGER = LoggerFactory.getLogger(HealthChecks.class);
    private static final String HEALTH_CHECK_SUFFIX = "HealthCheck";

    private final Environment environment;
    private final Set<HasHealthCheck> healthChecks;

    @Inject
    HealthChecks(final Environment environment, final Set<HasHealthCheck> healthChecks) {
        this.environment = environment;
        this.healthChecks = healthChecks;
    }

    void register() {
        final HealthCheckRegistry healthCheckRegistry = environment.healthChecks();
        LOGGER.info("Adding health checks:");
        healthChecks.forEach(hasHealthCheck -> {
            final String name = hasHealthCheck.getClass().getName() + HEALTH_CHECK_SUFFIX;
            LOGGER.info("\t{}", name);
            healthCheckRegistry.register(name, hasHealthCheck.getHealthCheck());
        });
    }
}
