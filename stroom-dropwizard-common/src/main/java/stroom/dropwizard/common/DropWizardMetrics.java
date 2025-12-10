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

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.metrics.HasMetrics;
import stroom.util.metrics.MetricsUtil;
import stroom.util.metrics.MetricsUtil.NamedMetric;
import stroom.util.shared.NullSafe;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Metered;
import com.codahale.metrics.Metric;
import io.dropwizard.core.setup.Environment;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Singleton
public class DropWizardMetrics {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DropWizardMetrics.class);

    private final Environment environment;
    private final Set<HasMetrics> hasMetricsSet;

    @Inject
    public DropWizardMetrics(final Environment environment,
                             final Set<HasMetrics> hasMetricsSet) {
        this.environment = environment;
        this.hasMetricsSet = hasMetricsSet;
//        registerMetricConsumers(hasMetricsSet);
    }

//    private void registerMetricConsumers(final Set<HasMetrics> hasMetricsSet) {
//        for (final HasMetrics hasMetrics : NullSafe.set(hasMetricsSet)) {
//            // Allow HasMetrics to tell us about metrics
//            hasMetrics.registerAdditionalMetricConsumer(additionalMetric -> {
//
//                if (additionalMetric != null) {
//                    if (!HasMetrics.METRIC_NAME_PATTERN.matcher(additionalMetric.name()).matches()) {
//                        throw new RuntimeException(LogUtil.message("Metric [{}] from {} does not match pattern {}",
//                                additionalMetric.name(),
//                                hasMetrics.getClass().getName(),
//                                HasMetrics.METRIC_NAME_PATTERN));
//                    }
//                    LOGGER.info("Registering additional metric '{}' {}",
//                            additionalMetric.name(), getMetricType(additionalMetric.metric()));
//                    registerMetric(additionalMetric);
//                }
//            });
//        }
//    }

    public void register() {
        final List<NamedMetric> namedMetrics = NullSafe.stream(hasMetricsSet)
                .filter(Objects::nonNull)
                .map(this::getMetrics)
                .filter(Objects::nonNull)
                .flatMap(map -> map.entrySet().stream())
                .filter(Objects::nonNull)
                .map(entry -> new NamedMetric(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(MetricsUtil.NamedMetric::name, String::compareToIgnoreCase))
                .toList();

        final int maxNameLength = namedMetrics.stream()
                .mapToInt(namedMetric -> namedMetric.name().length())
                .max()
                .orElse(0);

        LOGGER.info("Registering metrics");
        namedMetrics.forEach(namedMetric -> {
            LOGGER.info("\t{} of type {}",
                    StringUtils.rightPad(namedMetric.name(), maxNameLength, " "),
                    getMetricType(namedMetric.metric()));

            registerMetric(namedMetric);
        });
    }

    private Map<String, Metric> getMetrics(final HasMetrics hasMetrics) {
        final Map<String, Metric> metrics = hasMetrics.getMetrics();

        if (NullSafe.hasEntries(metrics)) {
            final Predicate<String> invalidNamePredicate = Predicate.not(
                    MetricsUtil.METRIC_NAME_PATTERN.asPredicate());

            final String invalidNames = metrics.keySet()
                    .stream()
                    .filter(invalidNamePredicate)
                    .map(name -> "'" + name + "'")
                    .collect(Collectors.joining(", "));
            if (!invalidNames.isEmpty()) {
                throw new RuntimeException(LogUtil.message("Metrics [{}] from {} do not match pattern {}",
                        invalidNames, hasMetrics.getClass().getName(), MetricsUtil.METRIC_NAME_PATTERN));
            }
        }
        return metrics;
    }

    private void registerMetric(final NamedMetric namedMetric) {
        environment.metrics()
                .register(namedMetric.name(), namedMetric.metric());
    }

    private String getMetricType(final Metric metric) {
        if (metric == null) {
            return null;
        } else {
            return switch (metric) {
                case final Histogram val -> Histogram.class.getSimpleName();
                case final Counter val -> Counter.class.getSimpleName();
                case final Metered val -> Metered.class.getSimpleName();
                case final Gauge<?> val -> Gauge.class.getSimpleName();
                default -> metric.getClass().getSimpleName();
            };
        }
    }
}
