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

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.servlets.tasks.LogConfigurationTask;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.glassfish.jersey.logging.LoggingFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import stroom.cluster.server.ClusterCallServiceRPC;
import stroom.connectors.elastic.StroomElasticProducerFactoryService;
import stroom.connectors.kafka.StroomKafkaProducerFactoryService;
import stroom.content.ContentSyncService;
import stroom.dashboard.spring.DashboardConfiguration;
import stroom.datafeed.server.DataFeedServlet;
import stroom.dictionary.server.DictionaryResource;
import stroom.dictionary.server.DictionaryStore;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.dictionary.spring.DictionaryConfiguration;
import stroom.dispatch.shared.DispatchService;
import stroom.entity.server.SpringRequestFactoryServlet;
import stroom.entity.server.util.ConnectionUtil;
import stroom.explorer.server.ExplorerConfiguration;
import stroom.feed.server.FeedStatusResource;
import stroom.feed.server.RemoteFeedServiceRPC;
import stroom.healthchecks.LogLevelInspector;
import stroom.importexport.server.ImportExportActionHandler;
import stroom.index.server.StroomIndexQueryResource;
import stroom.index.spring.IndexConfiguration;
import stroom.lifecycle.LifecycleService;
import stroom.logging.spring.EventLoggingConfiguration;
import stroom.pipeline.spring.PipelineConfiguration;
import stroom.proxy.guice.ProxyModule;
import stroom.proxy.handler.ForwardStreamHandlerFactory;
import stroom.proxy.handler.RemoteFeedStatusService;
import stroom.proxy.repo.ProxyLifecycle;
import stroom.proxy.repo.ProxyRepositoryManager;
import stroom.proxy.security.ProxySecurityFilter;
import stroom.proxy.servlet.ConfigServlet;
import stroom.proxy.servlet.ProxyStatusServlet;
import stroom.proxy.servlet.ProxyWelcomeServlet;
import stroom.ruleset.server.RuleSetResource;
import stroom.ruleset.server.RuleSetService;
import stroom.ruleset.shared.RuleSet;
import stroom.ruleset.spring.RuleSetConfiguration;
import stroom.script.server.ScriptServlet;
import stroom.script.spring.ScriptConfiguration;
import stroom.search.spring.SearchConfiguration;
import stroom.security.server.AuthorisationResource;
import stroom.security.server.JWTService;
import stroom.security.server.SecurityFilter;
import stroom.security.server.SessionListListener;
import stroom.security.server.SessionResource;
import stroom.security.spring.SecurityConfiguration;
import stroom.servicediscovery.ResourcePaths;
import stroom.servicediscovery.ServiceDiscovererImpl;
import stroom.servicediscovery.ServiceDiscoveryManager;
import stroom.servicediscovery.ServiceDiscoveryRegistrar;
import stroom.servlet.CacheControlFilter;
import stroom.servlet.DashboardServlet;
import stroom.servlet.DebugServlet;
import stroom.servlet.DynamicCSSServlet;
import stroom.servlet.EchoServlet;
import stroom.servlet.ExportConfigResource;
import stroom.servlet.HttpServletRequestFilter;
import stroom.servlet.ImportFileServlet;
import stroom.servlet.RejectPostFilter;
import stroom.servlet.SessionListServlet;
import stroom.servlet.SessionResourceStoreImpl;
import stroom.servlet.StatusServlet;
import stroom.servlet.StroomServlet;
import stroom.spring.MetaDataStatisticConfiguration;
import stroom.spring.PersistenceConfiguration;
import stroom.spring.ScopeConfiguration;
import stroom.spring.ServerComponentScanConfiguration;
import stroom.spring.ServerConfiguration;
import stroom.statistics.server.sql.search.SqlStatisticsQueryResource;
import stroom.statistics.spring.StatisticsConfiguration;
import stroom.util.HealthCheckUtils;
import stroom.util.config.StroomProperties;
import stroom.util.db.DbUtil;
import stroom.util.spring.StroomSpringProfiles;
import stroom.visualisation.spring.VisualisationConfiguration;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.ws.rs.client.Client;
import java.util.AbstractMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class App extends Application<Config> {
    private static final Logger LOGGER = LoggerFactory.getLogger(App.class);

    // This name is used by dropwizard metrics
    private static final String PROXY_JERSEY_CLIENT_NAME = "stroom-proxy_jersey_client";

    // All the servlet paths
    private static final String CLUSTER_CALL_RPC_PATH = ResourcePaths.ROOT_PATH + "/clustercall.rpc";
    private static final String CONFIG_PATH = ResourcePaths.ROOT_PATH + "/config";
    private static final String DASHBOARD_PATH = ResourcePaths.ROOT_PATH + "/dashboard";
    private static final String DATAFEED_PATH = ResourcePaths.ROOT_PATH + "/datafeed";
    private static final String DEBUG_PATH = ResourcePaths.ROOT_PATH + "/debug";
    private static final String DISPATCH_RPC_PATH = ResourcePaths.ROOT_PATH + "/dispatch.rpc";
    private static final String DYNAMIC_CSS_PATH = ResourcePaths.ROOT_PATH + "/dynamic.css";
    private static final String ECHO_PATH = ResourcePaths.ROOT_PATH + "/echo";
    private static final String GWT_REQUEST_PATH = ResourcePaths.ROOT_PATH + "/gwtRequest";
    private static final String IMPORT_FILE_RPC_PATH = ResourcePaths.ROOT_PATH + "/importfile.rpc";
    private static final String REMOTING_RPC_PATH = ResourcePaths.ROOT_PATH + "/remoting/remotefeedservice.rpc";
    private static final String RESOURCE_STORE_PATH = ResourcePaths.ROOT_PATH + "/resourcestore";
    private static final String SCRIPT_PATH = ResourcePaths.ROOT_PATH + "/script";
    private static final String SESSION_LIST_PATH = ResourcePaths.ROOT_PATH + "/sessionList";
    private static final String STATUS_PATH = ResourcePaths.ROOT_PATH + "/status";
    private static final String UI_PATH = ResourcePaths.ROOT_PATH + "/ui";

    private static String configPath;

    public static void main(final String[] args) throws Exception {
        if (args.length > 0) {
            configPath = args[args.length - 1];
        }

        // Hibernate requires JBoss Logging. The SLF4J API jar wasn't being detected so this sets it manually.
        System.setProperty("org.jboss.logging.provider", "slf4j");
        new App().run(args);
    }

    @Override
    public void initialize(final Bootstrap<Config> bootstrap) {
        // This allows us to use templating in the YAML configuration.
        bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
                bootstrap.getConfigurationSourceProvider(),
                new EnvironmentVariableSubstitutor(false)));
        bootstrap.addBundle(new AssetsBundle("/ui", ResourcePaths.ROOT_PATH, "index.html", "ui"));
    }

    @Override
    public void run(final Config configuration, final Environment environment) {
        // Add useful logging setup.
        registerLogConfiguration(environment);
        environment.healthChecks().register(LogLevelInspector.class.getName(), new LogLevelInspector());

        // We want Stroom to use the root path so we need to move Dropwizard's path.
        environment.jersey().setUrlPattern(ResourcePaths.API_PATH + "/*");

        // Set up a session manager for Jetty
        SessionHandler sessions = new SessionHandler();
        environment.servlets().setSessionHandler(sessions);

        // Configure Cross-Origin Resource Sharing.
        configureCors(environment);

        if ("proxy".equalsIgnoreCase(configuration.getMode())) {
            LOGGER.info("Starting Stroom Proxy");
            startProxy(configuration, environment);
        } else {
            LOGGER.info("Starting Stroom Application");
//            // Adding asset bundles this way is not normal but it is done so that proxy can serve it's own root page for now.
//            new AssetsBundle("/ui", "/", "stroom", "ui").run(environment);
            startApp(configuration, environment);
        }
    }


    private void startProxy(final Config configuration, final Environment environment) {

        // The jersey client is costly to create and is thread-safe so create one for the app
        // and make it injectable by guice
        final Client jerseyClient = createJerseyClient(
                configuration.getProxyConfig().getJerseyClientConfiguration(),
                environment);
        final ProxyModule proxyModule = new ProxyModule(configuration.getProxyConfig(), jerseyClient);

        final Injector injector = Guice.createInjector(proxyModule);

        final ServletContextHandler servletContextHandler = environment.getApplicationContext();

        HealthCheckRegistry healthCheckRegistry = environment.healthChecks();
        // Add health checks
        GuiceUtil.addHealthCheck(healthCheckRegistry, injector, DictionaryResource.class);
        GuiceUtil.addHealthCheck(healthCheckRegistry, injector, FeedStatusResource.class);
        GuiceUtil.addHealthCheck(healthCheckRegistry, injector, ForwardStreamHandlerFactory.class);
        GuiceUtil.addHealthCheck(healthCheckRegistry, injector, ProxyRepositoryManager.class);
        GuiceUtil.addHealthCheck(healthCheckRegistry, injector, RemoteFeedStatusService.class);
        GuiceUtil.addHealthCheck(healthCheckRegistry, injector, RuleSetResource.class);

        healthCheckRegistry.register(configuration.getProxyConfig().getClass().getName(), new HealthCheck() {
            @Override
            protected Result check() throws Exception {
                Map<String, Object> detailMap = HealthCheckUtils.beanToMap(configuration.getProxyConfig());

                // We don't really want passwords appearing on the admin page so mask them out.
                HealthCheckUtils.maskPasswords(detailMap);

                return Result.builder()
                        .healthy()
                        .withDetail("values", detailMap)
                        .build();
            }
        });

        // Add filters
        GuiceUtil.addFilter(servletContextHandler, injector, ProxySecurityFilter.class, "/*");

        // Add servlets
        ConfigServlet configServlet = new ConfigServlet(configPath);
        String configPathSpec = CONFIG_PATH;
        servletContextHandler.addServlet(new ServletHolder(configServlet), configPathSpec);
        healthCheckRegistry.register(configServlet.getClass().getName(), new HealthCheck() {
            @Override
            protected Result check() throws Exception {
                return Result.builder()
                        .healthy()
                        .withDetail("path", configPathSpec)
                        .build();
            }
        });

        GuiceUtil.addServlet(servletContextHandler, injector, DataFeedServlet.class, DATAFEED_PATH, healthCheckRegistry);
        GuiceUtil.addServlet(servletContextHandler, injector, DataFeedServlet.class, DATAFEED_PATH + "/*", healthCheckRegistry);
        GuiceUtil.addServlet(servletContextHandler, injector, ProxyWelcomeServlet.class, UI_PATH, healthCheckRegistry);
        GuiceUtil.addServlet(servletContextHandler, injector, ProxyStatusServlet.class, STATUS_PATH, healthCheckRegistry);
        GuiceUtil.addServlet(servletContextHandler, injector, DebugServlet.class, DEBUG_PATH, healthCheckRegistry);

        // Add resources.
        GuiceUtil.addResource(environment.jersey(), injector, DictionaryResource.class);
        GuiceUtil.addResource(environment.jersey(), injector, RuleSetResource.class);
        GuiceUtil.addResource(environment.jersey(), injector, FeedStatusResource.class);

        // Listen to the lifecycle of the Dropwizard app.
        GuiceUtil.manage(environment.lifecycle(), injector, ProxyLifecycle.class);

        // Sync content.
        if (configuration.getProxyConfig() != null &&
                configuration.getProxyConfig().getContentSyncConfig() != null &&
                configuration.getProxyConfig().getContentSyncConfig().isContentSyncEnabled()) {
            // Create a map of import handlers.
            final Map<String, ImportExportActionHandler> importExportActionHandlers = new HashMap<>();
            importExportActionHandlers.put(RuleSet.DOCUMENT_TYPE, injector.getInstance(RuleSetService.class));
            importExportActionHandlers.put(DictionaryDoc.ENTITY_TYPE, injector.getInstance(DictionaryStore.class));

            final ContentSyncService contentSyncService = new ContentSyncService(
                    configuration.getProxyConfig().getContentSyncConfig(), importExportActionHandlers);
            environment.lifecycle().manage(contentSyncService);
            GuiceUtil.addHealthCheck(healthCheckRegistry, contentSyncService);
        }
    }

    private Client createJerseyClient(final JerseyClientConfiguration jerseyClientConfiguration,
                                      final Environment environment) {

        // If the userAgent has not been explicitly set in the config then set it based
        // on the build version
        if (! jerseyClientConfiguration.getUserAgent().isPresent()) {
            final String userAgent = ForwardStreamHandlerFactory.getUserAgentString(null);
            LOGGER.info("Setting jersey client user agent string to [{}]", userAgent);
            jerseyClientConfiguration.setUserAgent(Optional.of(userAgent));
        }

        LOGGER.info("Creating jersey client {}", PROXY_JERSEY_CLIENT_NAME);
        return new JerseyClientBuilder(environment)
                .using(jerseyClientConfiguration)
                .build(PROXY_JERSEY_CLIENT_NAME)
                .register(LoggingFeature.class);
    }

    private void startApp(final Config configuration, final Environment environment) {
        // Get the external config.
        StroomProperties.setExternalConfigPath(configuration.getExternalConfig(), configPath);

        // Make sure we can connect to our databases, retrying as required.
        boolean didAllDbsConnect;
        didAllDbsConnect = waitForStroomDbConnection();
        didAllDbsConnect = waitForStatsDbConnection() && didAllDbsConnect;
        if (!didAllDbsConnect) {
            LOGGER.error("Can't connect to all databases, shutting down");
            System.exit(1);
        }

        // Start the spring context.
        LOGGER.info("Loading Spring context");
        final ApplicationContext applicationContext = loadApplicationContext(configuration, environment);

        final ServletContextHandler servletContextHandler = environment.getApplicationContext();

        // Add health checks
        if (StroomProperties.getBooleanProperty(
                ServiceDiscoveryManager.PROP_KEY_SERVICE_DISCOVERY_ENABLED, false)) {
            SpringUtil.addHealthCheck(environment.healthChecks(), applicationContext, ServiceDiscoveryRegistrar.class);
            SpringUtil.addHealthCheck(environment.healthChecks(), applicationContext, ServiceDiscovererImpl.class);
        }
        SpringUtil.addHealthCheck(environment.healthChecks(), applicationContext, StroomKafkaProducerFactoryService.class);
        SpringUtil.addHealthCheck(environment.healthChecks(), applicationContext, StroomElasticProducerFactoryService.class);
        SpringUtil.addHealthCheck(environment.healthChecks(), applicationContext, SqlStatisticsQueryResource.class);
        SpringUtil.addHealthCheck(environment.healthChecks(), applicationContext, StroomIndexQueryResource.class);
        SpringUtil.addHealthCheck(environment.healthChecks(), applicationContext, DictionaryResource.class);
        SpringUtil.addHealthCheck(environment.healthChecks(), applicationContext, RuleSetResource.class);
        SpringUtil.addHealthCheck(environment.healthChecks(), applicationContext, JWTService.class);
        SpringUtil.addHealthCheck(environment.healthChecks(), applicationContext, FeedStatusResource.class);

        // Add filters
        SpringUtil.addFilter(servletContextHandler, applicationContext, HttpServletRequestFilter.class, "/*");
        FilterUtil.addFilter(servletContextHandler, RejectPostFilter.class, "rejectPostFilter",
                ImmutableMap.of("rejectUri", "/"));

        final String cacheablePathsRegex = "^" + ResourcePaths.ROOT_PATH + "/script/?$";
        FilterUtil.addFilter(servletContextHandler, CacheControlFilter.class, "cacheControlFilter",
                ImmutableMap.of(
                        CacheControlFilter.INIT_PARAM_KEY_SECONDS, "600",
                        CacheControlFilter.INIT_PARAM_KEY_CACHEABLE_PATH_REGEX, cacheablePathsRegex));

        registerSecurityFilter(applicationContext, servletContextHandler);

        // Add servlets
        SpringUtil.addServlet(servletContextHandler, applicationContext, StroomServlet.class, UI_PATH);
        SpringUtil.addServlet(servletContextHandler, applicationContext, DashboardServlet.class, DASHBOARD_PATH);
        SpringUtil.addServlet(servletContextHandler, applicationContext, DynamicCSSServlet.class, DYNAMIC_CSS_PATH);
        SpringUtil.addServlet(servletContextHandler, applicationContext, DispatchService.class, DISPATCH_RPC_PATH);
        SpringUtil.addServlet(servletContextHandler, applicationContext, ImportFileServlet.class, IMPORT_FILE_RPC_PATH);
        SpringUtil.addServlet(servletContextHandler, applicationContext, ScriptServlet.class, SCRIPT_PATH);
        SpringUtil.addServlet(servletContextHandler, applicationContext, ClusterCallServiceRPC.class, CLUSTER_CALL_RPC_PATH);
        SpringUtil.addServlet(servletContextHandler, applicationContext, StatusServlet.class, STATUS_PATH);
        SpringUtil.addServlet(servletContextHandler, applicationContext, EchoServlet.class, ECHO_PATH);
        SpringUtil.addServlet(servletContextHandler, applicationContext, DebugServlet.class, DEBUG_PATH);
        SpringUtil.addServlet(servletContextHandler, applicationContext, SessionListServlet.class, SESSION_LIST_PATH);
        SpringUtil.addServlet(servletContextHandler, applicationContext, SessionResourceStoreImpl.class, RESOURCE_STORE_PATH + "+/*");
        SpringUtil.addServlet(servletContextHandler, applicationContext, SpringRequestFactoryServlet.class, GWT_REQUEST_PATH);
        SpringUtil.addServlet(servletContextHandler, applicationContext, RemoteFeedServiceRPC.class, REMOTING_RPC_PATH);
        SpringUtil.addServlet(servletContextHandler, applicationContext, DataFeedServlet.class, DATAFEED_PATH);
        SpringUtil.addServlet(servletContextHandler, applicationContext, DataFeedServlet.class, DATAFEED_PATH + "/*");

        // Add session listeners.
        SpringUtil.addServletListener(environment.servlets(), applicationContext, SessionListListener.class);

        // Add resources.
        SpringUtil.addResource(environment.jersey(), applicationContext, ExportConfigResource.class);
        SpringUtil.addResource(environment.jersey(), applicationContext, DictionaryResource.class);
        SpringUtil.addResource(environment.jersey(), applicationContext, RuleSetResource.class);
        SpringUtil.addResource(environment.jersey(), applicationContext, StroomIndexQueryResource.class);
        SpringUtil.addResource(environment.jersey(), applicationContext, SqlStatisticsQueryResource.class);
        SpringUtil.addResource(environment.jersey(), applicationContext, AuthorisationResource.class);
        SpringUtil.addResource(environment.jersey(), applicationContext, SessionResource.class);
        SpringUtil.addResource(environment.jersey(), applicationContext, FeedStatusResource.class);

        // Map exceptions to helpful HTTP responses
        environment.jersey().register(PermissionExceptionMapper.class);

        // Listen to the lifecycle of the Dropwizard app.
        SpringUtil.manage(environment.lifecycle(), applicationContext, LifecycleService.class);
    }

    private void registerSecurityFilter(final ApplicationContext applicationContext,
                                        final ServletContextHandler servletContextHandler) {
        // Specify a number of regexes to bypass normal authentication for
        final Map<String, String> initParams = ImmutableMap.<String, String>builder()
                .put(SecurityFilter.API_PATH_REGEX_PARAM, ResourcePaths.API_PATH + ".*")
                .put(SecurityFilter.DISPATCH_PATH_REGEX_PARAM, DISPATCH_RPC_PATH + "$")
                // define a number of path regexes to bypass user authentication as there is no user
                .put(makeBypassAuthInitParam(CLUSTER_CALL_RPC_PATH, false))
                // need to cater for /stroom/datafeed and /stroom/datafeed/* for some reason
                .put(makeBypassAuthInitParam(DATAFEED_PATH, true))
                .put(makeBypassAuthInitParam(STATUS_PATH, false))
                .put(makeBypassAuthInitParam(ECHO_PATH, false))
                .put(makeBypassAuthInitParam(DEBUG_PATH, false))
                .put(makeBypassAuthInitParam(REMOTING_RPC_PATH, false))
                .build();

        SpringUtil.addFilter(servletContextHandler, applicationContext, SecurityFilter.class, "/*", initParams);
    }

    private Map.Entry<String, String> makeBypassAuthInitParam(final String path, final boolean allowPartialMatch) {
        final String regex = "^" + path + (allowPartialMatch ? ".*" : "$");
        return new AbstractMap.SimpleEntry<>(SecurityFilter.makeBypassAuthInitKey(path), regex);
    }

    private boolean waitForStroomDbConnection() {
        final String driverClassname = StroomProperties.getProperty(ConnectionUtil.JDBC_DRIVER_CLASS_NAME);
        final String driverUrl = StroomProperties.getProperty(ConnectionUtil.JDBC_DRIVER_URL);
        final String driverUsername = StroomProperties.getProperty(ConnectionUtil.JDBC_DRIVER_USERNAME);
        final String driverPassword = StroomProperties.getProperty(ConnectionUtil.JDBC_DRIVER_PASSWORD);

        return DbUtil.waitForConnection(driverClassname, driverUrl, driverUsername, driverPassword);
    }

    private boolean waitForStatsDbConnection() {
        final String driverClassname = StroomProperties.getProperty("stroom.statistics.sql.jdbcDriverClassName");
        final String driverUrl = StroomProperties.getProperty("stroom.statistics.sql.jdbcDriverUrl|trace");
        final String driverUsername = StroomProperties.getProperty("stroom.statistics.sql.jdbcDriverUsername");
        final String driverPassword = StroomProperties.getProperty("stroom.statistics.sql.jdbcDriverPassword");

        return DbUtil.waitForConnection(driverClassname, driverUrl, driverUsername, driverPassword);
    }

    private ApplicationContext loadApplicationContext(final Configuration configuration, final Environment environment) {
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
                RuleSetConfiguration.class
        );
        try {
            applicationContext.refresh();
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw e;
        }
        return applicationContext;
    }

    private static void configureCors(io.dropwizard.setup.Environment environment) {
        FilterRegistration.Dynamic cors = environment.servlets().addFilter("CORS", CrossOriginFilter.class);
        cors.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
        cors.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET,PUT,POST,DELETE,OPTIONS");
        cors.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*");
        cors.setInitParameter(CrossOriginFilter.ALLOWED_HEADERS_PARAM, "*");
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
