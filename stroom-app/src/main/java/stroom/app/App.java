/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.app.commands.CreateAccountCommand;
import stroom.app.commands.CreateApiKeyCommand;
import stroom.app.commands.DbMigrationCommand;
import stroom.app.commands.ManageUsersCommand;
import stroom.app.commands.ResetPasswordCommand;
import stroom.app.guice.AppModule;
import stroom.config.app.AppConfig;
import stroom.config.app.Config;
import stroom.config.app.SecurityConfig;
import stroom.config.app.SessionConfig;
import stroom.config.app.SessionCookieConfig;
import stroom.config.app.StroomYamlUtil;
import stroom.config.global.impl.ConfigMapper;
import stroom.dropwizard.common.AdminServlets;
import stroom.dropwizard.common.Filters;
import stroom.dropwizard.common.HealthChecks;
import stroom.dropwizard.common.ManagedServices;
import stroom.dropwizard.common.RestResources;
import stroom.dropwizard.common.Servlets;
import stroom.dropwizard.common.SessionListeners;
import stroom.event.logging.rs.api.RestResourceAutoLogger;
import stroom.node.impl.NodeConfig;
import stroom.security.impl.AuthenticationConfig;
import stroom.security.openid.api.AbstractOpenIdConfig;
import stroom.security.openid.api.IdpType;
import stroom.util.authentication.DefaultOpenIdCredentials;
import stroom.util.config.AppConfigValidator;
import stroom.util.config.ConfigValidator;
import stroom.util.config.PropertyPathDecorator;
import stroom.util.io.DirProvidersModule;
import stroom.util.io.FileUtil;
import stroom.util.io.HomeDirProvider;
import stroom.util.io.PathConfig;
import stroom.util.io.StroomPathConfig;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.DefaultLoggingFilter;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.servlet.SessionUtil;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourcePaths;
import stroom.util.time.StroomDuration;
import stroom.util.validation.ValidationModule;
import stroom.util.yaml.YamlUtil;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.jersey.sessions.SessionFactoryProvider;
import io.dropwizard.servlets.tasks.LogConfigurationTask;
import jakarta.inject.Inject;
import jakarta.validation.ValidatorFactory;
import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.session.SessionHandler;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Objects;

public class App extends Application<Config> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(App.class);

    private static final String APP_NAME = "Stroom";

    @Inject
    private HealthChecks healthChecks;
    @Inject
    private Filters filters;
    @Inject
    private Servlets servlets;
    @Inject
    private AdminServlets adminServlets;
    @Inject
    private SessionListeners sessionListeners;
    @Inject
    private RestResources restResources;
    @Inject
    private ManagedServices managedServices;
    @Inject
    private RestResourceAutoLogger resourceAutoLogger;

    // Injected manually
    private HomeDirProvider homeDirProvider;
    private TempDirProvider tempDirProvider;

    private final Path configFile;

    // This is an additional injector for use only with jakarta.validation. It means we can do validation
    // of the yaml file before our main injector has been created and also so we can use our custom
    // validation annotations with REST services (see initialize() method). It feels a bit wrong having two
    // injectors running but not sure how else we could do this unless Guice is not used for the validators.
    private final Injector validationOnlyInjector;

    // Needed for DropwizardExtensionsSupport
    public App() {
        configFile = Paths.get("PATH_NOT_SUPPLIED");
        validationOnlyInjector = createValidationInjector(configFile);
    }

    App(final Path configFile) {
        this.configFile = configFile;
        validationOnlyInjector = createValidationInjector(configFile);
    }

    public static void main(final String[] args) throws Exception {
        // hibernate-validator seems to use jboss-logging which spits the following ERROR
        // out to the console if this prop is not set:
        //   ERROR StatusLogger Log4j2 could not find a logging implementation.
        //   Please add log4j-core to the classpath. Using SimpleLogger to log to the console...
        System.setProperty("org.jboss.logging.provider", "slf4j");

        final Path yamlConfigFile = YamlUtil.getYamlFileFromArgs(args);
        new App(yamlConfigFile).run(args);
    }

    @Override
    public String getName() {
        return APP_NAME;
    }

    @Override
    public void initialize(final Bootstrap<Config> bootstrap) {

        // Dropwizard 2.x no longer fails on unknown properties by default but we want it to.
        bootstrap.getObjectMapper()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // This allows us to use env var templating and relative (to stroom home) paths in the YAML configuration.
        bootstrap.setConfigurationSourceProvider(StroomYamlUtil.createConfigurationSourceProvider(
                bootstrap.getConfigurationSourceProvider(), true));

        // Add the GWT UI assets.
        bootstrap.addBundle(new DynamicAssetsBundle(
                ResourcePaths.UI_PATH,
                ResourcePaths.UI_PATH,
                "index.html",
                ResourcePaths.UI_SERVLET_NAME));

        // Admin servlet for Prometheus to scrape (pull) metrics
//        bootstrap.addBundle(new PrometheusBundle());

        addCliCommands(bootstrap);

        // If we want to use jakarta.validation on our rest resources with our own custom validation annotations
        // then we need to set the ValidatorFactory. As our main Guice Injector is not available, yet we need to
        // create one just for the REST validation
        bootstrap.setValidatorFactory(validationOnlyInjector.getInstance(ValidatorFactory.class));
    }

    private void addCliCommands(final Bootstrap<Config> bootstrap) {
        // Add a DW Command so we can run the full migration without running the
        // http server
        bootstrap.addCommand(new DbMigrationCommand(configFile));
        bootstrap.addCommand(new CreateAccountCommand(configFile));
        bootstrap.addCommand(new CreateApiKeyCommand(configFile));
        bootstrap.addCommand(new ResetPasswordCommand(configFile));
        bootstrap.addCommand(new ManageUsersCommand(configFile));
    }

    @Override
    public void run(final Config configuration, final Environment environment) {

        Objects.requireNonNull(configFile, () ->
                LogUtil.message("No config YAML file supplied in arguments"));

        LOGGER.info("Using application configuration file {}", configFile.toAbsolutePath().normalize());

        validateAppConfig(configuration, configFile);

        // Initialise all the DB connections and app config; and run all the Flyway migrations
        // if needed, then return the injector used.
        final Injector bootStrapInjector = BootstrapUtil.bootstrapApplication(
                configuration, environment, configFile);

        this.homeDirProvider = bootStrapInjector.getInstance(HomeDirProvider.class);
        this.tempDirProvider = bootStrapInjector.getInstance(TempDirProvider.class);

        // Ensure we have our home/temp dirs set up
        FileUtil.ensureDirExists(homeDirProvider.get());
        FileUtil.ensureDirExists(tempDirProvider.get());

        LOGGER.info("Completed initialisation of database connections and application configuration");

        // Merge the sparse de-serialised config with our default AppConfig tree
        // so we have a full config tree but with any yaml overrides
//        final AppConfig mergedAppConfig = ConfigMapper.buildMergedAppConfig(configFile);
//        configuration.setAppConfig(mergedAppConfig);

        // Turn on Jersey logging of request/response payloads
        // I can't seem to get this to work unless Level is SEVERE
        // TODO need to establish if there is a performance hit for using the JUL to SLF bridge
        //   see http://www.slf4j.org/legacy.html#jul-to-slf4j
        environment.jersey().register(DefaultLoggingFilter.createWithDefaults());

        // Add useful logging setup.
        registerLogConfiguration(environment);

        // We want Stroom to use the root path so we need to move Dropwizard's path.
        environment.jersey().setUrlPattern(ResourcePaths.API_ROOT_PATH + "/*");

        // Need to get these config classed from ConfigMapper as the main appInjector is not created yet
        // and configuration only holds the YAML view of the config, not the DB view.
        final ConfigMapper configMapper = bootStrapInjector.getInstance(ConfigMapper.class);
        final SessionCookieConfig sessionCookieConfig = configMapper.getConfigObject(SessionCookieConfig.class);
//        final CorsConfig corsConfig = configMapper.getConfigObject(CorsConfig.class);
        final SessionConfig sessionConfig = configMapper.getConfigObject(SessionConfig.class);

        // Set up a session handler for Jetty
        configureSessionHandling(environment, sessionConfig);
        configureSessionCookie(environment, sessionCookieConfig);

//        // Configure Cross-Origin Resource Sharing.
//        configureCors(environment, corsConfig);

        LOGGER.info("Starting Stroom Application");

        // Inherit all the bindings from the bootStrapInjector
        final Injector appInjector = bootStrapInjector.createChildInjector(new AppModule());
        appInjector.injectMembers(this);

        //Register REST Resource Auto Logger to automatically log calls to suitably annotated resources/methods
        //Note that if autologger is not required, and the next line removed, then it will be necessary to
        //register a DelegatingExceptionMapper directly instead.
        environment.jersey().register(resourceAutoLogger);

        // Add health checks
        healthChecks.register();
        // Add filters
        filters.register();
        // Add servlets
        servlets.register();
        // Add admin port/path servlets. Needs to be called after healthChecks.register()
        adminServlets.register();
        // Add session listeners.
        sessionListeners.register();
        // Add all injectable rest resources.
        restResources.register();
        // Listen to the lifecycle of the Dropwizard app.
        managedServices.register();

        warnAboutDefaultOpenIdCreds(configuration, appInjector);

        showNodeInfo(configuration);
    }

    private void showNodeInfo(final Config configuration) {
        LOGGER.info("""
                        ********************************************************************************
                          Stroom home:   {}
                          Stroom temp:   {}
                          Node name:     {}
                        ********************************************************************************""",
                homeDirProvider.get().toAbsolutePath().normalize(),
                tempDirProvider.get().toAbsolutePath().normalize(),
                getNodeName(configuration.getYamlAppConfig()));
    }

    private void warnAboutDefaultOpenIdCreds(final Config configuration, final Injector injector) {

        final boolean areDefaultOpenIdCredsInUse = NullSafe.test(configuration.getYamlAppConfig(),
                AppConfig::getSecurityConfig,
                SecurityConfig::getAuthenticationConfig,
                AuthenticationConfig::getOpenIdConfig,
                openIdConfig ->
                        IdpType.TEST_CREDENTIALS.equals(openIdConfig.getIdentityProviderType()));

        if (areDefaultOpenIdCredsInUse) {
            final DefaultOpenIdCredentials defaultOpenIdCredentials = injector.getInstance(
                    DefaultOpenIdCredentials.class);
            final String propPath = configuration.getYamlAppConfig()
                    .getSecurityConfig()
                    .getAuthenticationConfig()
                    .getOpenIdConfig()
                    .getFullPathStr(AbstractOpenIdConfig.PROP_NAME_IDP_TYPE);

            LOGGER.warn("\n" +
                        "\n  -----------------------------------------------------------------------------" +
                        "\n  " +
                        "\n                                        WARNING!" +
                        "\n  " +
                        "\n   Using default and publicly available Open ID authentication credentials. " +
                        "\n   This is insecure! These should only be used in test/demo environments. " +
                        "\n   Set " + propPath + " to INTERNAL_IDP/EXTERNAL_IDP for production environments." +
                        "\n" +
                        "\n   " + defaultOpenIdCredentials.getApiKey() +
                        "\n  -----------------------------------------------------------------------------" +
                        "\n");
        }
    }

    private String getNodeName(final AppConfig appConfig) {
        return NullSafe.get(appConfig, AppConfig::getNodeConfig, NodeConfig::getNodeName);
    }

    private void validateAppConfig(final Config config, final Path configFile) {

        final AppConfig appConfig = config.getYamlAppConfig();

        // Walk the config tree to decorate it with all the path names
        // so we can qualify each prop
        PropertyPathDecorator.decoratePaths(appConfig, AppConfig.ROOT_PROPERTY_PATH);

        final AppConfigValidator appConfigValidator = validationOnlyInjector.getInstance(AppConfigValidator.class);

        LOGGER.info("Validating application configuration file {}",
                configFile.toAbsolutePath().normalize());

        final ConfigValidator.Result<AbstractConfig> result = appConfigValidator.validateRecursively(appConfig);

        result.handleViolations(AppConfigValidator::logConstraintViolation);

        LOGGER.info("Completed validation of application configuration, errors: {}, warnings: {}",
                result.getErrorCount(),
                result.getWarningCount());

        if (result.hasErrors() && appConfig.isHaltBootOnConfigValidationFailure()) {
            LOGGER.error("Application configuration is invalid. Stopping Stroom. To run Stroom with invalid " +
                         "configuration, set {} to false, however this is not advised!",
                    appConfig.getFullPathStr(AppConfig.PROP_NAME_HALT_BOOT_ON_CONFIG_VALIDATION_FAILURE));
            System.exit(1);
        }
    }

    private void configureSessionHandling(final Environment environment,
                                          final SessionConfig sessionConfig) {

        final SessionHandler sessionHandler = new SessionHandler();
        // We need to give our session cookie a name other than JSESSIONID, otherwise it might
        // clash with other services running on the same domain.
        sessionHandler.setSessionCookie(SessionUtil.STROOM_SESSION_COOKIE_NAME);
        // In case we use URL encoding of the session ID, which we currently don't
        sessionHandler.setSessionIdPathParameterName(SessionUtil.STROOM_SESSION_COOKIE_NAME);
        long maxInactiveIntervalSecs = NullSafe.getOrElse(
                sessionConfig.getMaxInactiveInterval(),
                StroomDuration::getDuration,
                Duration::toSeconds,
                -1L);
        if (maxInactiveIntervalSecs > Integer.MAX_VALUE) {
            maxInactiveIntervalSecs = -1;
        }
        LOGGER.info("Setting session maxInactiveInterval to {} secs ({})",
                ModelStringUtil.formatCsv(maxInactiveIntervalSecs),
                (maxInactiveIntervalSecs > 0
                        ? Duration.ofSeconds(maxInactiveIntervalSecs).toString()
                        : String.valueOf(maxInactiveIntervalSecs)));

        // If we don't let sessions expire then the Map of HttpSession in SessionListListener
        // will grow and grow
        sessionHandler.setMaxInactiveInterval((int) maxInactiveIntervalSecs);

        environment.servlets().setSessionHandler(sessionHandler);
        environment.jersey().register(SessionFactoryProvider.class);
    }

    private void configureSessionCookie(final Environment environment,
                                        final SessionCookieConfig sessionCookieConfig) {
        // Ensure the session cookie that provides JSESSIONID is secure.
        final ContextHandler.Context context = environment
                .getApplicationContext()
                .getServletContext();

        final jakarta.servlet.SessionCookieConfig servletSessionCookieConfig = context
                .getSessionCookieConfig();
        servletSessionCookieConfig.setSecure(sessionCookieConfig.isSecure());
        servletSessionCookieConfig.setHttpOnly(sessionCookieConfig.isHttpOnly());
        context.setAttribute(
                HttpCookie.SAME_SITE_DEFAULT_ATTRIBUTE,
                sessionCookieConfig.getSameSite().getAttributeValue());
    }

//    private static void configureCors(final Environment environment,
//                                      final CorsConfig corsConfig) {
//        // Enable CORS headers
//        final FilterRegistration.Dynamic cors =
//                environment.servlets().addFilter("CORS", CrossOriginFilter.class);
//
//        // Configure CORS parameters
//        cors.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM,
//                "*"); // Same as default.
//        cors.setInitParameter(CrossOriginFilter.ALLOWED_HEADERS_PARAM,
//                "X-Requested-With,Content-Type,Accept,Origin"); // Same as default.
//        cors.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM,
//                "GET,POST,HEAD"); // Same as default.
//
//        // Add other overrides from config.
//        if (corsConfig != null && corsConfig.getParameters() != null && !corsConfig.getParameters().isEmpty()) {
//            corsConfig.getParameters().forEach(param -> {
//                cors.setInitParameter(param.getName(), param.getValue());
//            });
//        }
//
//        // Add URL mapping
//        cors.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
//    }

    private void registerLogConfiguration(final Environment environment) {
        // Task to allow configuration of log levels at runtime
        final String path = environment.getAdminContext().getContextPath();

        // To change the log level do one of:
        // curl -X POST -d "logger=stroom&level=DEBUG" [admin context path]/tasks/log-level
        // http -f POST [admin context path]/tasks/log-level logger=stroom level=DEBUG
        // 'http' requires installing HTTPie

        LOGGER.info("Registering Log Configuration Task on {}/tasks/log-level", path);
        environment.admin().addTask(new LogConfigurationTask());
    }

//    private void disableAuthentication(final AppConfig appConfig) {
//        LOGGER.warn("\n" + ConsoleColour.red(
//                "" +
//                        "\n           ***************************************************************" +
//                        "\n           FOR DEVELOPER USE ONLY!  DO NOT RUN IN PRODUCTION ENVIRONMENTS!" +
//                        "\n" +
//                        "\n                          ALL AUTHENTICATION IS DISABLED!" +
//                        "\n           ***************************************************************"));
//
//        final AuthenticationConfig authenticationConfig = appConfig.getSecurityConfig().getAuthenticationConfig();
//
//        // Auth needs HTTPS and GWT super dev mode cannot work in HTTPS
//        String msg = new ColouredStringBuilder()
//                .appendRed("In GWT Super Dev Mode, overriding ")
//                .appendCyan(AuthenticationConfig.PROP_NAME_AUTHENTICATION_REQUIRED)
//                .appendRed(" to [")
//                .appendCyan(Boolean.toString(SUPER_DEV_AUTHENTICATION_REQUIRED_VALUE))
//                .appendRed("] in appConfig")
//                .toString();
//
//        LOGGER.warn(msg);
//        authenticationConfig.setAuthenticationRequired(SUPER_DEV_AUTHENTICATION_REQUIRED_VALUE);
//    }

    /**
     * Creates a separate guice injector just for doing javax validation on the config
     */
    private Injector createValidationInjector(final Path configFile) {

        try {
            // We need to read the AppConfig in the same way that dropwiz will so we can get the
            // possibly substituted values for stroom.(home|temp) for use with PathCreator
            LOGGER.info("Parsing config file to establish home and temp");
            final AppConfig appConfig = StroomYamlUtil.readAppConfig(configFile);
            LOGGER.debug(() -> "pathConfig " + appConfig.getPathConfig());

            // Allow for someone having pathConfig set to null in the yaml, e.g. just
            //   pathConfig:
            final PathConfig effectivePathConfig = Objects.requireNonNullElse(
                    appConfig.getPathConfig(),
                    new StroomPathConfig());

            return Guice.createInjector(
                    new ValidationModule(),
                    new DirProvidersModule(),
                    new AbstractModule() {
                        @Override
                        protected void configure() {
                            bind(PathConfig.class).toInstance(effectivePathConfig);
                        }
                    });

        } catch (final IOException e) {
            throw new RuntimeException("Error parsing config file " + configFile.toAbsolutePath().normalize());
        }
    }
}
