package stroom.index.app.guice;

import stroom.index.app.Config;

import com.google.inject.AbstractModule;
import io.dropwizard.setup.Environment;

public class AppModule extends AbstractModule {

    private final Config configuration;
    private final Environment environment;

    public AppModule(final Config configuration,
                     final Environment environment) {
        this.configuration = configuration;
        this.environment = environment;
    }

    @Override
    protected void configure() {
        bind(Config.class).toInstance(configuration);
        bind(Environment.class).toInstance(environment);
    }
}
