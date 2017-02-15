package stroom.startup;

import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import stroom.Config;
import stroom.util.spring.StroomSpringProfiles;
import stroom.util.thread.ThreadScopeContextHolder;

public class App extends Application<Config> {
    public static void main(String[] args) throws Exception {
        new App().run(args);
    }

    @Override
    public void initialize(Bootstrap<Config> bootstrap) {
    }

    @Override
    public void run(Config configuration, Environment environment) throws Exception {
        // We need to set this otherwise we won't have all the beans we need.
        System.setProperty("spring.profiles.active", StroomSpringProfiles.PROD);

        // We need to prime this otherwise we won't have a thread scope context and bean initialisation will fail
        ThreadScopeContextHolder.createContext();


        ApplicationContexts.configure(configuration, environment);
        Servlets.loadInto(environment);
        Filters.loadInto(environment);
        Listeners.loadInto(environment, ApplicationContexts.applicationContext);
        ApplicationContexts.start();
    }



}
