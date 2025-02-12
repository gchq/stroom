package stroom.test.common;

import stroom.util.metrics.Metrics;

import com.codahale.metrics.MetricRegistry;

public class MockMetrics implements Metrics {

    @Override
    public MetricRegistry getRegistry() {
        return new MetricRegistry();
    }
}
