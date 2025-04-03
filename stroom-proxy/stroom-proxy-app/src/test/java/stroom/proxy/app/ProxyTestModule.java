package stroom.proxy.app;

import stroom.proxy.app.guice.ProxyConfigModule;
import stroom.proxy.app.guice.ProxyCoreModule;
import stroom.proxy.app.handler.ForwardFileDestinationFactory;
import stroom.proxy.app.handler.ForwardHttpPostDestinationFactory;
import stroom.proxy.app.handler.ForwardHttpPostDestinationFactoryImpl;
import stroom.proxy.app.handler.MockForwardFileDestinationFactory;
import stroom.test.common.MockMetrics;
import stroom.util.metrics.Metrics;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.google.inject.AbstractModule;
import io.dropwizard.core.setup.Environment;

import java.nio.file.Path;

public class ProxyTestModule extends AbstractModule {

    private final Config configuration;
    private final Environment environment;
    private final ProxyConfigHolder proxyConfigHolder;

    public ProxyTestModule(final Config configuration,
                           final Environment environment,
                           final Path configFile) {
        this.configuration = configuration;
        this.environment = environment;

        proxyConfigHolder = new ProxyConfigHolder(
                configuration.getProxyConfig(),
                configFile);
    }

    @Override
    protected void configure() {
        bind(Config.class).toInstance(configuration);
        bind(Environment.class).toInstance(environment);
        bind(MetricRegistry.class).toInstance(environment.metrics());
        bind(HealthCheckRegistry.class).toInstance(environment.healthChecks());
        bind(Metrics.class).toInstance(new MockMetrics());

        install(new ProxyConfigModule(proxyConfigHolder));
        install(new ProxyCoreModule());

        bind(ForwardHttpPostDestinationFactory.class).to(ForwardHttpPostDestinationFactoryImpl.class);
        bind(ForwardFileDestinationFactory.class).to(MockForwardFileDestinationFactory.class);
    }
}
