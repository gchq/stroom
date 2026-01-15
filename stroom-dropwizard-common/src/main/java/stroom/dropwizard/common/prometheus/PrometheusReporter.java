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

import stroom.util.exception.ThrowingBiConsumer;
import stroom.util.logging.LogUtil;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public class PrometheusReporter extends ScheduledReporter {

    /**
     * A builder for {@link PrometheusReporter} instances. Defaults to not using a prefix, and
     * not filtering metrics.
     */

    public static class Builder {

        private final MetricRegistry registry;
        private String prefix;
        private MetricFilter filter;
        private ScheduledExecutorService executor;
        private boolean shutdownExecutorOnStop;

        private Builder(final MetricRegistry registry) {
            this.registry = registry;
            this.prefix = null;
            this.filter = MetricFilter.ALL;
            this.executor = null;
            this.shutdownExecutorOnStop = true;
        }

        /**
         * Specifies whether or not, the executor (used for reporting) will be stopped with same time with reporter.
         * Default value is true.
         * Setting this parameter to false, has the sense in combining with providing external
         * managed executor via {@link #scheduleOn(ScheduledExecutorService)}.
         *
         * @param shutdownExecutorOnStop if true, then executor will be stopped in same time with this reporter
         * @return {@code this}
         */
        public Builder shutdownExecutorOnStop(final boolean shutdownExecutorOnStop) {
            this.shutdownExecutorOnStop = shutdownExecutorOnStop;
            return this;
        }

        /**
         * Specifies the executor to use while scheduling reporting of metrics.
         * Default value is null.
         * Null value leads to executor will be auto created on start.
         *
         * @param executor the executor to use while scheduling reporting of metrics.
         * @return {@code this}
         */
        public Builder scheduleOn(final ScheduledExecutorService executor) {
            this.executor = executor;
            return this;
        }

        /**
         * Prefix all metric names with the given string.
         *
         * @param prefix the prefix for all metric names
         * @return {@code this}
         */
        public Builder prefixedWith(final String prefix) {
            this.prefix = prefix;
            return this;
        }

        /**
         * Only report metrics which match the given filter.
         *
         * @param filter a {@link MetricFilter}
         * @return {@code this}
         */
        public Builder filter(final MetricFilter filter) {
            this.filter = filter;
            return this;
        }

        /**
         * Builds a {@link PrometheusReporter} with the given properties, sending metrics using the
         * given {@link PrometheusSender}.
         * <p>
         * Present for binary compatibility
         *
         * @param prometheus a {@link PushGateway}
         * @return a {@link PrometheusReporter}
         */
        public PrometheusReporter build(final PushGateway prometheus) {
            return build((PrometheusSender) prometheus);
        }

        /**
         * Builds a {@link PrometheusReporter} with the given properties, sending metrics using the
         * given {@link PrometheusSender}.
         *
         * @param prometheus a {@link PrometheusSender}
         * @return a {@link PrometheusReporter}
         */
        public PrometheusReporter build(final PrometheusSender prometheus) {
            return new PrometheusReporter(registry,
                    prometheus,
                    prefix,
                    filter,
                    executor,
                    shutdownExecutorOnStop);
        }

    }

    private static final TimeUnit DURATION_UNIT = TimeUnit.MILLISECONDS;
    private static final TimeUnit RATE_UNIT = TimeUnit.SECONDS;

    private static final Logger LOGGER = LoggerFactory.getLogger(PrometheusReporter.class);

    private final PrometheusSender prometheus;
    private final String prefix;

    /**
     * Creates a new {@link PrometheusReporter} instance.
     *
     * @param registry               the {@link MetricRegistry} containing the metrics this
     *                               reporter will report
     * @param prometheus             the {@link PrometheusSender} which is responsible for sending metrics to a
     *                               Prometheus server.
     * @param prefix                 the prefix of all metric names (may be null)
     * @param filter                 the filter for which metrics to report
     * @param executor               the executor to use while scheduling reporting of metrics (may be null).
     * @param shutdownExecutorOnStop if true, then executor will be stopped in same time with this reporter
     */
    protected PrometheusReporter(final MetricRegistry registry,
                                 final PrometheusSender prometheus,
                                 final String prefix,
                                 final MetricFilter filter,
                                 final ScheduledExecutorService executor,
                                 final boolean shutdownExecutorOnStop) {
        super(registry,
                "prometheus-reporter",
                filter,
                RATE_UNIT,
                DURATION_UNIT,
                executor,
                shutdownExecutorOnStop,
                Collections.emptySet());
        this.prometheus = prometheus;
        this.prefix = prefix;
    }

    @Override
    public void stop() {
        try {
            super.stop();
        } finally {
            try {
                prometheus.close();
            } catch (final IOException e) {
                LOGGER.debug("Error disconnecting from Prometheus {}", prometheus, e);
            }
        }
    }

    @Override
    public void report(final SortedMap<String, Gauge> gauges,
                       final SortedMap<String, Counter> counters,
                       final SortedMap<String, Histogram> histograms,
                       final SortedMap<String, Meter> meters,
                       final SortedMap<String, Timer> timers) {
        try {
            if (!prometheus.isConnected()) {
                prometheus.connect();
            }

            prometheus.sendAppInfo();
            sendMetrics(gauges, ThrowingBiConsumer.unchecked(prometheus::sendGauge));
            sendMetrics(counters, ThrowingBiConsumer.unchecked(prometheus::sendCounter));
            sendMetrics(histograms, ThrowingBiConsumer.unchecked(prometheus::sendHistogram));
            sendMetrics(meters, ThrowingBiConsumer.unchecked(prometheus::sendMeter));
            sendMetrics(timers, ThrowingBiConsumer.unchecked(prometheus::sendTimer));

            prometheus.flush();
        } catch (final IOException e) {
            LOGGER.warn("Unable to report to Prometheus {}", prometheus, e);
        } finally {
            try {
                prometheus.close();
            } catch (final IOException e) {
                LOGGER.error("Error while closing connection: {}", LogUtil.exceptionMessage(e), e);
            }
        }
    }

    private <T extends Metric> void sendMetrics(final Map<String, T> metrics,
                                                final BiConsumer<String, T> sendFunction) {
        for (final Map.Entry<String, T> entry : metrics.entrySet()) {
            final String dropWizardName = entry.getKey();
            final T metric = entry.getValue();
            if (metric != null) {
                sendFunction.accept(prefixed(dropWizardName), metric);
            } else {
                LOGGER.debug("Null metric with name '{}'", dropWizardName);
            }
        }

    }

    private String prefixed(final String name) {
        return prefix == null
                ? name
                : (prefix + name);
    }

    /**
     * Returns a new {@link Builder} for {@link PrometheusReporter}.
     *
     * @param registry the registry to report
     * @return a {@link Builder} instance for a {@link PrometheusReporter}
     */
    public static Builder forRegistry(final MetricRegistry registry) {
        return new Builder(registry);
    }
}
