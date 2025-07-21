package stroom.test.common;

import stroom.util.metrics.Metrics;

import com.codahale.metrics.MetricRegistry;

public class MockMetrics implements Metrics {

    private static final Metrics INSTANCE = new MockMetrics();

    @Override
    public MetricRegistry getRegistry() {
        return new MetricRegistry();
    }

    public static Metrics getInstance() {
        return INSTANCE;
    }
}
