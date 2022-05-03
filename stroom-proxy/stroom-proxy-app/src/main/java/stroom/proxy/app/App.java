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

package stroom.proxy.app;

import stroom.dropwizard.common.AdminServlets;
import stroom.dropwizard.common.DelegatingExceptionMapper;
import stroom.dropwizard.common.Filters;
import stroom.dropwizard.common.HealthChecks;
import stroom.dropwizard.common.ManagedServices;
import stroom.dropwizard.common.RestResources;
import stroom.dropwizard.common.Servlets;
import stroom.proxy.app.guice.ProxyModule;
import stroom.util.authentication.DefaultOpenIdCredentials;
import stroom.util.config.ConfigValidator;
import stroom.util.config.PropertyPathDecorator;
import stroom.util.date.DateUtil;
import stroom.util.io.DirProvidersModule;
import stroom.util.io.FileUtil;
import stroom.util.io.HomeDirProvider;
import stroom.util.io.PathConfig;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.BuildInfo;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.ResourcePaths;
import stroom.util.validation.ValidationModule;
import stroom.util.yaml.YamlUtil;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.dropwizard.Application;
import io.dropwizard.servlets.tasks.LogConfigurationTask;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlets.CrossOriginFilter;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.Objects;
import javax.inject.Inject;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.validation.ValidatorFactory;

public class App extends Application<Config> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(App.class);

    @Inject
    private HealthChecks healthChecks;
    @Inject
    private Filters filters;
    @Inject
    private Servlets servlets;
    @Inject
    private AdminServlets adminServlets;
    @Inject
    private RestResources restResources;
    @Inject
    private ManagedServices managedServices;
    @Inject
    private DelegatingExceptionMapper delegatingExceptionMapper;
    @Inject
    private BuildInfo buildInfo;
    @Inject
    private HomeDirProvider homeDirProvider;
    @Inject
    private TempDirProvider tempDirProvider;

    private final Path configFile;

    // This is an additional injector for use only with javax.validation. It means we can do validation
    // of the yaml file before our main injector has been created and also so we can use our custom
    // validation annotations with REST services (see initialize() method). It feels a bit wrong having two
    // injectors running but not sure how else we could do this unless Guice is not used for the validators.
    // TODO 28/02/2022 AT: We could add the validation module to the root injector along with BuildInfo
    //  then we don't have to bind it twice
    private final Injector validationOnlyInjector;

    // Needed for DropwizardExtensionsSupport
    public App() {
        configFile = Paths.get("proxy-dev.yml");
        validationOnlyInjector = createValidationInjector();
    }


    App(final Path configFile) {
        this.configFile = configFile;
        validationOnlyInjector = createValidationInjector();
    }

    public static void main(final String[] args) throws Exception {
        final Path yamlConfigFile = YamlUtil.getYamlFileFromArgs(args);
        new App(yamlConfigFile).run(args);
    }

    @Override
    public void initialize(final Bootstrap<Config> bootstrap) {
        // Dropwizard 2.x no longer fails on unknown properties by default but we want it to.
        bootstrap.getObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // This allows us to use env var templating and relative (to proxy home) paths in the YAML configuration.
        bootstrap.setConfigurationSourceProvider(ProxyYamlUtil.createConfigurationSourceProvider(
                bootstrap.getConfigurationSourceProvider(), true));

        // If we want to use javax.validation on our rest resources with our own custom validation annotations
        // then we need to set the ValidatorFactory. As our main Guice Injector is not available yet we need to
        // create one just for the REST validation
        bootstrap.setValidatorFactory(validationOnlyInjector.getInstance(ValidatorFactory.class));
    }

    @Override
    public void run(final Config configuration, final Environment environment) {
        Objects.requireNonNull(configFile, () ->
                LogUtil.message("No config YAML file supplied in arguments"));

        LOGGER.info("Using application configuration file {}",
                configFile.toAbsolutePath().normalize());

        validateAppConfig(configuration, configFile);

        // Add useful logging setup.
        registerLogConfiguration(environment);

        // We want Stroom to use the root path so we need to move Dropwizard's path.
        environment.jersey().setUrlPattern(ResourcePaths.API_ROOT_PATH + "/*");

        // Set up a session handler for Jetty
        environment.servlets().setSessionHandler(new SessionHandler());

        // Configure Cross-Origin Resource Sharing.
        configureCors(environment);

        LOGGER.info("Starting Stroom Proxy");

        final ProxyModule proxyModule = new ProxyModule(configuration, environment, configFile);
        final Injector injector = Guice.createInjector(proxyModule);
        injector.injectMembers(this);

        // Ensure we have our home/temp dirs set up
        FileUtil.ensureDirExists(homeDirProvider.get());
        FileUtil.ensureDirExists(tempDirProvider.get());

        // Add health checks
        healthChecks.register();

        // Add filters
        filters.register();

        // Add servlets
        servlets.register();

        // Add admin port/path servlets. Needs to be called after healthChecks.register()
        adminServlets.register();

        // Add all injectable rest resources.
        restResources.register();

        // Add jersey exception mappers.
        environment.jersey().register(delegatingExceptionMapper);

        // Listen to the lifecycle of the Dropwizard app.
        managedServices.register();

        warnAboutDefaultOpenIdCreds(configuration, injector);

        showInfo();
    }

    private void warnAboutDefaultOpenIdCreds(Config configuration, Injector injector) {
        if (configuration.getProxyConfig().isUseDefaultOpenIdCredentials()) {
            DefaultOpenIdCredentials defaultOpenIdCredentials = injector.getInstance(DefaultOpenIdCredentials.class);
            LOGGER.warn("" +
                    "\n  ---------------------------------------------------------------------------------------" +
                    "\n  " +
                    "\n                                        WARNING!" +
                    "\n  " +
                    "\n   Using default and publicly available Open ID authentication credentials. " +
                    "\n   These should only be used in test/demo environments. " +
                    "\n   Set useDefaultOpenIdCredentials to false for production environments. " +
                    "The API key in use is:" +
                    "\n" +
                    "\n   " + defaultOpenIdCredentials.getApiKey() +
                    "\n  ---------------------------------------------------------------------------------------" +
                    "");
        }
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

    private void showInfo() {
        Objects.requireNonNull(buildInfo);
        LOGGER.info(""
                + "\n  Build version:       " + buildInfo.getBuildVersion()
                + "\n  Build date:          " + DateUtil.createNormalDateTimeString(buildInfo.getBuildTime())
                + "\n  Stroom Proxy home:   " + homeDirProvider.get().toAbsolutePath().normalize()
                + "\n  Stroom Proxy temp:   " + tempDirProvider.get().toAbsolutePath().normalize());
    }

    private Injector createValidationInjector() {
        try {
            // We need to read the AppConfig in the same way that dropwiz will so we can get the
            // possibly substituted values for stroom.(home|temp) for use with PathCreator
            LOGGER.info("Parsing config file to establish home and temp");
            final ProxyConfig proxyConfig = ProxyYamlUtil.readProxyConfig(configFile);

            LOGGER.debug(() -> "proxyPathConfig " + proxyConfig.getPathConfig());

            // Allow for someone having pathConfig set to null in the yaml, e.g. just
            //   pathConfig:
            final PathConfig effectivePathConfig = Objects.requireNonNullElse(
                    proxyConfig.getPathConfig(),
                    new ProxyPathConfig());

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

    private void validateAppConfig(final Config config, final Path configFile) {

        final ProxyConfig proxyConfig = config.getProxyConfig();

        // Walk the config tree to decorate it with all the path names
        // so we can qualify each prop
        PropertyPathDecorator.decoratePaths(proxyConfig, ProxyConfig.ROOT_PROPERTY_PATH);

        final ProxyConfigValidator appConfigValidator = validationOnlyInjector.getInstance(ProxyConfigValidator.class);

        LOGGER.info("Validating application configuration file {}",
                configFile.toAbsolutePath().normalize().toString());

        final ConfigValidator.Result<IsProxyConfig> result = appConfigValidator.validateRecursively(proxyConfig);

        result.handleViolations(ProxyConfigValidator::logConstraintViolation);

        LOGGER.info("Completed validation of application configuration, errors: {}, warnings: {}",
                result.getErrorCount(),
                result.getWarningCount());

        if (result.hasErrors() && proxyConfig.isHaltBootOnConfigValidationFailure()) {
            LOGGER.error("Application configuration is invalid. Stopping Stroom Proxy. To run Stroom Proxy with " +
                            "invalid configuration, set {} to false, however this is not advised!",
                    proxyConfig.getFullPathStr(ProxyConfig.PROP_NAME_HALT_BOOT_ON_CONFIG_VALIDATION_FAILURE));
            System.exit(1);
        }
    }
}
