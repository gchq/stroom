package stroom.util.metrics;

import com.codahale.metrics.MetricRegistry;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class MetricsImpl implements Metrics {

    private final MetricRegistry metricRegistry;

    @Inject
    public MetricsImpl(final MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
    }

    @Override
    public MetricRegistry getRegistry() {
        // This is registry comes from a static so is no good in tests
        return metricRegistry;
    }
}
