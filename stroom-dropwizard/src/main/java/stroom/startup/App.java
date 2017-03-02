package stroom.startup;

import com.codahale.metrics.health.HealthCheck;
import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.setup.Bootstrap;
import stroom.Config;
import stroom.SearchResource;

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
        // The order in which the following are run is important.

        Environment.configure(environment);
        SpringContexts springContexts = new SpringContexts();
        Servlets servlets = new Servlets(environment);
        Filters.loadInto(environment);
        Listeners.loadInto(environment, springContexts.rootContext);

        springContexts.start(environment, configuration);

        SearchResource searchResource = new SearchResource();
        environment.jersey().register(searchResource);

        new Thread(() -> APIs.register(servlets.upgradeDispatcherServletHolder, searchResource))
                .start();


        environment.healthChecks().register("SearchResourceHealthCheck", new HealthCheck() {
            @Override
            protected Result check() throws Exception {
                return searchResource.getHealth();
            }
        });
    }
}
