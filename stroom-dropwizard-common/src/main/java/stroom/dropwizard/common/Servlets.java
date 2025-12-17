/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.dropwizard.common;

import stroom.util.ConsoleColour;
import stroom.util.HasHealthCheck;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.IsServlet;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.Unauthenticated;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import io.dropwizard.core.setup.Environment;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.servlet.Servlet;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
public class Servlets {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Servlets.class);

    private static final String SERVLET_PATH_KEY = "servletPath";

    private final Environment environment;
    private final Set<IsServlet> servlets;
    private final AuthenticationBypassCheckerImpl authenticationBypassCheckerImpl;

    @Inject
    Servlets(final Environment environment,
             final Set<IsServlet> servlets,
             final AuthenticationBypassCheckerImpl authenticationBypassCheckerImpl) {
        this.environment = environment;
        this.servlets = servlets;
        this.authenticationBypassCheckerImpl = authenticationBypassCheckerImpl;
    }

    public void register() {
        final ServletContextHandler servletContextHandler = environment.getApplicationContext();

        validateServlets();

        LOGGER.info("Adding servlets to application path/port:");
        final Set<String> allPaths = new HashSet<>();
        final int maxNameLength = servlets.stream()
                .mapToInt(servlet -> servlet.getClass().getName().length())
                .max()
                .orElse(0);

        // Register all the path specs for each servlet class in pathspec order
        servlets.stream()
                .flatMap(servlet ->
                        servlet.getPathSpecs().stream()
                                .map(partialPathSpec -> {
//                                    final String name = servlet.getClass().getName();
                                    final String servletName = servlet.getName();
                                    Objects.requireNonNull(partialPathSpec);
                                    final String fullPathSpec = ResourcePaths.buildServletPath(partialPathSpec);
                                    return new ServletInfo(servlet, servletName, fullPathSpec);
                                }))
                .sorted(Comparator.comparing(ServletInfo::fullPathSpec))
                .forEach(servletInfo -> {
                    addServlet(
                            servletContextHandler,
                            allPaths,
                            maxNameLength,
                            servletInfo.servlet,
                            servletInfo.servletName,
                            servletInfo.fullPathSpec);
                });
    }

    private void validateServlets() {
        // Check for duplicate servlet path specs, assumes they are globally unique
        final List<String> duplicatePaths = servlets.stream()
                .flatMap(servlet ->
                        servlet.getPathSpecs().stream())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .toList();

        if (!duplicatePaths.isEmpty()) {
            throw new RuntimeException(LogUtil.message(
                    "Multiple servlets exist for each of the following servlet paths [{}]",
                    String.join(", ", duplicatePaths)));
        }

        final List<String> duplicateNames = servlets.stream()
                .map(IsServlet::getName)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .toList();

        if (!duplicateNames.isEmpty()) {
            throw new RuntimeException(LogUtil.message(
                    "Multiple servlets exist for each of the following names [{}]. Servlets must have unique names",
                    String.join(", ", duplicateNames)));
        }
    }

    private void addServlet(final ServletContextHandler servletContextHandler,
                            final Set<String> allPaths,
                            final int maxNameLength,
                            final IsServlet aServlet,
                            final String servletName,
                            final String fullPathSpec) {


        if (allPaths.contains(fullPathSpec)) {
            LOGGER.error("\t{} => {}   {}",
                    StringUtils.rightPad(servletName, maxNameLength, " "),
                    fullPathSpec,
                    ConsoleColour.red("**Duplicate path**"));
            throw new RuntimeException(LogUtil.message("Duplicate servlet path {}", fullPathSpec));
        } else {
            final boolean isUnauthenticated;
            if (aServlet.getClass().isAnnotationPresent(Unauthenticated.class)) {
                isUnauthenticated = true;
                authenticationBypassCheckerImpl.registerUnauthenticatedServletName(servletName);
            } else {
                isUnauthenticated = false;
            }

            // datafeed is a special case. It does bypass auth, but uses its own auth mechanism,
            // so we don't want to give impression it has no auth in the logs
            final String suffix = isUnauthenticated && !fullPathSpec.contains("datafeed")
                    ? " (Unauthenticated)"
                    : "";

            LOGGER.info("\t{} => {}{}",
                    StringUtils.rightPad(servletName, maxNameLength, " "),
                    fullPathSpec,
                    suffix);

            final ServletHolder servletHolder;
            if (aServlet instanceof final Servlet servlet) {
                servletHolder = new ServletHolder(servletName, servlet);
            } else {
                throw new RuntimeException(LogUtil.message("Injected class {} is not a Servlet",
                        LogUtil.getClassName(aServlet)));
            }

            servletContextHandler.addServlet(servletHolder, fullPathSpec);
            allPaths.add(fullPathSpec);

            registerHealthCheck(aServlet, fullPathSpec);
        }
    }

    private void registerHealthCheck(final IsServlet servlet,
                                     final String fullPathSpec) {

        final HealthCheckRegistry healthCheckRegistry = environment.healthChecks();
        final String name = servlet.getClass().getName();

        if (servlet instanceof final HasHealthCheck hasHealthCheck) {
            LOGGER.info("Adding health check for servlet {}", name);
            // object has a getHealth method so build a HealthCheck that wraps it and
            // adds in the servlet path information
            healthCheckRegistry.register(name, new HealthCheck() {
                @Override
                protected HealthCheck.Result check() {
                    // Decorate the existing health check results with the full path spec
                    // as the servlet doesn't know its own full path
                    final HealthCheck.ResultBuilder builder = Result.builder();
                    Result result = null;
                    try {
                        result = hasHealthCheck.getHealth();
                        if (result.isHealthy()) {
                            builder.healthy();
                        } else {
                            builder.unhealthy(result.getError());
                        }
                        builder.withMessage(result.getMessage());
                        if (result.getDetails() != null) {
                            if (result.getDetails().containsKey(SERVLET_PATH_KEY)) {
                                LOGGER.warn("Overriding health check detail for {} {} in servlet {}",
                                        SERVLET_PATH_KEY,
                                        result.getDetails().get(SERVLET_PATH_KEY),
                                        name);
                            }
                            result.getDetails()
                                    .forEach(builder::withDetail);
                        }
                    } catch (final Exception e) {
                        builder.unhealthy(e);
                        LOGGER.error("Error getting health for servlet {}: {}",
                                servlet.getClass().getSimpleName(), LogUtil.exceptionMessage(e), e);
                    }

                    builder.withDetail(SERVLET_PATH_KEY, fullPathSpec);
                    result = builder.build();

                    return result;
                }
            });
        } else {
            // Servlet doesn't have a health check
        }
    }


    // --------------------------------------------------------------------------------


    private record ServletInfo(
            IsServlet servlet,
            String servletName,
            String fullPathSpec) {

    }
}
