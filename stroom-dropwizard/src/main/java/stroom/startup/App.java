package stroom.startup;

import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.eclipse.jetty.util.resource.PathResource;
import stroom.Config;
import stroom.security.spring.SecurityConfiguration;
import stroom.util.spring.StroomSpringProfiles;
import stroom.util.thread.ThreadScopeContextHolder;

import java.nio.file.Paths;

public class App extends Application<Config> {
    public static void main(String[] args) throws Exception {
        new App().run(args);
    }

    @Override
    public void initialize(Bootstrap<Config> bootstrap) {
        bootstrap.addBundle(new AssetsBundle("/webapp", "/", "stroom.jsp", "webapp"));
    }

    @Override
    public void run(Config configuration, Environment environment) throws Exception {
        // We want Stroom to use the root path so we need to move Dropwizard's path.
        environment.jersey().setUrlPattern("/api/*");

        // We need to set this otherwise we won't have all the beans we need.
        System.setProperty("spring.profiles.active", String.format("%s,%s", StroomSpringProfiles.PROD, SecurityConfiguration.PROD_SECURITY));


        // We need to prime this otherwise we won't have a thread scope context and bean initialisation will fail
        ThreadScopeContextHolder.createContext();

        ApplicationContexts.configure();
        Servlets.loadInto(environment);
        Filters.loadInto(environment);
        Listeners.loadInto(environment, ApplicationContexts.rootContext);

        // If we don't set the baseResource then servlets might not be able to find files.
        environment.servlets().setBaseResource(new PathResource(Paths.get("src/main/resources/webapp/")));

        // We need to set the servlet context otherwise there will be no default servlet handling.
        ApplicationContexts.applicationContext.setServletContext(environment.getApplicationContext().getServletContext());

        ApplicationContexts.start(environment, configuration);

    }



}
