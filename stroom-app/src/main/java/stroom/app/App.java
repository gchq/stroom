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
import stroom.app.commands.DbMigrationCommand;
import stroom.app.commands.ManageUsersCommand;
import stroom.app.commands.ResetPasswordCommand;
import stroom.app.guice.AppModule;
import stroom.config.app.AppConfig;
import stroom.config.app.Config;
import stroom.config.global.impl.ConfigMapper;
import stroom.config.global.impl.validation.ConfigValidator;
import stroom.config.global.impl.validation.ValidationModule;
import stroom.dropwizard.common.DelegatingExceptionMapper;
import stroom.dropwizard.common.Filters;
import stroom.dropwizard.common.HealthChecks;
import stroom.dropwizard.common.ManagedServices;
import stroom.dropwizard.common.RestResources;
import stroom.dropwizard.common.Servlets;
import stroom.dropwizard.common.SessionListeners;
import stroom.security.impl.AuthenticationConfig;
import stroom.security.impl.ContentSecurityConfig;
import stroom.util.ColouredStringBuilder;
import stroom.util.ConsoleColour;
import stroom.util.logging.LogUtil;
import stroom.util.shared.BuildInfo;
import stroom.util.shared.ResourcePaths;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.jersey.sessions.SessionFactoryProvider;
import io.dropwizard.servlets.tasks.LogConfigurationTask;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.glassfish.jersey.logging.LoggingFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.servlet.SessionCookieConfig;
import javax.validation.ValidatorFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.logging.Level;

public class App extends Application<Config> {
    private static final Logger LOGGER = LoggerFactory.getLogger(App.class);

    private static final String APP_NAME = "Stroom";
    private static final String GWT_SUPER_DEV_SYSTEM_PROP_NAME = "gwtSuperDevMode";
    public static final String SESSION_COOKIE_NAME = "STROOM_SESSION_ID";
    private static final boolean SUPER_DEV_AUTHENTICATION_REQUIRED_VALUE = false;
    private static final String SUPER_DEV_CONTENT_SECURITY_POLICY_VALUE = "";

    @Inject
    private HealthChecks healthChecks;
    @Inject
    private Filters filters;
    @Inject
    private Servlets servlets;
    @Inject
    private SessionListeners sessionListeners;
    @Inject
    private DelegatingExceptionMapper delegatingExceptionMapper;
    @Inject
    private RestResources restResources;
    @Inject
    private ManagedServices managedServices;
    @Inject
    private BuildInfo buildInfo;

    private final Path configFile;

    // This is an additional injector for use only with javax.validation. It means we can do validation
    // of the yaml file before our main injector has been created and also so we can use our custom
    // validation annotations with REST services (see initialize() method). It feels a bit wrong having two
    // injectors running but not sure how else we could do this unless Guice is not used for the validators.
    private final Injector validationOnlyInjector;

    // Needed for DropwizardExtensionsSupport
    public App() {
        configFile = Paths.get("PATH_NOT_SUPPLIED");
        validationOnlyInjector = createValidationInjector();
    }


    App(final Path configFile) {
        super();
        this.configFile = configFile;
        validationOnlyInjector = createValidationInjector();
    }

    public static void main(final String[] args) throws Exception {
        final Path yamlConfigFile = getYamlFileFromArgs(args);
        new App(yamlConfigFile).run(args);
    }

    @Override
    public String getName() {
        return APP_NAME;
    }

    @Override
    public void initialize(final Bootstrap<Config> bootstrap) {
        // This allows us to use templating in the YAML configuration.
        bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
                bootstrap.getConfigurationSourceProvider(),
                new EnvironmentVariableSubstitutor(false)));

        // Add the GWT UI assets.
        bootstrap.addBundle(new AssetsBundle(
                "/ui",
                ResourcePaths.ROOT_PATH,
                "index.html",
                "ui"));

        // Add the new React UI assets. Note that the React UI uses sub paths for navigation using the React BrowserRouter.
        // This always needs the root page to be served regardless of the path requested so we need to use a special asset bundle to achieve this.
        bootstrap.addBundle(new BrowserRouterAssetsBundle(
                "/new-ui",
                "/",
                "index.html",
                "new-ui",
                ResourcePaths.SINGLE_PAGE_PREFIX));

        addCommands(bootstrap);

        // If we want to use javax.validation on our rest resources with our own custom validation annotations
        // then we need to set the ValidatorFactory. As our main Guice Injector is not available yet we need to
        // create one just for the REST validation
        bootstrap.setValidatorFactory(validationOnlyInjector.getInstance(ValidatorFactory.class));
    }

    private void addCommands(final Bootstrap<Config> bootstrap) {
        // Add a DW Command so we can run the full migration without running the
        // http server
        bootstrap.addCommand(new DbMigrationCommand(configFile));
        bootstrap.addCommand(new CreateAccountCommand(configFile));
        bootstrap.addCommand(new ResetPasswordCommand(configFile));
        bootstrap.addCommand(new ManageUsersCommand(configFile));
    }

    @Override
    public void run(final Config configuration, final Environment environment) {
        Objects.requireNonNull(configFile, () ->
                LogUtil.message("No config YAML file supplied in arguments"));

        LOGGER.info("Using application configuration file {}", configFile.toAbsolutePath().normalize());

        validateAppConfig(configuration, configFile);

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

        // Check if we are running GWT Super Dev Mode
        checkForSuperDev(configuration.getAppConfig());

        // Add useful logging setup.
        registerLogConfiguration(environment);

        // We want Stroom to use the root path so we need to move Dropwizard's path.
        environment.jersey().setUrlPattern(ResourcePaths.API_ROOT_PATH + "/*");

        // Set up a session handler for Jetty
        configureSessionHandling(environment);

        // Ensure the session cookie that provides JSESSIONID is secure.
        configureSessionCookie(environment, configuration.getAppConfig().getSessionCookieConfig());

        // Configure Cross-Origin Resource Sharing.
        configureCors(environment);


        LOGGER.info("Starting Stroom Application ({})", getNodeName(configuration.getAppConfig()));

        final AppModule appModule = new AppModule(configuration, environment, configFile);
        final Injector injector = Guice.createInjector(appModule);


        injector.injectMembers(this);

        // Add health checks
        healthChecks.register();

        // Add filters
        filters.register();

        // Add servlets
        servlets.register();

        // Add session listeners.
        sessionListeners.register();

        // Add all injectable rest resources.
        restResources.register();

        // Add jersey exception mappers.
        environment.jersey().register(delegatingExceptionMapper);

        // Listen to the lifecycle of the Dropwizard app.
        managedServices.register();

        warnAboutDefaultOpenIdCreds(configuration);

        showBuildInfo();

    }

    private void showBuildInfo() {
        Objects.requireNonNull(buildInfo);
        LOGGER.info("Build version: {}, date: {}",
                buildInfo.getBuildVersion(), buildInfo.getBuildDate());
    }

    private void warnAboutDefaultOpenIdCreds(Config configuration) {
        if (configuration.getAppConfig().getSecurityConfig().getIdentityConfig().isUseDefaultOpenIdCredentials()) {
            String propPath = configuration.getAppConfig().getSecurityConfig().getIdentityConfig().getFullPath("useDefaultOpenIdCredentials");
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

        // Inject this way rather than using injectMembers so we can avoid instantiating
        // too many classes before running our validation

        // create a minimalist throw away injector to just perform the validation
//        Injector injector = Guice.createInjector(new BootConfigValidationModule(config, configFile));

//        injector.getInstance(ConfigMapper.class);
        final AppConfig appConfig = config.getAppConfig();

        ConfigMapper.decorateWithPropertyPaths(appConfig);

        final ConfigValidator configValidator = validationOnlyInjector.getInstance(ConfigValidator.class);

        LOGGER.info("Validating application configuration file {}",
                configFile.toAbsolutePath().normalize().toString());

        final ConfigValidator.Result result = configValidator.validateRecursively(appConfig);

        result.handleViolations(ConfigValidator::logConstraintViolation);

        LOGGER.info("Completed validation of application configuration, errors: {}, warnings: {}",
                result.getErrorCount(),
                result.getWarningCount());

        if (result.hasErrors() && appConfig.isHaltBootOnConfigValidationFailure()) {
            LOGGER.error("Application configuration is invalid. Stopping Stroom. To run Stroom with invalid " +
                            "configuration, set {} to false. This is not advised!",
                    appConfig.getFullPath(AppConfig.PROP_NAME_HALT_BOOT_ON_CONFIG_VALIDATION_FAILURE));
            System.exit(1);
        }
    }

    private static Path getYamlFileFromArgs(final String[] args) {
        // This is not ideal as we are duplicating what dropwizard is doing but there appears to be
        // no way of getting the yaml file location from the dropwizard classes

        for (String arg : args) {
            if (arg.toLowerCase().endsWith("yml") || arg.toLowerCase().endsWith("yaml")) {
                Path yamlFile = Path.of(arg);
                if (Files.isRegularFile(yamlFile)) {
                    return yamlFile;
                } else {
                    // NOTE if you are getting here while running in IJ then you have probable not run
                    // local.yaml.sh
                    LOGGER.warn("YAML config file [{}] from arguments [{}] is not a valid file.\n" +
                                    "You need to supply a valid stroom configuration YAML file.",
                            yamlFile, Arrays.asList(args));
                }
            }
        }
        LOGGER.warn("Could not extract YAML config file from arguments [{}]",
                Arrays.asList(args));
        return null;
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

    private void checkForSuperDev(final AppConfig appConfig) {
        // If sys prop gwtSuperDevMode=true then override other config props
        // i.e. use a run configuration with arg '-DgwtSuperDevMode=true'
        if (Boolean.getBoolean(GWT_SUPER_DEV_SYSTEM_PROP_NAME)) {
            LOGGER.warn("" + ConsoleColour.red(
                    "" +
                            "\n                                      _                                  _      " +
                            "\n                                     | |                                | |     " +
                            "\n      ___ _   _ _ __   ___ _ __    __| | _____   __  _ __ ___   ___   __| | ___ " +
                            "\n     / __| | | | '_ \\ / _ \\ '__|  / _` |/ _ \\ \\ / / | '_ ` _ \\ / _ \\ / _` |/ _ \\" +
                            "\n     \\__ \\ |_| | |_) |  __/ |    | (_| |  __/\\ V /  | | | | | | (_) | (_| |  __/" +
                            "\n     |___/\\__,_| .__/ \\___|_|     \\__,_|\\___| \\_/   |_| |_| |_|\\___/ \\__,_|\\___|" +
                            "\n               | |                                                              " +
                            "\n               |_|                                                              " +
                            "\n"));

//            disableAuthentication(appConfig);

            // Super Dev Mode isn't compatible with HTTPS so ensure cookies are not secure.
            appConfig.getSessionCookieConfig().setSecure(false);

            // The standard content security policy is incompatible with GWT super dev mode
            disableContentSecurity(appConfig);
        }
    }

    private void disableAuthentication(final AppConfig appConfig) {
        LOGGER.warn("\n" + ConsoleColour.red(
                "" +
                        "\n           ***************************************************************" +
                        "\n           FOR DEVELOPER USE ONLY!  DO NOT RUN IN PRODUCTION ENVIRONMENTS!" +
                        "\n" +
                        "\n                          ALL AUTHENTICATION IS DISABLED!" +
                        "\n           ***************************************************************"));

        final AuthenticationConfig authenticationConfig = appConfig.getSecurityConfig().getAuthenticationConfig();

        // Auth needs HTTPS and GWT super dev mode cannot work in HTTPS
        String msg = new ColouredStringBuilder()
                .appendRed("In GWT Super Dev Mode, overriding ")
                .appendCyan(AuthenticationConfig.PROP_NAME_AUTHENTICATION_REQUIRED)
                .appendRed(" to [")
                .appendCyan(Boolean.toString(SUPER_DEV_AUTHENTICATION_REQUIRED_VALUE))
                .appendRed("] in appConfig")
                .toString();

        LOGGER.warn(msg);
        authenticationConfig.setAuthenticationRequired(SUPER_DEV_AUTHENTICATION_REQUIRED_VALUE);
    }

    private void disableContentSecurity(final AppConfig appConfig) {
        final ContentSecurityConfig contentSecurityConfig = appConfig.getSecurityConfig().getContentSecurityConfig();
        final String msg = new ColouredStringBuilder()
                .appendRed("In GWT Super Dev Mode, overriding ")
                .appendCyan(ContentSecurityConfig.PROP_NAME_CONTENT_SECURITY_POLICY)
                .appendRed(" to [")
                .appendCyan(SUPER_DEV_CONTENT_SECURITY_POLICY_VALUE)
                .appendRed("] in appConfig")
                .toString();

        LOGGER.warn(msg);
        contentSecurityConfig.setContentSecurityPolicy(SUPER_DEV_CONTENT_SECURITY_POLICY_VALUE);
    }

    private Injector createValidationInjector() {
        return Guice.createInjector(new ValidationModule());
    }
}
