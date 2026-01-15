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

import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metered;
import com.codahale.metrics.Metric;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.google.common.base.CaseFormat;
import com.google.common.base.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class DropwizardMetricsExporter {

    public static final Converter<String, String> CASE_CONVERTER = CaseFormat.UPPER_CAMEL.converterTo(
            CaseFormat.LOWER_UNDERSCORE);
    private static final Logger LOGGER = LoggerFactory.getLogger(DropwizardMetricsExporter.class);
    private static final Map<String, Boolean> METRICS_WITH_INVALID_TYPES = new ConcurrentHashMap<>();
    private static final Map<String, Optional<String>> NAME_TO_SANITISED_NAME_MAP = new ConcurrentHashMap<>();
    private static final Pattern NAME_SANITISE_PATTERN = Pattern.compile("[^a-zA-Z0-9:_]");
    private static final Pattern MULTIPLE_UNDERSCORE_PATTERN = Pattern.compile("_+");
    private static final Pattern MULTIPLE_UPPERCASE_CHARS_PATTERN = Pattern.compile("[A-Z]{2,}");
    private static final String APP_INFO_METRIC_NAME = "app_info";
    // Value is always 1 as the metric is just a means to provide the labels.
    // Prometheus cannot handle string values at all.
    private static final Gauge<Double> APP_INFO_GAUGE = () -> 1d;
    private static final double APP_INFO_VALUE = APP_INFO_GAUGE.getValue();
    private static final String APP_INFO_HELP = getHelpMessage(APP_INFO_METRIC_NAME, MetricType.GAUGE, APP_INFO_GAUGE);
    // Prometheus reserved suffixes
    private static final String PROMETHEUS_TOTAL_SUFFIX = "_total";
    private static final String PROMETHEUS_SUM_SUFFIX = "_sum";
    private static final String PROMETHEUS_COUNT_SUFFIX = "_count";
    private static final String PROMETHEUS_BUCKET_SUFFIX = "_bucket";
    private static final Set<String> PROMETHEUS_RESERVED_SUFFIXES = Set.of(
            PROMETHEUS_TOTAL_SUFFIX,
            PROMETHEUS_SUM_SUFFIX,
            PROMETHEUS_COUNT_SUFFIX,
            PROMETHEUS_BUCKET_SUFFIX);

    /**
     * The names (DropWizard names) of metrics that we want to exclude, e.g.
     * if the metric returns an invalid type.
     */
    private static final Set<String> METRIC_NAME_DENY_SET = Set.of(
            "jvm.attribute.name",
            "jvm.attribute.vendor",
            "jvm.threads.deadlocks");
    public static final String QUANTILE_KEY = "quantile";

    private final PrometheusTextWriter writer;

    public DropwizardMetricsExporter(final PrometheusTextWriter writer) {
        this.writer = writer;
    }

    public void writeAppInfo() {
        writeAppInfo(null, s -> true);
    }

    public void writeAppInfo(final AppInfoProvider appInfoProvider,
                             final Predicate<String> sanitisedNameFilter) {
        final String sanitisedName = APP_INFO_METRIC_NAME;
        if (sanitisedNameFilter.test(sanitisedName)) {
            final MetricType dropwizardMetricType = MetricType.GAUGE;
            writer.writeHelp(sanitisedName, APP_INFO_HELP);
            writer.writeType(sanitisedName, dropwizardMetricType.getPrometheusMetricType());
            final Map<String, String> appInfoLabels = NullSafe.getOrElse(appInfoProvider,
                    AppInfoProvider::getAppInfo,
                    AbstractAppInfoProvider.BASE_APP_INFO);
            writer.writeDoubleSample(sanitisedName, appInfoLabels, APP_INFO_VALUE);
        }
    }

    public void writeGauge(final String dropwizardName,
                           final Map<String, String> labels,
                           final Gauge<?> gauge) {
        doWithSanitisedName(dropwizardName, sanitisedName -> {
            final MetricType dropwizardMetricType = MetricType.GAUGE;
            writer.writeHelp(sanitisedName, getHelpMessage(dropwizardName, dropwizardMetricType, gauge));
            writer.writeType(sanitisedName, dropwizardMetricType.getPrometheusMetricType());

            final Object obj = gauge.getValue();
            switch (obj) {
                case final Long aLong -> writer.writeLongSample(sanitisedName, labels, aLong);
                case final Integer anInteger -> writer.writeLongSample(sanitisedName, labels, anInteger);
                case final Short aShort -> writer.writeLongSample(sanitisedName, labels, aShort);
                case final Byte aByte -> writer.writeLongSample(sanitisedName, labels, aByte);
                case final Number aNumber -> writer.writeDoubleSample(sanitisedName, labels, aNumber.doubleValue());
                default -> {
                    // Only log once, not every time
                    NAME_TO_SANITISED_NAME_MAP.computeIfAbsent(dropwizardName, k -> {
                        LOGGER.error("Invalid type {} for Gauge metric {}. " +
                                     "This metric will not be exported to Prometheus",
                                dropwizardName, obj.getClass().getName());
                        return Optional.empty();
                    });
                }
            }
        });
    }

    /**
     * Export counter as Prometheus <a href="https://prometheus.io/docs/concepts/metric_types/#gauge">Gauge</a>.
     */
    public void writeCounter(final String dropwizardName,
                             final Map<String, String> labels,
                             final Counter counter) {
        doWithSanitisedName(dropwizardName, sanitisedName -> {
            final MetricType dropwizardMetricType = MetricType.COUNTER;
            writer.writeHelp(sanitisedName, getHelpMessage(dropwizardName, dropwizardMetricType, counter));
            writer.writeType(sanitisedName, dropwizardMetricType.getPrometheusMetricType());
            writer.writeLongSample(sanitisedName, labels, counter.getCount());
        });
    }

    /**
     * Export a histogram snapshot as a prometheus SUMMARY.
     *
     * @param dropwizardName metric name.
     * @param histogram      the histogram.
     */
    public void writeHistogram(final String dropwizardName,
                               final Map<String, String> labels,
                               final Histogram histogram) {
        doWithSanitisedName(dropwizardName, sanitisedName -> {
            final MetricType dropwizardMetricType = MetricType.HISTOGRAM;
            writeSnapshotAndCount(
                    sanitisedName,
                    labels,
                    histogram.getSnapshot(),
                    histogram.getCount(),
                    1.0,
                    dropwizardMetricType.getPrometheusMetricType(),
                    getHelpMessage(dropwizardName, dropwizardMetricType, histogram));
        });
    }

    public void writeTimer(final String dropwizardName,
                           final Map<String, String> labels,
                           final Timer timer) {
        doWithSanitisedName(dropwizardName, sanitisedName -> {
            final MetricType dropwizardMetricType = MetricType.TIMER;
            writeSnapshotAndCount(
                    sanitisedName,
                    labels,
                    timer.getSnapshot(),
                    timer.getCount(),
                    1.0D / TimeUnit.SECONDS.toNanos(1L),
                    dropwizardMetricType.getPrometheusMetricType(),
                    getHelpMessage(dropwizardName, dropwizardMetricType, timer));
//        writeMetered(dropwizardName, timer);
        });
    }

    public void writeMeter(final String dropwizardName,
                           final Map<String, String> labels,
                           final Meter meter) {
        doWithSanitisedName(dropwizardName, sanitisedName -> {
            final MetricType dropwizardMetricType = MetricType.METER;
            writer.writeHelp(sanitisedName, getHelpMessage(dropwizardName, dropwizardMetricType, meter));
            writer.writeType(sanitisedName, dropwizardMetricType.getPrometheusMetricType());
            writer.writeLongSample(sanitisedName, labels, meter.getCount());
//        writeMetered(dropwizardName, meter);
        });
    }

    private void writeSnapshotAndCount(final String sanitisedName,
                                       final Map<String, String> labels,
                                       final Snapshot snapshot,
                                       final long count,
                                       final double factor,
                                       final PrometheusMetricType type,
                                       final String helpMessage) {
        // TODO Need to figure out how best to export our TIMER and HISTOGRAM,
        //  i.e. whether to use a prometheus SUMMARY or HISTOGRAM,
        //  and whether to drop all the quantile metrics as described here
        //  https://prometheus.io/docs/instrumenting/writing_exporters/#drop-less-useful-statistics
        //  See also
        //  https://prometheus.io/docs/practices/histograms/#quantiles
        writer.writeHelp(sanitisedName, helpMessage);
        writer.writeType(sanitisedName, type);
        writer.writeDoubleSample(sanitisedName,
                labels(labels, QUANTILE_KEY, "0.5"),
                snapshot.getMedian() * factor);
        writer.writeDoubleSample(sanitisedName,
                labels(labels, QUANTILE_KEY, "0.75"),
                snapshot.get75thPercentile() * factor);
        writer.writeDoubleSample(sanitisedName,
                labels(labels, QUANTILE_KEY, "0.95"),
                snapshot.get95thPercentile() * factor);
        writer.writeDoubleSample(sanitisedName,
                labels(labels, QUANTILE_KEY, "0.98"),
                snapshot.get98thPercentile() * factor);
        writer.writeDoubleSample(sanitisedName,
                labels(labels, QUANTILE_KEY, "0.99"),
                snapshot.get99thPercentile() * factor);
        writer.writeDoubleSample(sanitisedName,
                labels(labels, QUANTILE_KEY, "0.999"),
                snapshot.get999thPercentile() * factor);

        // Ignoring these, see link for details
        //  https://prometheus.io/docs/instrumenting/writing_exporters/#drop-less-useful-statistics
//        writer.writeLongSample(sanitisedName + "_min", labels, snapshot.getMin());
//        writer.writeLongSample(sanitisedName + "_max", labels, snapshot.getMax());
//        writer.writeDoubleSample(sanitisedName + "_median", labels, snapshot.getMedian());
//        writer.writeDoubleSample(sanitisedName + "_mean", labels, snapshot.getMean());
//        writer.writeDoubleSample(sanitisedName + "_stddev", labels, snapshot.getStdDev());
        writer.writeLongSample(sanitisedName + PROMETHEUS_COUNT_SUFFIX, labels, count);
    }

    /**
     * The Prometheus docs say to not include these values
     * <a href="https://prometheus.io/docs/instrumenting/writing_exporters/#drop-less-useful-statistics">
     * prometheus docs
     * </a>
     */
    @Deprecated
    private void writeMetered(final String dropwizardName, final Metered metered) {
        doWithSanitisedName(dropwizardName, sanitisedName -> {
            writer.writeDoubleSample(sanitisedName, Map.of("rate", "m1"), metered.getOneMinuteRate());
            writer.writeDoubleSample(sanitisedName, Map.of("rate", "m5"), metered.getFiveMinuteRate());
            writer.writeDoubleSample(sanitisedName, Map.of("rate", "m15"), metered.getFifteenMinuteRate());
            writer.writeDoubleSample(sanitisedName, Map.of("rate", "mean"), metered.getMeanRate());
        });
    }

    private static String getHelpMessage(final String metricName,
                                         final MetricType dropwizardMetricType,
                                         final Metric metric) {
        return String.format("Generated from Dropwizard metric import (metric=%s, dropwizardType=%s, class=%s)",
                metricName,
                dropwizardMetricType.getText(),
                metric.getClass().getName());
    }

    static void doWithSanitisedName(final String dropwizardName,
                                    final Consumer<String> sanitisedNameConsumer) {
        sanitiseMetricName(dropwizardName)
                .ifPresentOrElse(
                        sanitisedNameConsumer,
                        () -> LOGGER.debug("Ignored metric '{}'", dropwizardName));
    }

    /**
     * Convert dropwizardName into a form that is safe for use as a prometheus
     * metric name. If the metric is not suitable for use in prometheus then
     * an empty optional will be returned.
     *
     * @param dropwizardName The name of the metric as known to DropWizard
     * @return The sanitised name or an empty if it couldn't be sanitised or the
     * metric is not supported in prometheus.
     */
    static Optional<String> sanitiseMetricName(final String dropwizardName) {
        // This assumes we won't have loads of unique names
        return NAME_TO_SANITISED_NAME_MAP.computeIfAbsent(dropwizardName, aName -> {
            if (METRIC_NAME_DENY_SET.contains(aName)) {
                LOGGER.debug("Excluding metric '{}'", aName);
                return Optional.empty();
            } else {
                try {
                    String output = aName;
                    // Get rid of any chars not liked by prometheus
                    output = NAME_SANITISE_PATTERN.matcher(output)
                            .replaceAll("_");
                    // FOO => Foo so CASE_CONVERTER doesn't do FOO => F_O_O
                    output = replaceUppercaseWords(output);
                    // UpperCamel to lower_snake
                    output = CASE_CONVERTER.convert(output);
                    // Remove repeated '_' chars
                    output = MULTIPLE_UNDERSCORE_PATTERN.matcher(output)
                            .replaceAll("_");
                    LOGGER.debug("Converted '{}' => '{}'", aName, output);
                    for (final String reservedSuffix : PROMETHEUS_RESERVED_SUFFIXES) {
                        if (output.endsWith(reservedSuffix)) {
                            LOGGER.warn("Ignoring metric '{}' as the sanitised name '{}' is not " +
                                        "allowed to end with '{}'.",
                                    dropwizardName, output, reservedSuffix);
                        }
                    }
                    return NullSafe.isNonBlankString(output)
                            ? Optional.of(output)
                            : Optional.empty();
                } catch (final Exception e) {
                    LOGGER.error("Error sanitising metric name '{}': {}",
                            dropwizardName, LogUtil.exceptionMessage(e), e);
                    return Optional.empty();
                }
            }
        });
    }

    /**
     * FOO => Foo
     * Foo => Foo (no change)
     * foo => foo (no change)
     */
    private static String replaceUppercaseWords(final String str) {
        final Matcher matcher = MULTIPLE_UPPERCASE_CHARS_PATTERN.matcher(str);
        return matcher.replaceAll(matchResult -> {
            final String match = matchResult.group();
            if (match.length() >= 2) {
                // It should be, given the pattern
                return match.charAt(0) + match.substring(1).toLowerCase();
            } else {
                return match;
            }
        });
    }

    /**
     * @return A map containing the contents of labels and an entry for key => value.
     */
    private Map<String, String> labels(final Map<String, String> labels,
                                       final String key,
                                       final String value) {
        if (NullSafe.isEmptyMap(labels)) {
            return Map.of(key, value);
        } else {
            final Map<String, String> map = new HashMap<>(labels);
            map.put(key, value);
            return Collections.unmodifiableMap(map);
        }
    }
}
