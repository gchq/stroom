package stroom.startup;

import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.setup.Bootstrap;
import stroom.Config;

public class App extends Application<Config> {
    public static void main(String[] args) throws Exception {
        new App().run(args);
    }

    @Override
    public void initialize(Bootstrap<Config> bootstrap) {
        bootstrap.addBundle(new AssetsBundle("/webapp", "/", "stroom.jsp", "webapp"));
    }

    @Override
    public void run(Config configuration, io.dropwizard.setup.Environment environment) throws Exception {
        Environment.configure(environment);
        SpringContexts.configure();
        Servlets.loadInto(environment);
        Filters.loadInto(environment);
        Listeners.loadInto(environment, SpringContexts.rootContext);
        SpringContexts.start(environment, configuration);
    }
}
