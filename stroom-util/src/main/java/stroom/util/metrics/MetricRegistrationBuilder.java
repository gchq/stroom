package stroom.util.metrics;

import stroom.util.NullSafe;

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
import com.codahale.metrics.Timer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

public class MetricRegistrationBuilder {

    private final MetricRegistry metricRegistry;
    private final Class<?> clazz;
    private List<String> nameParts;

    MetricRegistrationBuilder(final MetricRegistry metricRegistry,
                              final Class<?> clazz) {
        this.metricRegistry = Objects.requireNonNull(metricRegistry);
        this.clazz = Objects.requireNonNull(clazz);
    }

    /**
     * Adds a name part. Each part will have unsuitable characters stripped and be separated
     * by a '.' character. Name parts will be appended onto the fully qualified class name.
     */
    public MetricRegistrationBuilder addNamePart(final String namePart) {
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
    public MetricRegistrationBuilder withNameParts(final List<String> nameParts) {
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
        return new VoidRegisterStage<>(metricRegistry, clazz, nameParts, gauge);
    }

    /**
     * @see CachedGauge
     */
    public <T> VoidRegisterStage<CachedGauge<T>> cachedGauge(final CachedGauge<T> cachedGauge) {
        return new VoidRegisterStage<>(metricRegistry, clazz, nameParts, cachedGauge);
    }

    /**
     * @see RatioGauge
     */
    public VoidRegisterStage<RatioGauge> ratioGauge(final RatioGauge ratioGauge) {
        return new VoidRegisterStage<>(metricRegistry, clazz, nameParts, ratioGauge);
    }

    /**
     * @see DerivativeGauge
     */
    public <F, T> VoidRegisterStage<DerivativeGauge<F, T>> derivativeGauge(
            final DerivativeGauge<F, T> derivativeGauge) {
        return new VoidRegisterStage<>(metricRegistry, clazz, nameParts, derivativeGauge);
    }

    /**
     * @see SettableGauge
     */
    public <T> CreateAndRegisterStage<SettableGauge<T>> settableGauge() {
        return new CreateAndRegisterStage<>(
                metricRegistry, clazz, nameParts, MetricRegistry::gauge);
    }

    /**
     * @see MetricSet
     */
    public VoidRegisterStage<MetricSet> metricSet(final MetricSet metricSet) {
        return new VoidRegisterStage<>(metricRegistry, clazz, nameParts, metricSet);
    }

    /**
     * @see Counter
     */
    public CreateAndRegisterStage<Counter> counter() {
        return new CreateAndRegisterStage<>(
                metricRegistry, clazz, nameParts, MetricRegistry::counter);
    }

    /**
     * @see Timer
     */
    public CreateAndRegisterStage<Timer> timer() {
        return new CreateAndRegisterStage<>(
                metricRegistry, clazz, nameParts, MetricRegistry::timer);
    }

    /**
     * @see Meter
     */
    public CreateAndRegisterStage<Meter> meter() {
        return new CreateAndRegisterStage<>(
                metricRegistry, clazz, nameParts, MetricRegistry::meter);
    }

    /**
     * @see Histogram
     */
    public CreateAndRegisterStage<Histogram> histogram() {
        return new CreateAndRegisterStage<>(
                metricRegistry, clazz, nameParts, MetricRegistry::histogram);
    }


    // --------------------------------------------------------------------------------


    public static class VoidRegisterStage<M extends Metric> {

        private final MetricRegistry metricRegistry;
        private final Class<?> clazz;
        private final List<String> nameParts;
        private final M metric;

        private VoidRegisterStage(final MetricRegistry metricRegistry,
                                  final Class<?> clazz,
                                  final List<String> nameParts,
                                  final M metric) {
            this.metricRegistry = Objects.requireNonNull(metricRegistry);
            this.clazz = Objects.requireNonNull(clazz);
            this.nameParts = nameParts;
            this.metric = Objects.requireNonNull(metric);
        }

        public void register() {
            final String[] namePartsArr = NullSafe.stream(nameParts)
                    .toArray(String[]::new);

            MetricsUtil.register(
                    metricRegistry,
                    MetricsUtil.buildName(clazz, namePartsArr),
                    metric);
        }
    }


    // --------------------------------------------------------------------------------


    public static class CreateAndRegisterStage<M extends Metric> {

        private final MetricRegistry metricRegistry;
        private final Class<?> clazz;
        private final List<String> nameParts;
        private final BiFunction<MetricRegistry, String, M> createFunc;

        private CreateAndRegisterStage(final MetricRegistry metricRegistry,
                                       final Class<?> clazz,
                                       final List<String> nameParts,
                                       final BiFunction<MetricRegistry, String, M> createFunc) {
            this.metricRegistry = Objects.requireNonNull(metricRegistry);
            this.clazz = Objects.requireNonNull(clazz);
            this.nameParts = nameParts;
            this.createFunc = Objects.requireNonNull(createFunc);
        }

        public M createAndRegister() {
            final String[] namePartsArr = NullSafe.stream(nameParts)
                    .toArray(String[]::new);

            return createFunc.apply(
                    metricRegistry,
                    MetricsUtil.buildName(clazz, namePartsArr));
        }
    }
}
