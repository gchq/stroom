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

package stroom.proxy.app;

import stroom.dropwizard.common.AdminServlets;
import stroom.dropwizard.common.AdminTasks;
import stroom.dropwizard.common.DelegatingExceptionMapper;
import stroom.dropwizard.common.DropWizardMetrics;
import stroom.dropwizard.common.Filters;
import stroom.dropwizard.common.HealthChecks;
import stroom.dropwizard.common.ManagedServices;
import stroom.dropwizard.common.RestResources;
import stroom.dropwizard.common.Servlets;
import stroom.proxy.app.guice.ProxyModule;
import stroom.proxy.app.handler.ForwardFileConfig;
import stroom.proxy.app.handler.ForwardHttpPostConfig;
import stroom.proxy.app.handler.ProxyId;
import stroom.security.openid.api.AbstractOpenIdConfig;
import stroom.security.openid.api.IdpType;
import stroom.util.authentication.DefaultOpenIdCredentials;
import stroom.util.config.ConfigValidator;
import stroom.util.config.PropertyPathDecorator;
import stroom.util.date.DateUtil;
import stroom.util.io.DirProvidersModule;
import stroom.util.io.FileUtil;
import stroom.util.io.HomeDirProvider;
import stroom.util.io.PathConfig;
import stroom.util.io.PathCreator;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.DefaultLoggingFilter;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.BuildInfo;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourcePaths;
import stroom.util.validation.ValidationModule;
import stroom.util.yaml.YamlUtil;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.servlets.tasks.LogConfigurationTask;
import jakarta.inject.Inject;
import jakarta.validation.ValidatorFactory;
import org.eclipse.jetty.server.session.SessionHandler;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Collectors;

public class App extends Application<Config> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(App.class);

    @Inject
    private DropWizardMetrics dropWizardMetrics;
    @Inject
    private AdminTasks adminTasks;
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
    private PathCreator pathCreator;
    @Inject
    private TempDirProvider tempDirProvider;
    @Inject
    private ProxyId proxyId;

    private Injector injector;

    private final Path configFile;

    // This is an additional injector for use only with jakarta.validation. It means we can do validation
    // of the yaml file before our main injector has been created and also so we can use our custom
    // validation annotations with REST services (see initialize() method). It feels a bit wrong having two
    // injectors running but not sure how else we could do this unless Guice is not used for the validators.
    // TODO 28/02/2022 AT: We could add the validation module to the root injector along with BuildInfo
    //  then we don't have to bind it twice
    private final Injector validationOnlyInjector;

    @SuppressWarnings("unused") // Used by DropwizardExtensionsSupport in AbstractApplicationTest
    public App() {
        configFile = null; // No config file. Test will create the Config obj
        validationOnlyInjector = createValidationInjector();
    }


    App(final Path configFile) {
        Objects.requireNonNull(configFile, () ->
                LogUtil.message("No config YAML file supplied in arguments"));
        this.configFile = configFile;
        validationOnlyInjector = createValidationInjector();
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
    public void initialize(final Bootstrap<Config> bootstrap) {
        // Dropwizard 2.x no longer fails on unknown properties by default but we want it to.
        bootstrap.getObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // This allows us to use env var templating and relative (to proxy home) paths in the YAML configuration.
        bootstrap.setConfigurationSourceProvider(ProxyYamlUtil.createConfigurationSourceProvider(
                bootstrap.getConfigurationSourceProvider(), true));

        // If we want to use jakarta.validation on our rest resources with our own custom validation annotations
        // then we need to set the ValidatorFactory. As our main Guice Injector is not available yet we need to
        // create one just for the REST validation
        bootstrap.setValidatorFactory(validationOnlyInjector.getInstance(ValidatorFactory.class));

        // Admin servlet for Prometheus to scrape (pull) metrics
//        bootstrap.addBundle(new PrometheusBundle());
    }

    @Override
    public void run(final Config configuration, final Environment environment) {
        LOGGER.info("Using application configuration file {}",
                NullSafe.get(configFile, Path::toAbsolutePath, Path::normalize));

        validateAppConfig(configuration, configFile);

        // Add useful logging setup.
        registerLogConfiguration(environment);

        environment.jersey().register(DefaultLoggingFilter.createWithDefaults());

        // We want Stroom to use the root path so we need to move Dropwizard's path.
        environment.jersey().setUrlPattern(ResourcePaths.API_ROOT_PATH + "/*");

        // Set up a session handler for Jetty
        environment.servlets().setSessionHandler(new SessionHandler());

        LOGGER.info("Starting Stroom Proxy");

        final ProxyModule proxyModule = new ProxyModule(configuration, environment, configFile);
        injector = Guice.createInjector(proxyModule);
        injector.injectMembers(this);

        // Ensure we have our home/temp dirs set up
        FileUtil.ensureDirExists(homeDirProvider.get());
        FileUtil.ensureDirExists(tempDirProvider.get());

        // Add DropWizard metrics
        dropWizardMetrics.register();

        // Add admin tasks
        adminTasks.register();

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

        showInfo(configuration);
    }

    private void warnAboutDefaultOpenIdCreds(final Config configuration, final Injector injector) {

        final boolean areDefaultOpenIdCredsInUse = NullSafe.test(configuration.getProxyConfig(),
                ProxyConfig::getProxySecurityConfig,
                ProxySecurityConfig::getAuthenticationConfig,
                ProxyAuthenticationConfig::getOpenIdConfig,
                openIdConfig ->
                        IdpType.TEST_CREDENTIALS.equals(openIdConfig.getIdentityProviderType()));

        if (areDefaultOpenIdCredsInUse) {
            final DefaultOpenIdCredentials defaultOpenIdCredentials = injector.getInstance(
                    DefaultOpenIdCredentials.class);
            final String propPath = configuration.getProxyConfig()
                    .getProxySecurityConfig()
                    .getAuthenticationConfig()
                    .getOpenIdConfig()
                    .getFullPathStr(AbstractOpenIdConfig.PROP_NAME_IDP_TYPE);
            LOGGER.warn("" +
                        "\n  ---------------------------------------------------------------------------------------" +
                        "\n  " +
                        "\n                                        WARNING!" +
                        "\n  " +
                        "\n   Using default and publicly available Open ID authentication credentials. " +
                        "\n   These should only be used in test/demo environments. " +
                        "\n   Set " + propPath + " to EXTERNAL/NO_IDP for production environments." +
                        "The API key in use is:" +
                        "\n" +
                        "\n   " + defaultOpenIdCredentials.getApiKey() +
                        "\n  ---------------------------------------------------------------------------------------" +
                        "");
        }
    }

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

    private void showInfo(final Config configuration) {
        Objects.requireNonNull(buildInfo);
        final ProxyConfig proxyConfig = configuration.getProxyConfig();

        final String forwaders = proxyConfig.streamAllForwarders()
                .map(forwarderConfig -> {
                    final String name = forwarderConfig.getName();
                    final String destination = forwarderConfig.getDestinationDescription(
                            proxyConfig.getDownstreamHostConfig(),
                            pathCreator);
                    final String state = forwarderConfig.isEnabled()
                            ? ""
                            : " DISABLED";
                    final String instant = forwarderConfig.isInstant()
                            ? " (INSTANT)"
                            : "";
                    final String type = switch (forwarderConfig) {
                        case final ForwardHttpPostConfig ignored -> "HTTP";
                        case final ForwardFileConfig ignored -> "FILE";
                    };
                    return "    " + type + ": '" + name + "' -> " + destination + instant + state;
                })
                .sorted()
                .collect(Collectors.joining("\n"));
        final IdpType idpType = NullSafe.get(
                proxyConfig,
                ProxyConfig::getProxySecurityConfig,
                ProxySecurityConfig::getAuthenticationConfig,
                ProxyAuthenticationConfig::getOpenIdConfig,
                ProxyOpenIdConfig::getIdentityProviderType);

        LOGGER.info(""
                    + "\n  Build version:       " + buildInfo.getBuildVersion()
                    + "\n  Build date:          " + DateUtil.createNormalDateTimeString(buildInfo.getBuildTime())
                    + "\n  Stroom Proxy home:   " + homeDirProvider.get().toAbsolutePath().normalize()
                    + "\n  Stroom Proxy temp:   " + tempDirProvider.get().toAbsolutePath().normalize()
                    + "\n  Proxy ID:            " + proxyId.getId()
                    + "\n  IDP Type:            " + idpType
                    + "\n  Forwarders:          " + "\n" + forwaders
                    + "\n");
    }

    private Injector createValidationInjector() {
        try {
            // We need to read the AppConfig in the same way that dropwiz will so we can get the
            // possibly substituted values for stroom.(home|temp) for use with PathCreator
            final PathConfig effectivePathConfig;
            if (configFile != null) {
                final ProxyConfig proxyConfig;
                LOGGER.info("Parsing config file to establish home and temp");
                proxyConfig = ProxyYamlUtil.readProxyConfig(configFile);
                LOGGER.debug(() -> "proxyPathConfig " + proxyConfig.getPathConfig());
                // Allow for someone having pathConfig set to null in the yaml, e.g. just
                //   pathConfig:
                effectivePathConfig = Objects.requireNonNullElse(
                        proxyConfig.getPathConfig(),
                        new ProxyPathConfig());
            } else {
                // Go in here when using AbstractApplicationTest as that does not use a physical config file
                // The test can set the system props to override the home/temp locations
                effectivePathConfig = new ProxyPathConfig();
            }

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

    private void validateAppConfig(final Config config, final Path configFile) {

        final ProxyConfig proxyConfig = config.getProxyConfig();

        // Walk the config tree to decorate it with all the path names
        // so we can qualify each prop
        PropertyPathDecorator.decoratePaths(proxyConfig, ProxyConfig.ROOT_PROPERTY_PATH);

        final ProxyConfigValidator appConfigValidator = validationOnlyInjector.getInstance(ProxyConfigValidator.class);

        LOGGER.info("Validating application configuration file {}",
                NullSafe.get(configFile, Path::toAbsolutePath, Path::normalize));

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

    public Injector getInjector() {
        return injector;
    }
}
