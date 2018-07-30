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

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.servlets.tasks.LogConfigurationTask;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cluster.ClusterCallServiceRPC;
import stroom.content.ContentSyncService;
import stroom.content.ProxySecurityFilter;
import stroom.datafeed.DataFeedServlet;
import stroom.dictionary.DictionaryResource;
import stroom.dictionary.DictionaryResource2;
import stroom.dictionary.DictionaryStore;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.dispatch.shared.DispatchService;
import stroom.explorer.ExplorerResource;
import stroom.feed.RemoteFeedServiceRPC;
import stroom.guice.AppModule;
import stroom.importexport.ImportExportActionHandler;
import stroom.index.StroomIndexQueryResource;
import stroom.lifecycle.LifecycleService;
import stroom.persist.PersistLifecycle;
import stroom.resource.PipelineResource;
import stroom.proxy.guice.ProxyModule;
import stroom.proxy.repo.ProxyLifecycle;
import stroom.proxy.servlet.ConfigServlet;
import stroom.proxy.servlet.ProxyStatusServlet;
import stroom.proxy.servlet.ProxyWelcomeServlet;
import stroom.resource.ElementResource;
import stroom.resource.SessionResourceStoreImpl;
import stroom.resource.XsltResource;
import stroom.ruleset.RuleSetResource;
import stroom.ruleset.RuleSetResource2;
import stroom.ruleset.RuleSetService;
import stroom.ruleset.shared.RuleSet;
import stroom.script.ScriptServlet;
import stroom.security.AuthorisationResource;
import stroom.security.SecurityFilter;
import stroom.security.SessionResource;
import stroom.servicediscovery.ResourcePaths;
import stroom.servicediscovery.ServiceDiscovererImpl;
import stroom.servicediscovery.ServiceDiscoveryRegistrar;
import stroom.servlet.CacheControlFilter;
import stroom.servlet.DashboardServlet;
import stroom.servlet.DebugServlet;
import stroom.servlet.DynamicCSSServlet;
import stroom.servlet.EchoServlet;
import stroom.servlet.ExportConfigServlet;
import stroom.servlet.HttpServletRequestFilter;
import stroom.servlet.ImportFileServlet;
import stroom.servlet.RejectPostFilter;
import stroom.servlet.SessionListListener;
import stroom.servlet.SessionListServlet;
import stroom.servlet.StatusServlet;
import stroom.servlet.StroomServlet;
import stroom.statistics.sql.search.SqlStatisticsQueryResource;
import stroom.streamstore.StreamAttributeMapResource;
import stroom.streamtask.resource.StreamTaskResource;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public class App extends Application<Config> {
    private static final Logger LOGGER = LoggerFactory.getLogger(App.class);

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
            startProxy(configuration, environment);
        } else {
//            // Adding asset bundles this way is not normal but it is done so that proxy can serve it's own root page for now.
//            new AssetsBundle("/ui", "/", "stroom", "ui").run(environment);
            startApp(configuration, environment);
        }
    }

    private void startProxy(final Config configuration, final Environment environment) {
        final ProxyModule proxyModule = new ProxyModule(configuration.getProxyConfig());
        final Injector injector = Guice.createInjector(proxyModule);

        final ServletContextHandler servletContextHandler = environment.getApplicationContext();

        // Add health checks
        GuiceUtil.addHealthCheck(environment.healthChecks(), injector, DictionaryResource.class);
        GuiceUtil.addHealthCheck(environment.healthChecks(), injector, DictionaryResource2.class);
        GuiceUtil.addHealthCheck(environment.healthChecks(), injector, RuleSetResource.class);
        GuiceUtil.addHealthCheck(environment.healthChecks(), injector, RuleSetResource2.class);

        // Add filters
        GuiceUtil.addFilter(servletContextHandler, injector, ProxySecurityFilter.class, "/*");

        // Add servlets
        servletContextHandler.addServlet(new ServletHolder(new ConfigServlet(configPath)), ResourcePaths.ROOT_PATH + "/config");
        GuiceUtil.addServlet(servletContextHandler, injector, DataFeedServlet.class, ResourcePaths.ROOT_PATH + "/datafeed");
        GuiceUtil.addServlet(servletContextHandler, injector, DataFeedServlet.class, ResourcePaths.ROOT_PATH + "/datafeed/*");
        GuiceUtil.addServlet(servletContextHandler, injector, ProxyWelcomeServlet.class, ResourcePaths.ROOT_PATH + "/ui");
        GuiceUtil.addServlet(servletContextHandler, injector, ProxyStatusServlet.class, ResourcePaths.ROOT_PATH + "/status");
        GuiceUtil.addServlet(servletContextHandler, injector, DebugServlet.class, ResourcePaths.ROOT_PATH + "/debug");

        // Add resources.
        GuiceUtil.addResource(environment.jersey(), injector, DictionaryResource.class);
        GuiceUtil.addResource(environment.jersey(), injector, DictionaryResource2.class);
        GuiceUtil.addResource(environment.jersey(), injector, RuleSetResource.class);
        GuiceUtil.addResource(environment.jersey(), injector, RuleSetResource2.class);

        // Listen to the lifecycle of the Dropwizard app.
        GuiceUtil.manage(environment.lifecycle(), injector, ProxyLifecycle.class);

        // Sync content.
        if (configuration.getProxyConfig() != null && configuration.getProxyConfig().getContentSyncConfig() != null) {
            // Create a map of import handlers.
            final Map<String, ImportExportActionHandler> importExportActionHandlers = new HashMap<>();
            importExportActionHandlers.put(RuleSet.DOCUMENT_TYPE, injector.getInstance(RuleSetService.class));
            importExportActionHandlers.put(DictionaryDoc.ENTITY_TYPE, injector.getInstance(DictionaryStore.class));

            final ContentSyncService contentSyncService = new ContentSyncService(configuration.getProxyConfig().getContentSyncConfig(), importExportActionHandlers);
            environment.lifecycle().manage(contentSyncService);
        }
    }

    private void startApp(final Config configuration, final Environment environment) {
        final AppModule appModule = new AppModule();
        final Injector injector = Guice.createInjector(appModule);

        // Start the persistence service. This needs to be done before anything else as other filters and services rely on it.
        injector.getInstance(PersistLifecycle.class).startPersistence();

        final ServletContextHandler servletContextHandler = environment.getApplicationContext();

        // Add health checks
        GuiceUtil.addHealthCheck(environment.healthChecks(), injector, ServiceDiscoveryRegistrar.class);
        GuiceUtil.addHealthCheck(environment.healthChecks(), injector, ServiceDiscovererImpl.class);
        GuiceUtil.addHealthCheck(environment.healthChecks(), injector, SqlStatisticsQueryResource.class);
        GuiceUtil.addHealthCheck(environment.healthChecks(), injector, StroomIndexQueryResource.class);
        GuiceUtil.addHealthCheck(environment.healthChecks(), injector, DictionaryResource.class);
        GuiceUtil.addHealthCheck(environment.healthChecks(), injector, DictionaryResource2.class);
        GuiceUtil.addHealthCheck(environment.healthChecks(), injector, RuleSetResource.class);
        GuiceUtil.addHealthCheck(environment.healthChecks(), injector, RuleSetResource2.class);

        // Add filters
        GuiceUtil.addFilter(servletContextHandler, injector, HttpServletRequestFilter.class, "/*");
        FilterUtil.addFilter(servletContextHandler, RejectPostFilter.class, "rejectPostFilter").setInitParameter("rejectUri", "/");
        FilterUtil.addFilter(servletContextHandler, CacheControlFilter.class, "cacheControlFilter").setInitParameter("seconds", "600");
        GuiceUtil.addFilter(servletContextHandler, injector, SecurityFilter.class, "/*");

        // Add servlets
        GuiceUtil.addServlet(servletContextHandler, injector, StroomServlet.class, ResourcePaths.ROOT_PATH + "/ui");
        GuiceUtil.addServlet(servletContextHandler, injector, DashboardServlet.class, ResourcePaths.ROOT_PATH + "/dashboard");
        GuiceUtil.addServlet(servletContextHandler, injector, DynamicCSSServlet.class, ResourcePaths.ROOT_PATH + "/dynamic.css");
        GuiceUtil.addServlet(servletContextHandler, injector, DispatchService.class, ResourcePaths.ROOT_PATH + "/dispatch.rpc");
        GuiceUtil.addServlet(servletContextHandler, injector, ImportFileServlet.class, ResourcePaths.ROOT_PATH + "/importfile.rpc");
        GuiceUtil.addServlet(servletContextHandler, injector, ScriptServlet.class, ResourcePaths.ROOT_PATH + "/script");
        GuiceUtil.addServlet(servletContextHandler, injector, ClusterCallServiceRPC.class, ResourcePaths.ROOT_PATH + "/clustercall.rpc");
        GuiceUtil.addServlet(servletContextHandler, injector, ExportConfigServlet.class, ResourcePaths.ROOT_PATH + "/export");
        GuiceUtil.addServlet(servletContextHandler, injector, StatusServlet.class, ResourcePaths.ROOT_PATH + "/status");
        GuiceUtil.addServlet(servletContextHandler, injector, EchoServlet.class, ResourcePaths.ROOT_PATH + "/echo");
        GuiceUtil.addServlet(servletContextHandler, injector, DebugServlet.class, ResourcePaths.ROOT_PATH + "/debug");
        GuiceUtil.addServlet(servletContextHandler, injector, SessionListServlet.class, ResourcePaths.ROOT_PATH + "/sessionList");
        GuiceUtil.addServlet(servletContextHandler, injector, SessionResourceStoreImpl.class, ResourcePaths.ROOT_PATH + "/resourcestore/*");
        GuiceUtil.addServlet(servletContextHandler, injector, RemoteFeedServiceRPC.class, ResourcePaths.ROOT_PATH + "/remoting/remotefeedservice.rpc");
        GuiceUtil.addServlet(servletContextHandler, injector, DataFeedServlet.class, ResourcePaths.ROOT_PATH + "/datafeed");
        GuiceUtil.addServlet(servletContextHandler, injector, DataFeedServlet.class, ResourcePaths.ROOT_PATH + "/datafeed/*");

        // Add session listeners.
        GuiceUtil.addServletListener(environment.servlets(), injector, SessionListListener.class);

        // Add resources.
        GuiceUtil.addResource(environment.jersey(), injector, DictionaryResource.class);
        GuiceUtil.addResource(environment.jersey(), injector, DictionaryResource2.class);
        GuiceUtil.addResource(environment.jersey(), injector, RuleSetResource.class);
        GuiceUtil.addResource(environment.jersey(), injector, RuleSetResource2.class);
        GuiceUtil.addResource(environment.jersey(), injector, StroomIndexQueryResource.class);
        GuiceUtil.addResource(environment.jersey(), injector, SqlStatisticsQueryResource.class);
        GuiceUtil.addResource(environment.jersey(), injector, AuthorisationResource.class);
        GuiceUtil.addResource(environment.jersey(), injector, StreamTaskResource.class);
        GuiceUtil.addResource(environment.jersey(), injector, PipelineResource.class);
        GuiceUtil.addResource(environment.jersey(), injector, XsltResource.class);
        GuiceUtil.addResource(environment.jersey(), injector, ExplorerResource.class);
        GuiceUtil.addResource(environment.jersey(), injector, ElementResource.class);
        GuiceUtil.addResource(environment.jersey(), injector, SessionResource.class);
        GuiceUtil.addResource(environment.jersey(), injector, StreamAttributeMapResource.class);

        // Listen to the lifecycle of the Dropwizard app.
        GuiceUtil.manage(environment.lifecycle(), injector, LifecycleService.class);
    }

    private static void configureCors(io.dropwizard.setup.Environment environment) {
        FilterRegistration.Dynamic cors = environment.servlets().addFilter("CORS", CrossOriginFilter.class);
        cors.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
        cors.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET,PUT,POST,DELETE,OPTIONS,PATCH");
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
