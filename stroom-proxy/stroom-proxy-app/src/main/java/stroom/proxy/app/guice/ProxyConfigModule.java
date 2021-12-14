package stroom.proxy.app.guice;

import stroom.proxy.app.ProxyConfigHolder;
import stroom.proxy.app.ProxyConfigMonitor;
import stroom.util.config.ConfigLocation;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.HasHealthCheckBinder;
import stroom.util.io.DirProvidersModule;
import stroom.util.validation.ValidationModule;

import com.google.inject.AbstractModule;
import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyConfigModule extends AbstractModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyConfigModule.class);

    private final ProxyConfigHolder proxyConfigHolder;

    public ProxyConfigModule(final ProxyConfigHolder proxyConfigHolder) {
        this.proxyConfigHolder = proxyConfigHolder;
    }

    @Override
    protected void configure() {
        bind(ProxyConfigHolder.class).toInstance(proxyConfigHolder);

        bind(ProxyConfigMonitor.class).asEagerSingleton();

        install(new ProxyConfigProvidersModule());
        install(new DirProvidersModule());
        install(new ValidationModule());

        HasHealthCheckBinder.create(binder())
                .bind(ProxyConfigMonitor.class);

        GuiceUtil.buildMultiBinder(binder(), Managed.class)
                .addBinding(ProxyConfigMonitor.class);

        // Holder for the location of the yaml config file so the AppConfigMonitor can
        // get hold of it via guice
        bind(ConfigLocation.class)
                .toInstance(new ConfigLocation(proxyConfigHolder.getConfigFile()));
    }
}
