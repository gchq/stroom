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

import stroom.app.commands.CreateAccountCommand;
import stroom.app.commands.CreateApiKeyCommand;
import stroom.app.commands.DbMigrationCommand;
import stroom.app.commands.ManageUsersCommand;
import stroom.app.commands.ResetPasswordCommand;
import stroom.app.guice.AppModule;
import stroom.config.app.AppConfig;
import stroom.config.app.Config;
import stroom.config.app.StroomYamlUtil;
import stroom.config.global.impl.ConfigMapper;
import stroom.dropwizard.common.AdminServlets;
import stroom.dropwizard.common.Filters;
import stroom.dropwizard.common.HealthChecks;
import stroom.dropwizard.common.ManagedServices;
import stroom.dropwizard.common.RestResources;
import stroom.dropwizard.common.Servlets;
import stroom.dropwizard.common.SessionListeners;
import stroom.dropwizard.common.WebSockets;
import stroom.event.logging.rs.api.RestResourceAutoLogger;
import stroom.util.config.AppConfigValidator;
import stroom.util.config.ConfigValidator;
import stroom.util.config.PropertyPathDecorator;
import stroom.util.io.DirProvidersModule;
import stroom.util.io.FileUtil;
import stroom.util.io.HomeDirProvider;
import stroom.util.io.PathConfig;
import stroom.util.io.StroomPathConfig;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.ResourcePaths;
import stroom.util.validation.ValidationModule;
import stroom.util.yaml.YamlUtil;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.dropwizard.Application;
import io.dropwizard.jersey.sessions.SessionFactoryProvider;
import io.dropwizard.servlets.tasks.LogConfigurationTask;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.glassfish.jersey.logging.LoggingFeature;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.servlet.SessionCookieConfig;
import javax.sql.DataSource;
import javax.validation.ValidatorFactory;

public class App extends Application<Config> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(App.class);

    private static final String APP_NAME = "Stroom";
    public static final String SESSION_COOKIE_NAME = "STROOM_SESSION_ID";
//    private static final boolean SUPER_DEV_AUTHENTICATION_REQUIRED_VALUE = false;

    @Inject
    private HealthChecks healthChecks;
    @Inject
    private Filters filters;
    @Inject
    private Servlets servlets;
    @Inject
    private AdminServlets adminServlets;
    @Inject
    private WebSockets webSockets;
    @Inject
    private SessionListeners sessionListeners;
    @Inject
    private RestResources restResources;
    @Inject
    private ManagedServices managedServices;
    @Inject
    private RestResourceAutoLogger resourceAutoLogger;
    @Inject
    private Provider<Set<DataSource>> dataSourcesProvider;

    // Injected manually
    private HomeDirProvider homeDirProvider;
    private TempDirProvider tempDirProvider;

    private final Path configFile;

    // This is an additional injector for use only with javax.validation. It means we can do validation
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
        bootstrap.getObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // This allows us to use env var templating and relative (to stroom home) paths in the YAML configuration.
        bootstrap.setConfigurationSourceProvider(StroomYamlUtil.createConfigurationSourceProvider(
                bootstrap.getConfigurationSourceProvider(), true));

        // Add the GWT UI assets.
        bootstrap.addBundle(new DynamicAssetsBundle(
                "/ui",
                ResourcePaths.ROOT_PATH,
                "index.html",
                "ui"));

        // Add the new React UI assets. Note that the React UI uses sub paths for navigation using the React
        // BrowserRouter. This always needs the root page to be served regardless of the path requested so we
        // need to use a special asset bundle to achieve this.
        bootstrap.addBundle(new BrowserRouterAssetsBundle(
                "/new-ui",
                "/",
                "index.html",
                "new-ui",
                ResourcePaths.SINGLE_PAGE_PREFIX));

        addCliCommands(bootstrap);

        // If we want to use javax.validation on our rest resources with our own custom validation annotations
        // then we need to set the ValidatorFactory. As our main Guice Injector is not available yet we need to
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
        environment.jersey().register(
                new LoggingFeature(
                        java.util.logging.Logger.getLogger(LoggingFeature.DEFAULT_LOGGER_NAME),
                        Level.INFO,
                        LoggingFeature.Verbosity.PAYLOAD_ANY,
                        LoggingFeature.DEFAULT_MAX_ENTITY_SIZE));

        // Add useful logging setup.
        registerLogConfiguration(environment);

        // We want Stroom to use the root path so we need to move Dropwizard's path.
        environment.jersey().setUrlPattern(ResourcePaths.API_ROOT_PATH + "/*");

        // Set up a session handler for Jetty
        configureSessionHandling(environment);

        // Ensure the session cookie that provides JSESSIONID is secure.
        // Need to get it from ConfigMapper not AppConfig as ConfigMapper is now the source of
        // truth for config.
        final ConfigMapper configMapper = bootStrapInjector.getInstance(ConfigMapper.class);
        final stroom.config.app.SessionCookieConfig sessionCookieConfig = configMapper.getConfigObject(
                stroom.config.app.SessionCookieConfig.class);
        configureSessionCookie(environment, sessionCookieConfig);

        // Configure Cross-Origin Resource Sharing.
        configureCors(environment);

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

        // Add web sockets
        webSockets.register();

        // Add session listeners.
        sessionListeners.register();

        // Add all injectable rest resources.
        restResources.register();

        // Listen to the lifecycle of the Dropwizard app.
        managedServices.register();

        warnAboutDefaultOpenIdCreds(configuration);

        showNodeInfo(configuration);
    }

    private void showNodeInfo(final Config configuration) {
        LOGGER.info(""
                + "\n********************************************************************************"
                + "\n  Stroom home:   " + homeDirProvider.get().toAbsolutePath().normalize()
                + "\n  Stroom temp:   " + tempDirProvider.get().toAbsolutePath().normalize()
                + "\n  Node name:     " + getNodeName(configuration.getYamlAppConfig())
                + "\n********************************************************************************");
    }

    private void warnAboutDefaultOpenIdCreds(Config configuration) {
        if (configuration.getYamlAppConfig().getSecurityConfig().getIdentityConfig().isUseDefaultOpenIdCredentials()) {
            String propPath = configuration.getYamlAppConfig().getSecurityConfig().getIdentityConfig().getFullPathStr(
                    "useDefaultOpenIdCredentials");
            LOGGER.warn("\n" +
                    "\n  -----------------------------------------------------------------------------" +
                    "\n  " +
                    "\n                                        WARNING!" +
                    "\n  " +
                    "\n   Using default and publicly available Open ID authentication credentials. " +
                    "\n   This is insecure! These should only be used in test/demo environments. " +
                    "\n   Set " + propPath + " to false for production environments." +
                    "\n" +
                    "\n  -----------------------------------------------------------------------------" +
                    "\n");
        }
    }

    private String getNodeName(final AppConfig appConfig) {
        return appConfig != null
                ? (appConfig.getNodeConfig() != null
                ? appConfig.getNodeConfig().getNodeName()
                : null)
                : null;
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

    private static void configureSessionHandling(final Environment environment) {
        SessionHandler sessionHandler = new SessionHandler();
        // We need to give our session cookie a name other than JSESSIONID, otherwise it might
        // clash with other services running on the same domain.
        sessionHandler.setSessionCookie(SESSION_COOKIE_NAME);
        environment.servlets().setSessionHandler(sessionHandler);
        environment.jersey().register(SessionFactoryProvider.class);
    }

    private static void configureSessionCookie(final Environment environment,
                                               final stroom.config.app.SessionCookieConfig config) {
        // Ensure the session cookie that provides JSESSIONID is secure.
        final SessionCookieConfig sessionCookieConfig = environment
                .getApplicationContext()
                .getServletContext()
                .getSessionCookieConfig();
        sessionCookieConfig.setSecure(config.isSecure());
        sessionCookieConfig.setHttpOnly(config.isHttpOnly());
        // TODO : Add `SameSite=Strict` when supported by JEE
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

        } catch (IOException e) {
            throw new RuntimeException("Error parsing config file " + configFile.toAbsolutePath().normalize());
        }
    }
}
