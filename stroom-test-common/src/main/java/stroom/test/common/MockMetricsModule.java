package stroom.test.common;

import stroom.util.metrics.Metrics;

import com.google.inject.AbstractModule;

public class MockMetricsModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(Metrics.class).to(MockMetrics.class);
    }
}
