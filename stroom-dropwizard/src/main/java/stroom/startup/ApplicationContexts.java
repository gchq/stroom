package stroom.startup;

import io.dropwizard.setup.Environment;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import stroom.Config;

public class ApplicationContexts {

    static AnnotationConfigWebApplicationContext applicationContext;
    static AnnotationConfigWebApplicationContext rootContext;

    static void configure() throws ClassNotFoundException {
        rootContext = new AnnotationConfigWebApplicationContext();

        applicationContext = new AnnotationConfigWebApplicationContext();
        applicationContext.setParent(rootContext);


        // We register all the configuration beans so they'll be loaded when we call applicationContext.refresh().
        applicationContext.register(stroom.security.spring.SecurityConfiguration.class,
                stroom.spring.ScopeConfiguration.class,
                stroom.spring.PersistenceConfiguration.class,
                stroom.spring.ServerComponentScanConfiguration.class,
                stroom.spring.ServerConfiguration.class,
                stroom.spring.CachedServiceConfiguration.class,
                stroom.logging.spring.EventLoggingConfiguration.class,
                stroom.index.spring.IndexConfiguration.class,
                stroom.search.spring.SearchConfiguration.class,
                stroom.script.spring.ScriptConfiguration.class,
                stroom.visualisation.spring.VisualisationConfiguration.class,
                stroom.dashboard.spring.DashboardConfiguration.class,
                stroom.spring.CoreClientConfiguration.class,
                stroom.statistics.spring.StatisticsConfiguration.class,
                Config.class
        );

        applicationContext.registerShutdownHook();
    }

    static void start(Environment environment, Config configuration){
        rootContext.refresh();
        rootContext.start();
        applicationContext.refresh();
        applicationContext.getBeanFactory().registerSingleton("dwConfiguration", configuration);
        applicationContext.getBeanFactory().registerSingleton("dwEnvironment", environment);
        applicationContext.getBeanFactory().registerSingleton("dwObjectMapper", environment.getObjectMapper());
        applicationContext.start();
    }
}
