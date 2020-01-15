package stroom.dropwizard.common;

import com.codahale.metrics.health.HealthCheckRegistry;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.HasHealthCheck;

import javax.inject.Inject;
import java.util.Comparator;
import java.util.Set;

public class HealthChecks {
    private static final Logger LOGGER = LoggerFactory.getLogger(HealthChecks.class);

    private final Environment environment;
    private final Set<HasHealthCheck> healthChecks;

    @Inject
    HealthChecks(final Environment environment,
                 final Set<HasHealthCheck> healthChecks) {
        this.environment = environment;
        this.healthChecks = healthChecks;
    }

    public void register() {
        final HealthCheckRegistry healthCheckRegistry = environment.healthChecks();
        LOGGER.info("Adding health checks:");
        healthChecks.stream()
                .sorted(Comparator.comparing(hasHealthCheck ->
                        hasHealthCheck.getClass().getSimpleName()))
                .forEach(hasHealthCheck -> {
            final String name = hasHealthCheck.getClass().getName();
            LOGGER.info("\t{}", name);
            healthCheckRegistry.register(name, hasHealthCheck.getHealthCheck());
        });
    }
}
