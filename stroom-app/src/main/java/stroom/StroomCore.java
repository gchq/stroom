package stroom;

import com.google.common.collect.ImmutableMap;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import org.apache.shiro.web.servlet.AbstractShiroFilter;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import stroom.cluster.server.ClusterCallServiceRPC;
import stroom.dashboard.server.logging.spring.EventLoggingConfiguration;
import stroom.dashboard.spring.DashboardConfiguration;
import stroom.datafeed.server.DataFeedServiceImpl;
import stroom.dispatch.shared.DispatchService;
import stroom.entity.server.SpringRequestFactoryServlet;
import stroom.feed.server.RemoteFeedServiceRPC;
import stroom.index.spring.IndexConfiguration;
import stroom.index.spring.IndexResourceConfiguration;
import stroom.lifecycle.LifecycleService;
import stroom.pipeline.spring.PipelineConfiguration;
import stroom.script.server.ScriptServlet;
import stroom.script.spring.ScriptConfiguration;
import stroom.search.spring.SearchConfiguration;
import stroom.security.spring.SecurityConfiguration;
import stroom.servlet.DebugServlet;
import stroom.servlet.DynamicCSSServlet;
import stroom.servlet.EchoServlet;
import stroom.servlet.ExportConfigServlet;
import stroom.servlet.ImportFileServlet;
import stroom.servlet.RejectPostFilter;
import stroom.servlet.SessionListListener;
import stroom.servlet.SessionListServlet;
import stroom.servlet.SessionResourceStoreImpl;
import stroom.servlet.StatusServlet;
import stroom.spring.CoreClientConfiguration;
import stroom.spring.MetaDataStatisticConfiguration;
import stroom.spring.PersistenceConfiguration;
import stroom.spring.ScopeConfiguration;
import stroom.spring.ServerComponentScanConfiguration;
import stroom.spring.ServerConfiguration;
import stroom.startup.AppAware;
import stroom.statistics.spring.StatisticsConfiguration;
import stroom.statistics.spring.StatisticsResourceConfiguration;
import stroom.util.spring.StroomSpringProfiles;
import stroom.util.thread.ThreadScopeContextFilter;
import stroom.visualisation.spring.VisualisationConfiguration;

public class StroomCore implements AppAware {
    private final Logger LOGGER = LoggerFactory.getLogger(StroomCore.class);

    @Override
    public void initialize(final Configuration configuration, final Environment environment) {
        // Start the spring context.
        LOGGER.info("Loading Spring context");
        final ApplicationContext applicationContext = loadApplcationContext(configuration, environment);


        final ServletContextHandler servletContextHandler = environment.getApplicationContext();

        // Add health checks
//        SpringUtil.addHealthCheck(environment.healthChecks(), applicationContext, ServiceDiscoveryRegistrar.class);
//        SpringUtil.addHealthCheck(environment.healthChecks(), applicationContext, ServiceDiscovererImpl.class);
//        SpringUtil.addHealthCheck(environment.healthChecks(), applicationContext, SqlStatisticsQueryResource.class);
//        SpringUtil.addHealthCheck(environment.healthChecks(), applicationContext, StroomIndexQueryResource.class);

        // Add filters
        FilterUtil.addFilter(servletContextHandler, ThreadScopeContextFilter.class, "threadScopeContextFilter", null);

        FilterUtil.addFilter(servletContextHandler, RejectPostFilter.class, "rejectPostFilter",
                ImmutableMap.<String, String>builder().put("rejectUri", "/").build());

        SpringUtil.addFilter(servletContextHandler, applicationContext, AbstractShiroFilter.class, "/*");

        // Add servlets
        SpringUtil.addServlet(servletContextHandler, applicationContext, DynamicCSSServlet.class, "/dynamic.css");
        SpringUtil.addServlet(servletContextHandler, applicationContext, DispatchService.class, "/dispatch.rpc");
        SpringUtil.addServlet(servletContextHandler, applicationContext, ImportFileServlet.class, "/importfile.rpc");
        SpringUtil.addServlet(servletContextHandler, applicationContext, ScriptServlet.class, "/script");
        SpringUtil.addServlet(servletContextHandler, applicationContext, ClusterCallServiceRPC.class, "/clustercall.rpc");
        SpringUtil.addServlet(servletContextHandler, applicationContext, ExportConfigServlet.class, "/export");
        SpringUtil.addServlet(servletContextHandler, applicationContext, StatusServlet.class, "/status");
        SpringUtil.addServlet(servletContextHandler, applicationContext, EchoServlet.class, "/echo");
        SpringUtil.addServlet(servletContextHandler, applicationContext, DebugServlet.class, "/debug");
        SpringUtil.addServlet(servletContextHandler, applicationContext, SessionListServlet.class, "/sessionList");
        SpringUtil.addServlet(servletContextHandler, applicationContext, SessionResourceStoreImpl.class, "/resourcestore/*");
        SpringUtil.addServlet(servletContextHandler, applicationContext, SpringRequestFactoryServlet.class, "/gwtRequest");
        SpringUtil.addServlet(servletContextHandler, applicationContext, RemoteFeedServiceRPC.class, "/remoting/remotefeedservice.rpc");
        SpringUtil.addServlet(servletContextHandler, applicationContext, DataFeedServiceImpl.class, "/datafeed");
        SpringUtil.addServlet(servletContextHandler, applicationContext, DataFeedServiceImpl.class, "/datafeed/*");

        // Add session listeners.
        SpringUtil.addServletListener(environment.servlets(), applicationContext, SessionListListener.class);

        // Add resources.
//        SpringUtil.addResource(environment.jersey(), applicationContext, StroomIndexQueryResource.class);
//        SpringUtil.addResource(environment.jersey(), applicationContext, SqlStatisticsQueryResource.class);
//        SpringUtil.addResource(environment.jersey(), applicationContext, AuthenticationResource.class);
//        SpringUtil.addResource(environment.jersey(), applicationContext, AuthorisationResource.class);

        AdminTasks.registerAdminTasks(environment);


        // Listen to the lifecycle of the Dropwizard app.
        SpringUtil.manage(environment.lifecycle(), applicationContext, LifecycleService.class);
    }

    private ApplicationContext loadApplcationContext(final Configuration configuration, final Environment environment) {
        final AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext();
        applicationContext.getEnvironment().setActiveProfiles(StroomSpringProfiles.PROD, SecurityConfiguration.PROD_SECURITY);
        applicationContext.getBeanFactory().registerSingleton("dwConfiguration", configuration);
        applicationContext.getBeanFactory().registerSingleton("dwEnvironment", environment);
        applicationContext.register(
                ScopeConfiguration.class,
                PersistenceConfiguration.class,
                ServerComponentScanConfiguration.class,
                ServerConfiguration.class,
                EventLoggingConfiguration.class,
                PipelineConfiguration.class,
                IndexConfiguration.class,
                IndexResourceConfiguration.class,
                SearchConfiguration.class,
                ScriptConfiguration.class,
                VisualisationConfiguration.class,
                DashboardConfiguration.class,
                CoreClientConfiguration.class,
                MetaDataStatisticConfiguration.class,
                StatisticsConfiguration.class,
                StatisticsResourceConfiguration.class,
                SecurityConfiguration.class
        );
        applicationContext.refresh();
        return applicationContext;
    }
}
