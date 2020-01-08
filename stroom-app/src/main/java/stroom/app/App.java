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
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.app.commands.DbMigrationCommand;
import stroom.app.guice.AppModule;
import stroom.config.app.AppConfig;
import stroom.config.app.Config;
import stroom.config.global.impl.ConfigMapper;
import stroom.dropwizard.common.Filters;
import stroom.dropwizard.common.HealthChecks;
import stroom.dropwizard.common.ManagedServices;
import stroom.dropwizard.common.PermissionExceptionMapper;
import stroom.dropwizard.common.RestResources;
import stroom.dropwizard.common.Servlets;
import stroom.dropwizard.common.SessionListeners;
import stroom.util.guice.ResourcePaths;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ConfigValidationResults;

import javax.inject.Inject;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.servlet.SessionCookieConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.function.Consumer;

public class App extends Application<Config> {
    private static final Logger LOGGER = LoggerFactory.getLogger(App.class);

    @Inject
    private HealthChecks healthChecks;
    @Inject
    private Filters filters;
    @Inject
    private Servlets servlets;
    @Inject
    private SessionListeners sessionListeners;
    @Inject
    private RestResources restResources;
    @Inject
    private ManagedServices managedServices;

    private final Path configFile;

    // Needed for DropwizardExtensionsSupport
    public App() {
        configFile = Paths.get("PATH_NOT_SUPPLIED");
    }

    App(final Path configFile) {
        super();
        this.configFile = configFile;
    }

    public static void main(final String[] args) throws Exception {
        final Path yamlConfigFile = getYamlFileFromArgs(args);
        new App(yamlConfigFile).run(args);
    }

    @Override
    public void initialize(final Bootstrap<Config> bootstrap) {
        // This allows us to use templating in the YAML configuration.
        bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
                bootstrap.getConfigurationSourceProvider(),
                new EnvironmentVariableSubstitutor(false)));
        bootstrap.addBundle(new AssetsBundle("/ui", ResourcePaths.ROOT_PATH, "index.html", "ui"));

        // Add a DW Command so we can run the full migration without running the
        // http server
        bootstrap.addCommand(new DbMigrationCommand(configFile));
    }

    @Override
    public void run(final Config configuration, final Environment environment) {
        LOGGER.info("Using application configuration file {}", configFile.toAbsolutePath().normalize());

        if (configuration.getAppConfig().isSuperDevMode()) {
            configuration.getAppConfig().getSecurityConfig().getAuthenticationConfig().setAuthenticationRequired(false);
            configuration.getAppConfig().getSecurityConfig().getContentSecurityConfig().setContentSecurityPolicy("");
        }

        // Add useful logging setup.
        registerLogConfiguration(environment);

        // We want Stroom to use the root path so we need to move Dropwizard's path.
        environment.jersey().setUrlPattern(ResourcePaths.API_PATH + "/*");

        // Set up a session handler for Jetty
        environment.servlets().setSessionHandler(new SessionHandler());

        // Configure Cross-Origin Resource Sharing.
        configureCors(environment);

        LOGGER.info("Starting Stroom Application");

        final AppModule appModule = new AppModule(configuration, environment, configFile);
        final Injector injector = Guice.createInjector(appModule);
        injector.injectMembers(this);

        // TODO
        //   As this relies on ConfigMapper it needs to happen after all the guice binding is done.
        //   We could do it before if we instantiate our our own ConfigMapper and later bind that instance.
        //   Then we could validate the various DB conn/pool props.
        validateAppConfig(injector, configuration.getAppConfig());

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

        // Map exceptions to helpful HTTP responses
        environment.jersey().register(PermissionExceptionMapper.class);

        // Listen to the lifecycle of the Dropwizard app.
        managedServices.register();

        // Ensure the session cookie that provides JSESSIONID is secure.
        final SessionCookieConfig sessionCookieConfig = environment
                .getApplicationContext()
                .getServletContext()
                .getSessionCookieConfig();
        sessionCookieConfig.setSecure(configuration.getAppConfig().getSessionCookieConfig().isSecure());
        sessionCookieConfig.setHttpOnly(configuration.getAppConfig().getSessionCookieConfig().isHttpOnly());
        // TODO : Add `SameSite=Strict` when supported by JEE
    }

    private void validateAppConfig(final Injector injector, final AppConfig appConfig) {
        final ConfigMapper configMapper = injector.getInstance(ConfigMapper.class);
        LOGGER.info("Validating application configuration file {}",
            configFile.toAbsolutePath().normalize().toString());
        final ConfigValidationResults configValidationResults = configMapper.validateConfiguration();

        for (final ConfigValidationResults.ValidationMessage validationMessage : configValidationResults.getValidationMessages()) {
            ConfigValidationResults.Severity severity = validationMessage.getSeverity();
            final Consumer<String> logFunc = severity.equals(ConfigValidationResults.Severity.ERROR)
                ? LOGGER::error
                : LOGGER::warn;

            final String path = configMapper.getBasePath(validationMessage.getConfigInstance());
            final String fullpath = path + "." + validationMessage.getPropertyName();
            logFunc.accept(LogUtil.message("  Validation {} for {} - {}",
                severity.getLongName(),
                fullpath,
                validationMessage.getMessage()));
        }
        LOGGER.info("Completed validation of application configuration, errors: {}, warnings: {}",
            configValidationResults.getErrorCount(),
            configValidationResults.getWarningCount());
        
        if (!appConfig.isHaltBootOnConfigValidationFailure()) {
            LOGGER.warn("");
        }

        if (configValidationResults.hasErrors() && appConfig.isHaltBootOnConfigValidationFailure()) {
            LOGGER.error("Application configuration is invalid. Stopping Stroom. To run Stroom anyway, set {} to false",
                configMapper.getFullPath(appConfig, AppConfig.PROP_NAME_HALT_BOOT_ON_CONFIG_VALIDATION_FAILURE));
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
                    throw new IllegalArgumentException(LogUtil.message(
                            "YAML config file [{}] from arguments [{}] is not a valid file.\n" +
                            "You need to supply a valid stroom configuration YAML file.",
                            yamlFile, Arrays.asList(args)));
                }
            }
        }
        throw new IllegalArgumentException(LogUtil.message("Could not extract YAML config file from arguments [{}]",
                Arrays.asList(args)));
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
