/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.startup;

import com.google.common.collect.ImmutableMap;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.servlets.tasks.LogConfigurationTask;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.apache.shiro.web.servlet.AbstractShiroFilter;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import stroom.annotations.StroomAnnotationsConfig;
import stroom.annotations.StroomAnnotationsExplorerActionHandler;
import stroom.cluster.server.ClusterCallServiceRPC;
import stroom.dashboard.spring.DashboardConfiguration;
import stroom.datafeed.server.DataFeedServlet;
import stroom.dictionary.spring.DictionaryConfiguration;
import stroom.dispatch.shared.DispatchService;
import stroom.elastic.server.ElasticIndexResource;
import stroom.elastic.server.StroomElasticConfig;
import stroom.elastic.server.StroomElasticExplorerActionHandler;
import stroom.elastic.spring.ElasticIndexConfiguration;
import stroom.entity.server.SpringRequestFactoryServlet;
import stroom.explorer.server.ExplorerConfiguration;
import stroom.feed.server.RemoteFeedServiceRPC;
import stroom.index.server.StroomIndexQueryResource;
import stroom.index.spring.IndexConfiguration;
import stroom.lifecycle.LifecycleService;
import stroom.logging.spring.EventLoggingConfiguration;
import stroom.pipeline.spring.PipelineConfiguration;
import stroom.script.server.ScriptServlet;
import stroom.script.spring.ScriptConfiguration;
import stroom.search.spring.SearchConfiguration;
import stroom.security.server.AuthenticationResource;
import stroom.security.server.AuthorisationResource;
import stroom.security.spring.SecurityConfiguration;
import stroom.servicediscovery.ResourcePaths;
import stroom.servicediscovery.ServiceDiscovererImpl;
import stroom.servicediscovery.ServiceDiscoveryRegistrar;
import stroom.servlet.DebugServlet;
import stroom.servlet.DynamicCSSServlet;
import stroom.servlet.EchoServlet;
import stroom.servlet.ExportConfigServlet;
import stroom.servlet.HttpServletRequestFilter;
import stroom.servlet.ImportFileServlet;
import stroom.servlet.RejectPostFilter;
import stroom.servlet.SessionListListener;
import stroom.servlet.SessionListServlet;
import stroom.servlet.SessionResourceStoreImpl;
import stroom.servlet.StatusServlet;
import stroom.spring.MetaDataStatisticConfiguration;
import stroom.spring.PersistenceConfiguration;
import stroom.spring.ScopeConfiguration;
import stroom.spring.ServerComponentScanConfiguration;
import stroom.spring.ServerConfiguration;
import stroom.statistics.server.sql.search.SqlStatisticsQueryResource;
import stroom.statistics.spring.StatisticsConfiguration;
import stroom.util.spring.StroomSpringProfiles;
import stroom.visualisation.spring.VisualisationConfiguration;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import java.util.EnumSet;

public class App extends Application<Configuration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(App.class);

    public static void main(final String[] args) throws Exception {
        // Hibernate requires JBoss Logging. The SLF4J API jar wasn't being detected so this sets it manually.
        System.setProperty("org.jboss.logging.provider", "slf4j");
        new App().run(args);
    }

    @Override
    public void initialize(final Bootstrap<Configuration> bootstrap) {
        bootstrap.addBundle(new AssetsBundle("/ui", "/", "stroom.jsp", "ui"));
    }

    @Override
    public void run(final Configuration configuration, final Environment environment) throws Exception {
        // Add useful logging setup.
        registerLogConfiguration(environment);
        environment.healthChecks().register(LogLevelInspector.class.getName(), new LogLevelInspector());

        // We want Stroom to use the root path so we need to move Dropwizard's path.
        environment.jersey().setUrlPattern(ResourcePaths.ROOT_PATH + "/*");

        // Set up a session manager for Jetty
        SessionHandler sessions = new SessionHandler();
        environment.servlets().setSessionHandler(sessions);

        // Configure Cross-Origin Resource Sharing.
        configureCors(environment);

        // Start the spring context.
        LOGGER.info("Loading Spring context");
        final ApplicationContext applicationContext = loadApplcationContext(configuration, environment);


        final ServletContextHandler servletContextHandler = environment.getApplicationContext();

        // Add health checks
        SpringUtil.addHealthCheck(environment.healthChecks(), applicationContext, ServiceDiscoveryRegistrar.class);
        SpringUtil.addHealthCheck(environment.healthChecks(), applicationContext, ServiceDiscovererImpl.class);
        SpringUtil.addHealthCheck(environment.healthChecks(), applicationContext, SqlStatisticsQueryResource.class);
        SpringUtil.addHealthCheck(environment.healthChecks(), applicationContext, StroomIndexQueryResource.class);

        // Add filters
        SpringUtil.addFilter(servletContextHandler, applicationContext, HttpServletRequestFilter.class, "/*");
        FilterUtil.addFilter(servletContextHandler, RejectPostFilter.class, "rejectPostFilter", ImmutableMap.<String, String>builder().put("rejectUri", "/").build());
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
        SpringUtil.addServlet(servletContextHandler, applicationContext, DataFeedServlet.class, "/datafeed");
        SpringUtil.addServlet(servletContextHandler, applicationContext, DataFeedServlet.class, "/datafeed/*");

        // Add session listeners.
        SpringUtil.addServletListener(environment.servlets(), applicationContext, SessionListListener.class);

        // Add resources.
        SpringUtil.addResource(environment.jersey(), applicationContext, ElasticIndexResource.class);
        SpringUtil.addResource(environment.jersey(), applicationContext, StroomIndexQueryResource.class);
        SpringUtil.addResource(environment.jersey(), applicationContext, SqlStatisticsQueryResource.class);
        SpringUtil.addResource(environment.jersey(), applicationContext, AuthenticationResource.class);
        SpringUtil.addResource(environment.jersey(), applicationContext, AuthorisationResource.class);

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
                DictionaryConfiguration.class,
                PipelineConfiguration.class,
                ExplorerConfiguration.class,
                IndexConfiguration.class,
                SearchConfiguration.class,
                ScriptConfiguration.class,
                VisualisationConfiguration.class,
                DashboardConfiguration.class,
                MetaDataStatisticConfiguration.class,
                StatisticsConfiguration.class,
                SecurityConfiguration.class,
                StroomAnnotationsConfig.class,
                StroomAnnotationsExplorerActionHandler.class,
                //StroomElasticConfig.class,
                //StroomElasticExplorerActionHandler.class, // replaced by ElasticIndexConfiguration
                ElasticIndexConfiguration.class // Sort this
        );
        applicationContext.refresh();
        return applicationContext;
    }

    private void configureCors(final Environment environment) {
        FilterRegistration.Dynamic cors = environment.servlets().
                addFilter("CORS", CrossOriginFilter.class);
        cors.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
        cors.setInitParameter(
                CrossOriginFilter.ACCESS_CONTROL_ALLOW_METHODS_HEADER, "GET,PUT,POST,DELETE,OPTIONS");
        cors.setInitParameter(CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, "*");
        cors.setInitParameter(CrossOriginFilter.ACCESS_CONTROL_ALLOW_HEADERS_HEADER, "*");
        cors.setInitParameter(CrossOriginFilter.ACCESS_CONTROL_ALLOW_CREDENTIALS_HEADER, "true");
    }

    private void registerLogConfiguration(final Environment environment) {
        // Task to allow configuration of log levels at runtime
        String path = environment.getAdminContext().getContextPath();

        // To change the log level do one of:
        // curl -X POST -d "logger=stroom&level=DEBUG" [admin context path]/tasks/log-level
        // http -f POST [admin context path]/tasks/log-level logger=stroom level=DEBUG
        // 'http' requires installing HTTPie

        LOGGER.info("Registering Log Configuration Task on {}/tasks/log-level", path);
        environment.admin().addTask(new LogConfigurationTask());
    }
}
