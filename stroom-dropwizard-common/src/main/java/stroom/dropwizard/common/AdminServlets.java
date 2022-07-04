package stroom.dropwizard.common;

import stroom.util.ConsoleColour;
import stroom.util.logging.LogUtil;
import stroom.util.shared.IsAdminServlet;

import io.dropwizard.setup.Environment;
import io.vavr.Tuple;
import io.vavr.Tuple3;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.servlet.Servlet;

public class AdminServlets {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminServlets.class);

    private static final String SERVLET_PATH_KEY = "servletPath";

    private final Environment environment;
    private final Set<IsAdminServlet> adminServlets;

    @Inject
    AdminServlets(final Environment environment, final Set<IsAdminServlet> adminServlets) {
        this.environment = environment;
        this.adminServlets = adminServlets;
    }

    public void register() {
        final ServletContextHandler servletContextHandler = environment.getAdminContext();

        // Check for duplicate servlet path specs, assumes they are globally unique
        final List<String> duplicatePaths = adminServlets.stream()
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

        LOGGER.info("Adding servlets to admin path/port:");

        final Set<String> allPaths = new HashSet<>();

        int maxNameLength = adminServlets.stream()
                .mapToInt(servlet -> servlet.getClass().getName().length())
                .max()
                .orElse(0);

        // Register all the path specs for each servlet class in pathspec order
        adminServlets.stream()
                .flatMap(servlet ->
                        servlet.getPathSpecs().stream()
                                .map(partialPathSpec -> {
                                    final String name = servlet.getClass().getName();
                                    final String servletPath = Objects.requireNonNull(partialPathSpec);
                                    return Tuple.of(servlet, name, servletPath);
                                }))
                .sorted(Comparator.comparing(Tuple3::_3))
                .forEach(tuple3 -> {
                    final IsAdminServlet isAdminServlet = tuple3._1();
                    final String name = tuple3._2();
                    final String fullPathSpec = tuple3._3();

                    addServlet(
                            servletContextHandler,
                            allPaths,
                            maxNameLength,
                            isAdminServlet,
                            name,
                            fullPathSpec);
                });
    }

    private void addServlet(final ServletContextHandler servletContextHandler,
                            final Set<String> allPaths,
                            final int maxNameLength,
                            final IsAdminServlet isAdminServlet,
                            final String name,
                            final String partialPathSpec) {

        final String contextPath = servletContextHandler.getContextPath();
        final String fullPathSpec = contextPath + partialPathSpec;
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
            servletHolder = new ServletHolder(name, (Servlet) isAdminServlet);
        } catch (ClassCastException e) {
            throw new RuntimeException(LogUtil.message("Injected class {} is not a Servlet",
                    isAdminServlet.getClass().getName()));
        }
        servletContextHandler.addServlet(servletHolder, partialPathSpec);
        allPaths.add(fullPathSpec);
    }
}
