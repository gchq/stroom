package stroom.dropwizard.common;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.HasHealthCheck;
import stroom.util.guice.ServletInfo;

import javax.inject.Inject;
import javax.servlet.Servlet;
import java.util.Map;
import java.util.Set;

public class HealthChecks {
    private static final Logger LOGGER = LoggerFactory.getLogger(HealthChecks.class);
    private static final String HEALTH_CHECK_SUFFIX = "HealthCheck";

    private final Environment environment;
    private final Set<HasHealthCheck> healthChecks;
    private final Map<ServletInfo, Servlet> servlets;

    @Inject
    HealthChecks(final Environment environment,
                 final Set<HasHealthCheck> healthChecks,
                 final Map<ServletInfo, Servlet> servlets) {
        this.environment = environment;
        this.healthChecks = healthChecks;
        this.servlets = servlets;
    }

    public void register() {
        final HealthCheckRegistry healthCheckRegistry = environment.healthChecks();
        LOGGER.info("Adding health checks:");
        healthChecks.forEach(hasHealthCheck -> {
            final String name = hasHealthCheck.getClass().getName() + " - " + HEALTH_CHECK_SUFFIX;
            LOGGER.info("\t{}", name);
            healthCheckRegistry.register(name, hasHealthCheck.getHealthCheck());
        });

        // Add health checks for servlets.
        servlets.forEach((path, servlet) -> {
            final String name = servlet.getClass().getName();
            final String url = path.getUrl();
            LOGGER.info("\t{}", name);
            if (servlet instanceof HasHealthCheck) {
                // object has a getHealth method so build a HealthCheck that wraps it and
                // adds in the servlet path information
                healthCheckRegistry.register(name, new HealthCheck() {
                    @Override
                    protected Result check() {
                        HealthCheck.Result result = ((HasHealthCheck) servlet).getHealth();

                        HealthCheck.ResultBuilder resultBuilder = HealthCheck.Result.builder();
                        if (result.getDetails() != null) {
                            result.getDetails().forEach(resultBuilder::withDetail);
                            resultBuilder.withDetail("path", url);
                        }
                        if (result.getMessage() != null) {
                            resultBuilder.withMessage(result.getMessage());
                        }
                        if (result.getError() != null) {
                            resultBuilder.unhealthy(result.getError());
                        } else {
                            if (result.isHealthy()) {
                                resultBuilder.healthy();
                            } else {
                                resultBuilder.unhealthy();
                            }
                        }
                        return resultBuilder.build();
                    }
                });
            } else {
                // Servlet doesn't have a health check so create a noddy one that shows the path
                healthCheckRegistry.register(name, new HealthCheck() {
                    @Override
                    protected Result check() {
                        return Result.builder()
                                .healthy()
                                .withDetail("path", url)
                                .build();
                    }
                });
            }
        });
    }
}
