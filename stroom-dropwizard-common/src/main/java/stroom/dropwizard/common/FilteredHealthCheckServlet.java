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

import stroom.util.shared.IsAdminServlet;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheck.Result;
import com.codahale.metrics.health.HealthCheckFilter;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.codahale.metrics.json.HealthCheckModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Strings;
import jakarta.inject.Inject;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.stream.Collectors;

/**
 * A Servlet that allows filtering of the registered health checks using URL param allow and deny lists.
 * The allow and deny lists are comma delimted lists of health check names.
 * You can also supply ?minimal=true to only return the overall health + status without all the detail.
 * <p>
 * It is critical that this servlet is initialised AFTER {@link HealthChecks#register()} is called
 * so that the health check registry is all set up.
 * <p>
 * Some of the code in this class is copied from com.codahale.metrics.servlets.HealthCheckServlet
 * which is also licenced under Apache 2.0.
 */
public class FilteredHealthCheckServlet extends HttpServlet implements IsAdminServlet {

    private static final String BASE_PATH_SPEC = "/filteredhealthcheck";
    private static final Set<String> PATH_SPECS = Set.of(
            BASE_PATH_SPEC
    );

    private static final String PARAM_NAME_ALLOW_LIST = "allow";
    private static final String PARAM_NAME_DENY_LIST = "deny";
    private static final String PARAM_NAME_MINIMAL = "minimal";
    private static final String PARAM_NAME_PRETTY = "pretty";
    private static final String CONTENT_TYPE = "application/json";

    private final HealthCheckRegistry healthCheckRegistry;
    private final ObjectMapper objectMapper;

    @Inject
    public FilteredHealthCheckServlet(final HealthCheckRegistry healthCheckRegistry) {
        this.healthCheckRegistry = healthCheckRegistry;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new HealthCheckModule());
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException {

        final SortedMap<String, Result> results = runHealthChecks(req);

        resp.setContentType(CONTENT_TYPE);
        resp.setHeader("Cache-Control", "must-revalidate,no-cache,no-store");
        final boolean areAllHealthy = areAllHealthy(results);
        if (results.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
        } else {
            if (areAllHealthy) {
                resp.setStatus(HttpServletResponse.SC_OK);
            } else {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }

        try (final OutputStream output = resp.getOutputStream()) {
            if (isMinimalOutput(req)) {
                final Map<String, Boolean> minimalResults = Map.of("healthy", areAllHealthy);
                // Output a minimal form
                // { "healthy": true }
                // or
                // { "healthy": false }"
                getWriter(req).writeValue(output, minimalResults);
            } else {
                getWriter(req).writeValue(output, results);
            }
        }
    }

    private boolean isMinimalOutput(final HttpServletRequest request) {
        return Boolean.parseBoolean(request.getParameter(PARAM_NAME_MINIMAL));
    }

    private ObjectWriter getWriter(final HttpServletRequest request) {
        final boolean prettyPrint = Boolean.parseBoolean(request.getParameter(PARAM_NAME_PRETTY));
        if (prettyPrint) {
            return objectMapper.writerWithDefaultPrettyPrinter();
        }
        return objectMapper.writer();
    }

    private SortedMap<String, HealthCheck.Result> runHealthChecks(final HttpServletRequest request) {
        final HealthCheckFilter healthCheckFilter = buildHealthCheckFilter(request);

        // We could consider running the healthchecks in parallel by providing an executorService
        // to the overloaded form of this method.
        // The existing healthcheck doesn't use an executor service so this is at least no
        // worse.
        return healthCheckRegistry.runHealthChecks(healthCheckFilter);
    }

    private static boolean areAllHealthy(final Map<String, Result> results) {
        return results.values()
                .stream()
                .allMatch(Result::isHealthy);
    }

    private HealthCheckFilter buildHealthCheckFilter(final HttpServletRequest request) {
        final String allowListParamVal = request.getParameter(PARAM_NAME_ALLOW_LIST);
        final String denyListParamVal = request.getParameter(PARAM_NAME_DENY_LIST);

        final HealthCheckFilter healthCheckFilter;
        if (Strings.isNullOrEmpty(allowListParamVal) && Strings.isNullOrEmpty(denyListParamVal)) {
            healthCheckFilter = HealthCheckFilter.ALL;
        } else {
            final Set<String> allowSet = allowListParamVal == null || allowListParamVal.isBlank()
                    ? Collections.emptySet()
                    : Arrays.stream(allowListParamVal.split(","))
                            .collect(Collectors.toSet());
            final Set<String> denySet = denyListParamVal == null || denyListParamVal.isBlank()
                    ? Collections.emptySet()
                    : Arrays.stream(denyListParamVal.split(","))
                            .collect(Collectors.toSet());

            validateName(allowSet, PARAM_NAME_ALLOW_LIST);
            validateName(denySet, PARAM_NAME_DENY_LIST);

            healthCheckFilter = (name, healthCheck) -> {
                if (allowSet.isEmpty() && !denySet.contains(name)) {
                    // No allow list so allow all but then check deny list
                    return true;
                } else {
                    return allowSet.contains(name) && !denySet.contains(name);
                }
            };
        }
        return healthCheckFilter;
    }

    private void validateName(final Set<String> names,
                              final String paramName) {
        final SortedSet<String> allNames = healthCheckRegistry.getNames();
        if (!allNames.containsAll(names)) {
            for (final String allowName : names) {
                if (!allNames.contains(allowName)) {
                    throw new RuntimeException(
                            "Name '" + allowName
                                    + "' is not a valid health check name for parameter '"
                                    + paramName + "'.");
                }
            }
        }
    }

    @Override
    public Set<String> getPathSpecs() {
        return PATH_SPECS;
    }
}
