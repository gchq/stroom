package stroom.test;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.AbstractModule;
import io.dropwizard.core.setup.Environment;
import org.mockito.Mockito;

public class MockEnvironmentModule extends AbstractModule {

    @Override
    protected void configure() {
        final Environment mockEnvironment = Mockito.mock(Environment.class);
        final MetricRegistry metricRegistry = new MetricRegistry();
        Mockito.when(mockEnvironment.getName())
                        .thenReturn("MockEnvironment");
        Mockito.when(mockEnvironment.metrics())
                        .thenReturn(metricRegistry);
        bind(Environment.class).toInstance(mockEnvironment);
    }
}
