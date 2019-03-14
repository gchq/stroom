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

package stroom.app;

import com.codahale.metrics.health.HealthCheckRegistry;
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
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.app.guice.AppModule;
import stroom.cluster.impl.ClusterCallServiceRPC;
import stroom.data.store.impl.ImportFileServlet;
import stroom.dispatch.shared.DispatchService;
import stroom.core.feed.RemoteFeedServiceRPC;
import stroom.lifecycle.impl.LifecycleService;
import stroom.receive.common.DebugServlet;
import stroom.receive.common.ReceiveDataServlet;
import stroom.resource.impl.SessionResourceStoreImpl;
import stroom.script.ScriptServlet;
import stroom.security.impl.SecurityFilter;
import stroom.core.servicediscovery.ResourcePaths;
import stroom.core.servlet.CacheControlFilter;
import stroom.core.servlet.DashboardServlet;
import stroom.core.servlet.DynamicCSSServlet;
import stroom.core.servlet.EchoServlet;
import stroom.core.servlet.HttpServletRequestFilter;
import stroom.core.servlet.RejectPostFilter;
import stroom.core.servlet.SessionListListener;
import stroom.core.servlet.SessionListServlet;
import stroom.core.servlet.StatusServlet;
import stroom.core.servlet.StroomServlet;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import java.util.EnumSet;

public class App extends Application<Config> {
    private static final Logger LOGGER = LoggerFactory.getLogger(App.class);
//
//    private static String configPath;

    public static void main(final String[] args) throws Exception {
//        if (args.length > 0) {
//            configPath = args[args.length - 1];
//        }

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

        LOGGER.info("Starting app");

//        switch (configuration.getMode()) {
//            case PROXY:
//                startProxy(configuration, environment);
//                break;
//            case APP:
                // Adding asset bundles this way is not normal but it is done so that
                // proxy can serve it's own root page for now.
//            new AssetsBundle("/ui", "/", "stroom", "ui").run(environment);
                startApp(configuration, environment);
//                break;
//            default:
//                throw new RuntimeException(LogUtil.message(
//                        "Unexpected mode {}", configuration.getMode()));
//        }
    }

//    private void startProxy(final Config configuration, final Environment environment) {
//        LOGGER.info("Starting Stroom Proxy");
//
//        final ProxyModule proxyModule = new ProxyModule(configuration.getProxyConfig());
//        final Injector injector = Guice.createInjector(proxyModule);
//
//        final ServletContextHandler servletContextHandler = environment.getApplicationContext();
//
//        // Add health checks
//        final HealthCheckRegistry healthCheckRegistry = environment.healthChecks();
//        injector.getInstance(HealthChecks.class).register();
//
////        GuiceUtil.addHealthCheck(environment.healthChecks(), injector, ByteBufferPool.class);
////        GuiceUtil.addHealthCheck(environment.healthChecks(), injector, DictionaryResource.class);
////        GuiceUtil.addHealthCheck(environment.healthChecks(), injector, DictionaryResource2.class);
////        GuiceUtil.addHealthCheck(environment.healthChecks(), injector, RuleSetResource.class);
////        GuiceUtil.addHealthCheck(environment.healthChecks(), injector, RuleSetResource2.class);
////        GuiceUtil.addHealthCheck(healthCheckRegistry, injector, ForwardStreamHandlerFactory.class);
////        GuiceUtil.addHealthCheck(
////                environment.healthChecks(),
////                injector.getInstance(RefDataStoreFactory.class).getOffHeapStore());
//
//        healthCheckRegistry.register(configuration.getProxyConfig().getClass().getName(), new HealthCheck() {
//            @Override
//            protected Result check() {
//                Map<String, Object> detailMap = HealthCheckUtils.beanToMap(configuration.getProxyConfig());
//                return Result.builder()
//                        .healthy()
//                        .withDetail("values", detailMap)
//                        .build();
//            }
//        });
//
//        // Add filters
//        GuiceUtil.addFilter(servletContextHandler, injector, ProxySecurityFilter.class, "/*");
//
//        // Add servlets
//        ConfigServlet configServlet = new ConfigServlet(configPath);
//        String configPathSpec = ResourcePaths.ROOT_PATH + "/config";
//        servletContextHandler.addServlet(new ServletHolder(configServlet), configPathSpec);
//        healthCheckRegistry.register(configServlet.getClass().getName(), new HealthCheck() {
//            @Override
//            protected Result check() {
//                return Result.builder()
//                        .healthy()
//                        .withDetail("path", configPathSpec)
//                        .build();
//            }
//        });
//
//        GuiceUtil.addServlet(servletContextHandler, injector, ReceiveDataServlet.class, ResourcePaths.ROOT_PATH + "/datafeed", healthCheckRegistry);
//        GuiceUtil.addServlet(servletContextHandler, injector, ReceiveDataServlet.class, ResourcePaths.ROOT_PATH + "/datafeed/*", healthCheckRegistry);
//        GuiceUtil.addServlet(servletContextHandler, injector, ProxyWelcomeServlet.class, ResourcePaths.ROOT_PATH + "/ui", healthCheckRegistry);
//        GuiceUtil.addServlet(servletContextHandler, injector, ProxyStatusServlet.class, ResourcePaths.ROOT_PATH + "/status", healthCheckRegistry);
//        GuiceUtil.addServlet(servletContextHandler, injector, DebugServlet.class, ResourcePaths.ROOT_PATH + "/debug", healthCheckRegistry);
//
//        // Add resources.
//        GuiceUtil.addResource(environment.jersey(), injector, DictionaryResource.class);
//        GuiceUtil.addResource(environment.jersey(), injector, DictionaryResource2.class);
//        GuiceUtil.addResource(environment.jersey(), injector, ReceiveDataRuleSetResource.class);
//        GuiceUtil.addResource(environment.jersey(), injector, ReceiveDataRuleSetResource2.class);
//
//        // Listen to the lifecycle of the Dropwizard app.
//        GuiceUtil.manage(environment.lifecycle(), injector, ProxyLifecycle.class);
//
//        // Sync content.
//        if (configuration.getProxyConfig() != null &&
//                configuration.getProxyConfig().getContentSyncConfig() != null &&
//                configuration.getProxyConfig().getContentSyncConfig().isContentSyncEnabled()) {
//            // Create a map of import handlers.
//            final Map<String, ImportExportActionHandler> importExportActionHandlers = new HashMap<>();
//            importExportActionHandlers.put(ReceiveDataRules.DOCUMENT_TYPE, injector.getInstance(ReceiveDataRuleSetService.class));
//            importExportActionHandlers.put(DictionaryDoc.ENTITY_TYPE, injector.getInstance(DictionaryStore.class));
//
//            final ContentSyncService contentSyncService = new ContentSyncService(
//                    configuration.getProxyConfig().getContentSyncConfig(), importExportActionHandlers);
//            environment.lifecycle().manage(contentSyncService);
//            GuiceUtil.addHealthCheck(healthCheckRegistry, contentSyncService);
//        }
//    }

    private void startApp(final Config configuration, final Environment environment) {
        LOGGER.info("Starting Stroom Application");

        final AppModule appModule = new AppModule(configuration, environment);
        final Injector injector = Guice.createInjector(appModule);

        final ServletContextHandler servletContextHandler = environment.getApplicationContext();

        // Add health checks
        final HealthCheckRegistry healthCheckRegistry = environment.healthChecks();
        injector.getInstance(HealthChecks.class).register();

        // Add filters
        GuiceUtil.addFilter(servletContextHandler, injector, HttpServletRequestFilter.class, "/*");
        FilterUtil.addFilter(servletContextHandler, RejectPostFilter.class, "rejectPostFilter").setInitParameter("rejectUri", "/");
        FilterUtil.addFilter(servletContextHandler, CacheControlFilter.class, "cacheControlFilter").setInitParameter("seconds", "600");
        GuiceUtil.addFilter(servletContextHandler, injector, SecurityFilter.class, "/*");

        // Add servlets
        GuiceUtil.addServlet(servletContextHandler, injector, StroomServlet.class, ResourcePaths.ROOT_PATH + "/ui", healthCheckRegistry);
        GuiceUtil.addServlet(servletContextHandler, injector, DashboardServlet.class, ResourcePaths.ROOT_PATH + "/dashboard", healthCheckRegistry);
        GuiceUtil.addServlet(servletContextHandler, injector, DynamicCSSServlet.class, ResourcePaths.ROOT_PATH + "/dynamic.css", healthCheckRegistry);
        GuiceUtil.addServlet(servletContextHandler, injector, DispatchService.class, ResourcePaths.ROOT_PATH + "/dispatch.rpc", healthCheckRegistry);
        GuiceUtil.addServlet(servletContextHandler, injector, ImportFileServlet.class, ResourcePaths.ROOT_PATH + "/importfile.rpc", healthCheckRegistry);
        GuiceUtil.addServlet(servletContextHandler, injector, ScriptServlet.class, ResourcePaths.ROOT_PATH + "/script", healthCheckRegistry);
        GuiceUtil.addServlet(servletContextHandler, injector, ClusterCallServiceRPC.class, ResourcePaths.ROOT_PATH + "/clustercall.rpc", healthCheckRegistry);
        GuiceUtil.addServlet(servletContextHandler, injector, StatusServlet.class, ResourcePaths.ROOT_PATH + "/status", healthCheckRegistry);
        GuiceUtil.addServlet(servletContextHandler, injector, EchoServlet.class, ResourcePaths.ROOT_PATH + "/echo", healthCheckRegistry);
        GuiceUtil.addServlet(servletContextHandler, injector, DebugServlet.class, ResourcePaths.ROOT_PATH + "/debug", healthCheckRegistry);
        GuiceUtil.addServlet(servletContextHandler, injector, SessionListServlet.class, ResourcePaths.ROOT_PATH + "/sessionList", healthCheckRegistry);
        GuiceUtil.addServlet(servletContextHandler, injector, SessionResourceStoreImpl.class, ResourcePaths.ROOT_PATH + "/resourcestore/*", healthCheckRegistry);
        GuiceUtil.addServlet(servletContextHandler, injector, RemoteFeedServiceRPC.class, ResourcePaths.ROOT_PATH + "/remoting/remotefeedservice.rpc", healthCheckRegistry);
        GuiceUtil.addServlet(servletContextHandler, injector, ReceiveDataServlet.class, ResourcePaths.ROOT_PATH + "/datafeed", healthCheckRegistry);
        GuiceUtil.addServlet(servletContextHandler, injector, ReceiveDataServlet.class, ResourcePaths.ROOT_PATH + "/datafeed/*", healthCheckRegistry);

        // Add session listeners.
        GuiceUtil.addServletListener(environment.servlets(), injector, SessionListListener.class);

        // Add all injectable rest resources.
        injector.getInstance(RestResources.class).register(environment.jersey());

        // Map exceptions to helpful HTTP responses
        environment.jersey().register(PermissionExceptionMapper.class);

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
