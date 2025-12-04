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

/*
 * This file was copied from https://github.com/dhatim/dropwizard-prometheus
 * at commit a674a1696a67186823a464383484809738665282 (v4.0.1-2)
 * and modified to work within the Stroom code base. All subsequent
 * modifications from the original are also made under the Apache 2.0 licence
 * and are subject to Crown Copyright.
 */

/*
 * Copyright 2025 github.com/dhatim
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

package stroom.dropwizard.common.prometheus;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.metrics.Metrics;
import stroom.util.shared.IsAdminServlet;
import stroom.util.shared.NullSafe;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import io.dropwizard.metrics.servlets.MetricsServlet;
import jakarta.inject.Inject;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

class PrometheusMetricsServlet extends HttpServlet implements IsAdminServlet {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PrometheusMetricsServlet.class);
    private static final String BASE_PATH_SPEC = "/prometheusMetrics";
    private static final Set<String> PATH_SPECS = Set.of(BASE_PATH_SPEC);

    public static final String METRICS_REGISTRY = MetricsServlet.class.getCanonicalName() + ".registry";
    public static final String METRIC_FILTER = MetricsServlet.class.getCanonicalName() + ".metricFilter";
    public static final String ALLOWED_ORIGIN = MetricsServlet.class.getCanonicalName() + ".allowedOrigin";
    private static final Logger LOG = LoggerFactory.getLogger(PrometheusMetricsServlet.class);

    private final AppInfoProvider appInfoProvider;
    private final MetricRegistry registry;

    private String allowedOrigin;
    private MetricFilter filter;

    @Inject
    public PrometheusMetricsServlet(final AppInfoProvider appInfoProvider,
                                    final Metrics metrics) {
        this.appInfoProvider = appInfoProvider;
        this.registry = Objects.requireNonNull(metrics.getRegistry());
    }

    @Override
    public Set<String> getPathSpecs() {
        return PATH_SPECS;
    }

    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);

        final ServletContext context = config.getServletContext();
//        if (null == registry) {
//            final Object registryAttr = context.getAttribute(METRICS_REGISTRY);
//            if (registryAttr instanceof final MetricRegistry metricRegistry) {
//                this.registry = metricRegistry;
//            } else {
//                throw new ServletException("Couldn't find a MetricRegistry instance.");
//            }
//        }

        filter = Objects.requireNonNullElse(
                (MetricFilter) context.getAttribute(METRIC_FILTER),
                MetricFilter.ALL);

        this.allowedOrigin = context.getInitParameter(ALLOWED_ORIGIN);
    }

    @Override
    protected void doGet(final HttpServletRequest req,
                         final HttpServletResponse resp) throws IOException {
        resp.setContentType(TextFormat.CONTENT_TYPE);
        if (allowedOrigin != null) {
            resp.setHeader("Access-Control-Allow-Origin", allowedOrigin);
        }
        resp.setHeader("Cache-Control", "must-revalidate,no-cache,no-store");
        resp.setStatus(HttpServletResponse.SC_OK);

        final Set<String> filtered = parse(req);
        final Predicate<String> sanitisedNameFilter = NullSafe.hasItems(filtered)
                ? filtered::contains
                : sanitisedName -> true;

        final Map<String, String> nodeLabels = appInfoProvider.getNodeLabels();

        try (final PrometheusTextWriter writer = new PrometheusTextWriter(resp.getWriter())) {
            final DropwizardMetricsExporter exporter = new DropwizardMetricsExporter(writer);

            exporter.writeAppInfo(appInfoProvider, sanitisedNameFilter);

            writeMetrics(
                    registry::getGauges,
                    sanitisedNameFilter,
                    (dropwizardName, metric) ->
                            exporter.writeGauge(dropwizardName, nodeLabels, metric));

            writeMetrics(
                    registry::getCounters,
                    sanitisedNameFilter,
                    (dropwizardName, metric) ->
                            exporter.writeCounter(dropwizardName, nodeLabels, metric));

            writeMetrics(
                    registry::getHistograms,
                    sanitisedNameFilter,
                    (dropwizardName, metric) ->
                            exporter.writeHistogram(dropwizardName, nodeLabels, metric));

            writeMetrics(
                    registry::getMeters,
                    sanitisedNameFilter,
                    (dropwizardName, metric) ->
                            exporter.writeMeter(dropwizardName, nodeLabels, metric));

            writeMetrics(
                    registry::getTimers,
                    sanitisedNameFilter,
                    (dropwizardName, metric) ->
                            exporter.writeTimer(dropwizardName, nodeLabels, metric));

            writer.flush();
        } catch (final RuntimeException ex) {
            LOG.error("Unhandled exception", ex);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private <T extends Metric> void writeMetrics(final Function<MetricFilter, Map<String, T>> getter,
                                                 final Predicate<String> sanitisedNameFilter,
                                                 final BiConsumer<String, T> metricConsumer) {

        final Map<String, T> metricsMap = getter.apply(filter);
        for (final Map.Entry<String, T> entry : metricsMap.entrySet()) {
            final String dropwizardName = entry.getKey();
            final T metric = entry.getValue();
            if (metric != null) {
                DropwizardMetricsExporter.doWithSanitisedName(dropwizardName, sanitisedName -> {
                    if (sanitisedNameFilter.test(sanitisedName)) {
                        metricConsumer.accept(dropwizardName, metric);
                    }
                });
            } else {
                LOGGER.debug("Null metric with name '{}'", dropwizardName);
            }
        }
    }

    private Set<String> parse(final HttpServletRequest req) {
        final String[] includedParam = req.getParameterValues("name[]");
        return NullSafe.asSet(includedParam);
    }
}
