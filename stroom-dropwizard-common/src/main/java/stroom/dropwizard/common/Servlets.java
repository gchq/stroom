package stroom.dropwizard.common;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import io.dropwizard.setup.Environment;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.HasHealthCheck;
import stroom.util.guice.ResourcePaths;
import stroom.util.logging.LogUtil;
import stroom.util.shared.IsServlet;
import stroom.util.shared.Unauthenticated;

import javax.inject.Inject;
import javax.servlet.Servlet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Servlets {
    private static final Logger LOGGER = LoggerFactory.getLogger(Servlets.class);

    private static final String SERVLET_PATH_KEY = "servletPath";

    private final Environment environment;
    private final Set<IsServlet> servlets;

    @Inject
    Servlets(final Environment environment, final Set<IsServlet> servlets) {
        this.environment = environment;
        this.servlets = servlets;
    }

    public void register() {
        final ServletContextHandler servletContextHandler = environment.getApplicationContext();

        // Check for duplicate servlet path specs, assumes they are globally unique
        final List<String> duplicatePaths = servlets.stream()
                .flatMap(servlet ->
                        servlet.getPathSpecs().stream())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (!duplicatePaths.isEmpty()) {
            throw new RuntimeException(LogUtil.message(
                    "Multiple servlets exist for each of the following servlet paths [{}]",
                    String.join(", ", duplicatePaths)));
        }

        LOGGER.info("Adding servlets:");
        servlets.stream()
                .sorted(Comparator.comparing(isServlet ->
                        isServlet.getClass().getSimpleName()))
                .forEach(servlet -> {
                    // A servlet may have multiple path specs
                    for (String partialPathSpec : servlet.getPathSpecs()) {
                        final String name = servlet.getClass().getSimpleName();
                        final String servletPath = Objects.requireNonNull(partialPathSpec);
                        final String fullPathSpec;
                        // Determine the full servlet path based on whether the servlet requires
                        // authentication or not
                        if (servlet.getClass().isAnnotationPresent(Unauthenticated.class)) {
                            fullPathSpec = ResourcePaths.buildUnauthenticatedServletPath(servletPath);
                        } else {
                            fullPathSpec = ResourcePaths.buildAuthenticatedServletPath(servletPath);
                        }
                        LOGGER.info("\t{} -> {}", name, fullPathSpec);
                        final ServletHolder servletHolder;
                        try {
                            servletHolder = new ServletHolder(name, (Servlet) servlet);
                        } catch (ClassCastException e) {
                            throw new RuntimeException(LogUtil.message("Injected class {} is not a Servlet",
                                    servlet.getClass().getName()));
                        }
                        servletContextHandler.addServlet(servletHolder, fullPathSpec);

                        registerHealthCheck(servlet, fullPathSpec);
                    }
                });
    }

    private void registerHealthCheck(final IsServlet servlet,
                                     final String fullPathSpec) {

        final HealthCheckRegistry healthCheckRegistry = environment.healthChecks();
        final String name = servlet.getClass().getName();

        if (servlet instanceof HasHealthCheck) {
            LOGGER.info("Adding health check for servlet {}", name);
            // object has a getHealth method so build a HealthCheck that wraps it and
            // adds in the servlet path information
            healthCheckRegistry.register(name, new HealthCheck() {
                @Override
                protected HealthCheck.Result check() {
                    // Decorate the existing health check results with the full path spec
                    HealthCheck.Result result = ((HasHealthCheck) servlet).getHealth();

                    if (result.getDetails().containsKey(SERVLET_PATH_KEY)) {
                        LOGGER.warn("Overriding health check detail for {} {} in servlet {}",
                                SERVLET_PATH_KEY,
                                result.getDetails().get(SERVLET_PATH_KEY),
                                name);
                    }
                    result.getDetails().put(SERVLET_PATH_KEY, fullPathSpec);

                    return result;
                }
            });
        } else {
            // Servlet doesn't have a health check so create a noddy one that shows the path
            healthCheckRegistry.register(name, new HealthCheck() {
                @Override
                protected Result check() {
                    return Result.builder()
                            .healthy()
                            .withDetail(SERVLET_PATH_KEY, fullPathSpec)
                            .build();
                }
            });
        }
    }
}
