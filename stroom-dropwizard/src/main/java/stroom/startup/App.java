package stroom.startup;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.util.ContextInitializer;
import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.setup.Bootstrap;
import org.slf4j.LoggerFactory;
import stroom.Config;

public class App extends Application<Config> {
    public static void main(String[] args) throws Exception {
        new App().run(args);

        // LEAVING THIS HERE FOR NOW IN CASE IT IS NEEDED
//        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
//        context.reset();
//        ContextInitializer initializer = new ContextInitializer(context);
//        initializer.autoConfig();
    }

    @Override
    public void initialize(Bootstrap<Config> bootstrap) {
        bootstrap.addBundle(new AssetsBundle("/ui", "/", "stroom.jsp", "ui"));
    }

    @Override
    public void run(Config configuration, io.dropwizard.setup.Environment environment) throws Exception {
        // The order in which the following are run is important.
        Environment.configure(configuration, environment);
        SpringContexts springContexts = new SpringContexts();
        Servlets servlets = new Servlets(environment);
        Filters filters = new Filters(environment);
        Listeners listeners = new Listeners(environment, springContexts.rootContext);
        springContexts.start(environment, configuration);
        ApiResources apiResources = new ApiResources(environment, servlets.upgradeDispatcherServletHolder);
        HealthChecks healthChecks = new HealthChecks(environment, apiResources);
    }
}
