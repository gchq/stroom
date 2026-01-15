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

package stroom.util.metrics;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import com.codahale.metrics.CachedGauge;
import com.codahale.metrics.Counter;
import com.codahale.metrics.DerivativeGauge;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.RatioGauge;
import com.codahale.metrics.Timer;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Dropwizard Metrics related util methods/constants.
 * <p>
 * Provides a consistent way to create/register metrics with consistent naming.
 * </p>
 */
public class MetricsUtil {

    public static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(HasMetrics.class);

    public static final Pattern METRIC_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9.-]+$");
    public static final Pattern REPLACE_PATTERN = Pattern.compile("[^a-zA-Z0-9.-]");

    private MetricsUtil() {
        // Static util stuff only
    }

    /**
     * Build a metric name from the owning class and 0-many name parts.
     * Each part will have unsuitable characters stripped and be separated
     * by a '.' character. Name parts will be appended onto the fully qualified class name.
     *
     * @param clazz
     * @param nameParts
     * @return The metric name
     */
    public static String buildName(final Class<?> clazz, final String... nameParts) {
        return MetricRegistry.name(
                Objects.requireNonNull(clazz),
                NullSafe.stream(nameParts)
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .map(part -> REPLACE_PATTERN.matcher(part).replaceAll(""))
                        .filter(Predicate.not(String::isEmpty))
                        .toArray(String[]::new));
    }

    public static String buildName(final Class<?> clazz, final List<String> nameParts) {
        if (NullSafe.hasItems(nameParts)) {
            final String[] namePartsArr = NullSafe.stream(nameParts)
                    .toArray(String[]::new);
            return buildName(clazz, namePartsArr);
        } else {
            return MetricRegistry.name(Objects.requireNonNull(clazz), (String[]) null);
        }
    }

    /**
     * Register a metric with the default metric registry
     */
    static void register(final MetricRegistry metricRegistry,
                         final String name,
                         final Metric metric) {
        Objects.requireNonNull(metricRegistry);
        Objects.requireNonNull(name);
        Objects.requireNonNull(metric);
        LOGGER.debug("Registering metric '{}' of type {}", name, getMetricType(metric));
        metricRegistry.register(name, metric);
    }

    static void clearRegistry(final MetricRegistry registry) {
        LOGGER.info("Clearing metrics registry");
        registry.getNames()
                .forEach(registry::remove);
    }

    static String getMetricType(final Metric metric) {
        // Metrics are often lambdas so this makes it easier to see in the logs
        // what flavour of metric is being registered
        return switch (metric) {
            case null -> null;
            case final MetricRegistry ignored -> MetricRegistry.class.getSimpleName();
            case final Histogram ignored -> Histogram.class.getSimpleName();
            case final Counter ignored -> Counter.class.getSimpleName();
            case final Meter ignored -> Meter.class.getSimpleName();
            case final Timer ignored -> Timer.class.getSimpleName();
            case final RatioGauge ignored -> RatioGauge.class.getSimpleName();
            case final CachedGauge<?> ignored -> CachedGauge.class.getSimpleName();
            case final DerivativeGauge<?, ?> ignored -> DerivativeGauge.class.getSimpleName();
            case final Gauge<?> ignored -> Gauge.class.getSimpleName();
            case final MetricSet ignored -> MetricSet.class.getSimpleName();
            default -> metric.getClass().getSimpleName();
        };
    }

    // --------------------------------------------------------------------------------


    public record NamedMetric(String name, Metric metric) {

    }
}
