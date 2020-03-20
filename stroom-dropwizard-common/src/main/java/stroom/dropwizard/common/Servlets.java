package stroom.dropwizard.common;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import io.dropwizard.setup.Environment;
import io.vavr.Tuple;
import io.vavr.Tuple3;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.ConsoleColour;
import stroom.util.HasHealthCheck;
import stroom.util.logging.LogUtil;
import stroom.util.shared.IsServlet;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.Unauthenticated;

import javax.inject.Inject;
import javax.servlet.Servlet;
import java.util.Comparator;
import java.util.HashSet;
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

        final Set<String> allPaths = new HashSet<>();

        int maxNameLength = servlets.stream()
            .mapToInt(servlet -> servlet.getClass().getName().length())
            .max()
            .orElse(0);

        // Register all the path specs for each servlet class in pathspec order
        servlets.stream()
            .flatMap(servlet ->
                servlet.getPathSpecs().stream()
                .map(partialPathSpec -> {
                    final String name = servlet.getClass().getName();
                    final String servletPath = Objects.requireNonNull(partialPathSpec);
                    final String fullPathSpec;
                    // Determine the full servlet path based on whether the servlet requires
                    // authentication or not
                    if (servlet.getClass().isAnnotationPresent(Unauthenticated.class)) {
                        fullPathSpec = ResourcePaths.buildUnauthenticatedServletPath(servletPath);
                    } else {
                        fullPathSpec = ResourcePaths.buildAuthenticatedServletPath(servletPath);
                    }
                    return Tuple.of(servlet, name, fullPathSpec);
                }))
            .sorted(Comparator.comparing(Tuple3::_3))
            .forEach(tuple3 -> {
                final IsServlet isServlet = tuple3._1();
                final String name = tuple3._2();
                final String fullPathSpec = tuple3._3();

                addServlet(servletContextHandler, allPaths, maxNameLength, isServlet, name, fullPathSpec);
            });
    }

    private void addServlet(final ServletContextHandler servletContextHandler,
                            final Set<String> allPaths,
                            final int maxNameLength,
                            final IsServlet isServlet,
                            final String name,
                            final String fullPathSpec) {

        if (allPaths.contains(fullPathSpec)) {
            LOGGER.error("\t{} => {}   {}",
                StringUtils.rightPad(name, maxNameLength, " "),
                fullPathSpec,
                ConsoleColour.red("**Duplicate path**"));
            throw new RuntimeException(LogUtil.message("Duplicate servlet path {}", fullPathSpec));
        } else {
            LOGGER.info("\t{} => {}",
                StringUtils.rightPad(name, maxNameLength, " "),
                fullPathSpec);
        }

        final ServletHolder servletHolder;
        try {
            servletHolder = new ServletHolder(name, (Servlet) isServlet);
        } catch (ClassCastException e) {
            throw new RuntimeException(LogUtil.message("Injected class {} is not a Servlet",
                isServlet.getClass().getName()));
        }
        servletContextHandler.addServlet(servletHolder, fullPathSpec);
        allPaths.add(fullPathSpec);

        registerHealthCheck(isServlet, fullPathSpec);
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
                    // as the servlet doesn't know its own full path
                    HealthCheck.Result result = ((HasHealthCheck) servlet).getHealth();

                    HealthCheck.ResultBuilder builder = Result.builder();
                    if (result.isHealthy()) {
                        builder
                                .healthy()
                                .withMessage(result.getMessage())
                                .withDetail(SERVLET_PATH_KEY, fullPathSpec)
                                .build();
                    } else {
                        builder
                                .unhealthy(result.getError())
                                .withMessage(result.getMessage())
                                .withDetail(SERVLET_PATH_KEY, fullPathSpec)
                                .build();
                    }
                    builder
                            .withMessage(result.getMessage())
                            .withDetail(SERVLET_PATH_KEY, fullPathSpec);

                    if (result.getDetails() != null) {
                        if (result.getDetails().containsKey(SERVLET_PATH_KEY)) {
                            LOGGER.warn("Overriding health check detail for {} {} in servlet {}",
                                    SERVLET_PATH_KEY,
                                    result.getDetails().get(SERVLET_PATH_KEY),
                                    name);
                        }
                        result.getDetails().forEach(builder::withDetail);
                    }

                    result = builder.build();

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
