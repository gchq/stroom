package stroom.util;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

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
import com.codahale.metrics.SettableGauge;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.Timer;
import io.dropwizard.core.setup.Environment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Dropwizard Metrics related util methods/constants.
 * <p>
 * Provides a consistent way to create/register metrics with consistent naming.
 * </p>
 */
public class Metrics {

    public static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(HasMetrics.class);

    public static final Pattern METRIC_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9.-]+$");
    public static final Pattern REPLACE_PATTERN = Pattern.compile("[^a-zA-Z0-9.-]");

    public static final String DELTA = "delta";
    public static final String READ = "read";
    public static final String WRITE = "write";
    public static final String POSITION = "position";
    public static final String SIZE = "size";
    public static final String COUNT = "count";
    public static final String FILE_COUNT = "fileCount";
    public static final String SIZE_IN_BYTES = "sizeInBytes";

    private Metrics() {
        // Static util stuff only
    }

    /**
     * Build a metric name from the owning class and 0-many name parts.
     * Each part will have unsuitable characters stripped and be separated
     * by a '.' character. Name parts will be appended onto the fully qualified class name.
     *
     * @param clazz
     * @param nameParts
     * @return
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

//    /**
//     * Register a metric with the default metric registry
//     */
//    public static void register(final NamedMetric namedMetric) {
//        register(namedMetric.name, namedMetric.metric);
//    }

    /**
     * Register a metric with the default metric registry
     */
    private static void register(final String name, Metric metric) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(metric);
        LOGGER.debug("Registering metric '{}' of type {}", name, getMetricType(metric));
        getRegistry().register(name, metric);
    }

    /**
     * @param clazz The class that owns the metric. The fully qualified class name
     *              will be used as the prefix in the metric name.
     */
    public static RegistrationBuilder registrationBuilder(final Class<?> clazz) {
        return new RegistrationBuilder(clazz);
    }

    /**
     * @return The default {@link MetricRegistry} as would be returned from
     * {@link Environment#metrics()} or {@link SharedMetricRegistries#getDefault()}.
     */
    public static MetricRegistry getRegistry() {
        return SharedMetricRegistries.getDefault();
    }

    private static String getMetricType(final Metric metric) {
        // Metrics are often lambdas so this makes it easier to see in the logs
        // what each one is
        return switch (metric) {
            case null -> null;
            case MetricRegistry ignored -> MetricRegistry.class.getSimpleName();
            case Histogram ignored -> Histogram.class.getSimpleName();
            case Counter ignored -> Counter.class.getSimpleName();
            case Meter ignored -> Meter.class.getSimpleName();
            case Timer ignored -> Timer.class.getSimpleName();
            case RatioGauge ignored -> RatioGauge.class.getSimpleName();
            case CachedGauge<?> ignored -> CachedGauge.class.getSimpleName();
            case DerivativeGauge<?, ?> ignored -> DerivativeGauge.class.getSimpleName();
            case Gauge<?> ignored -> Gauge.class.getSimpleName();
            case MetricSet ignored -> MetricSet.class.getSimpleName();
            default -> metric.getClass().getSimpleName();
        };
    }


    // --------------------------------------------------------------------------------


    public record NamedMetric(String name, Metric metric) {

    }


    // --------------------------------------------------------------------------------


    public static class RegistrationBuilder {

        private final Class<?> clazz;
        private List<String> nameParts;

        private RegistrationBuilder(final Class<?> clazz) {
            this.clazz = Objects.requireNonNull(clazz);
        }

        /**
         * Adds a name part. Each part will have unsuitable characters stripped and be separated
         * by a '.' character. Name parts will be appended onto the fully qualified class name.
         */
        public RegistrationBuilder addNamePart(final String namePart) {
            if (nameParts == null) {
                nameParts = new ArrayList<>();
            }

            if (NullSafe.isNonBlankString(namePart)) {
                nameParts.add(namePart);
            }
            return this;
        }

        /**
         * Sets the name parts. Each part will have unsuitable characters stripped and be separated
         * by a '.' character. Name parts will be appended onto the fully qualified class name.
         */
        public RegistrationBuilder withNameParts(final List<String> nameParts) {
            if (this.nameParts == null) {
                this.nameParts = new ArrayList<>();
            }

            if (NullSafe.hasItems(nameParts)) {
                this.nameParts.addAll(nameParts);
            }
            return this;
        }

        /**
         * @see Gauge
         */
        public <T> VoidRegisterStage<Gauge<T>> gauge(final Gauge<T> gauge) {
            return new VoidRegisterStage<>(clazz, nameParts, gauge);
        }

        /**
         * @see CachedGauge
         */
        public <T> VoidRegisterStage<CachedGauge<T>> cachedGauge(final CachedGauge<T> cachedGauge) {
            return new VoidRegisterStage<>(clazz, nameParts, cachedGauge);
        }

        /**
         * @see RatioGauge
         */
        public VoidRegisterStage<RatioGauge> ratioGauge(final RatioGauge ratioGauge) {
            return new VoidRegisterStage<>(clazz, nameParts, ratioGauge);
        }

        /**
         * @see DerivativeGauge
         */
        public <F, T> VoidRegisterStage<DerivativeGauge<F, T>> derivativeGauge(
                final DerivativeGauge<F, T> derivativeGauge) {
            return new VoidRegisterStage<>(clazz, nameParts, derivativeGauge);
        }

        /**
         * @see SettableGauge
         */
        public <T> CreateAndRegisterStage<SettableGauge<T>> settableGauge() {
            return new CreateAndRegisterStage<>(clazz, nameParts, MetricRegistry::gauge);
        }

        /**
         * @see MetricSet
         */
        public VoidRegisterStage<MetricSet> metricSet(final MetricSet metricSet) {
            return new VoidRegisterStage<>(clazz, nameParts, metricSet);
        }

        /**
         * @see Counter
         */
        public CreateAndRegisterStage<Counter> counter() {
            return new CreateAndRegisterStage<>(clazz, nameParts, MetricRegistry::counter);
        }

        /**
         * @see Timer
         */
        public CreateAndRegisterStage<Timer> timer() {
            return new CreateAndRegisterStage<>(clazz, nameParts, MetricRegistry::timer);
        }

        /**
         * @see Meter
         */
        public CreateAndRegisterStage<Meter> meter() {
            return new CreateAndRegisterStage<>(clazz, nameParts, MetricRegistry::meter);
        }

        /**
         * @see Histogram
         */
        public CreateAndRegisterStage<Histogram> histogram() {
            return new CreateAndRegisterStage<>(clazz, nameParts, MetricRegistry::histogram);
        }
    }


    // --------------------------------------------------------------------------------


    public static class VoidRegisterStage<M extends Metric> {

        private final Class<?> clazz;
        private final List<String> nameParts;
        private final M metric;

        private VoidRegisterStage(final Class<?> clazz,
                                  final List<String> nameParts,
                                  final M metric) {
            this.clazz = Objects.requireNonNull(clazz);
            this.nameParts = nameParts;
            this.metric = Objects.requireNonNull(metric);
        }

        public void register() {
            final String[] namePartsArr = NullSafe.stream(nameParts)
                    .toArray(String[]::new);

            Metrics.register(
                    buildName(clazz, namePartsArr),
                    metric);
        }
    }


    // --------------------------------------------------------------------------------


    public static class CreateAndRegisterStage<M extends Metric> {

        private final Class<?> clazz;
        private final List<String> nameParts;
        private final BiFunction<MetricRegistry, String, M> createFunc;

        private CreateAndRegisterStage(final Class<?> clazz,
                                       final List<String> nameParts,
                                       final BiFunction<MetricRegistry, String, M> createFunc) {
            this.clazz = Objects.requireNonNull(clazz);
            this.nameParts = nameParts;
            this.createFunc = Objects.requireNonNull(createFunc);
        }

        public M createAndRegister() {
            final String[] namePartsArr = NullSafe.stream(nameParts)
                    .toArray(String[]::new);

            return createFunc.apply(
                    getRegistry(),
                    buildName(clazz, namePartsArr));
        }
    }
}
