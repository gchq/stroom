package stroom.config.global.impl;

import stroom.config.app.AppConfig;
import stroom.config.app.SuperDevUtil;
import stroom.config.app.YamlUtil;
import stroom.util.HasHealthCheck;
import stroom.util.config.AbstractFileChangeMonitor;
import stroom.util.config.AppConfigValidator;
import stroom.util.config.ConfigLocation;
import stroom.util.config.ConfigValidator;
import stroom.util.config.FieldMapper;
import stroom.util.config.PropertyPathDecorator;
import stroom.util.shared.AbstractConfig;

import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AppConfigMonitor extends AbstractFileChangeMonitor implements Managed, HasHealthCheck {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppConfigMonitor.class);

    private final AppConfig appConfig;
    private final Path configFile;
    private final GlobalConfigService globalConfigService;
    private final AppConfigValidator appConfigValidator;

    @Inject
    public AppConfigMonitor(final AppConfig appConfig,
                            final ConfigLocation configLocation,
                            final GlobalConfigService globalConfigService,
                            final AppConfigValidator appConfigValidator) {
        super(configLocation.getConfigFilePath());
        this.appConfig = appConfig;
        this.configFile = configLocation.getConfigFilePath();
        this.globalConfigService = globalConfigService;
        this.appConfigValidator = appConfigValidator;
    }

    @Override
    protected void onFileChange() {
        updateAppConfigFromFile();
    }

    private void updateAppConfigFromFile() {
        final AppConfig newAppConfig;
        try {
            LOGGER.info("Reading updated config file");
            newAppConfig = YamlUtil.readAppConfig(configFile);

            // Check if we are running GWT Super Dev Mode, if so relax security
            SuperDevUtil.relaxSecurityInSuperDevMode(newAppConfig);

            final ConfigValidator.Result<AbstractConfig> result = validateNewConfig(newAppConfig);

            if (result.hasErrors()) {
                LOGGER.error("Unable to update application config from file {} because it failed validation. " +
                                "Fix the errors and save the file.",
                        configFile.toAbsolutePath().normalize().toString());
            } else {
                try {
                    // Don't have to worry about the DB config merging that goes on in DataSourceFactoryImpl
                    // as that doesn't mutate the config objects

                    final AtomicInteger updateCount = new AtomicInteger(0);
                    final FieldMapper.UpdateAction updateAction =
                            (destParent, prop, sourcePropValue, destPropValue) -> {
                                logUpdate(destParent, prop, sourcePropValue, destPropValue);
                                updateCount.incrementAndGet();
                            };

                    LOGGER.info("Updating application config from file.");
                    // Copy changed values from the newly modified appConfig into the guice bound one
                    FieldMapper.copy(newAppConfig, this.appConfig, updateAction);

                    // Update the config objects using the DB as the removal of a yaml value may trigger
                    // a DB value to be effective
                    LOGGER.info("Completed updating application config from file. Changes: {}", updateCount.get());
                    globalConfigService.updateConfigObjects(newAppConfig);

                } catch (Throwable e) {
                    // Swallow error as we don't want to break the app because the new config is bad
                    // The admins can fix the problem and let it have another go.
                    LOGGER.error("Error updating runtime configuration from file {}",
                            configFile.toAbsolutePath().normalize(), e);
                }
            }
        } catch (Throwable e) {
            // Swallow error as we don't want to break the app because the file is bad.
            LOGGER.error("Error parsing configuration from file {}",
                    configFile.toAbsolutePath().normalize(), e);
        }
    }

    private ConfigValidator.Result<AbstractConfig> validateNewConfig(final AppConfig newAppConfig) {
        // Decorate the new config tree so it has all the paths,
        // i.e. call setBasePath on each branch in the newAppConfig tree so if we get any violations we
        // can log their locations with full paths.
        PropertyPathDecorator.decoratePaths(newAppConfig, AppConfig.ROOT_PROPERTY_PATH);

        LOGGER.info("Validating modified config file (home: {})", newAppConfig.getPathConfig().getHome());
        final ConfigValidator.Result<AbstractConfig> result = appConfigValidator.validateRecursively(newAppConfig);
        result.handleViolations(AppConfigValidator::logConstraintViolation);

        LOGGER.info("Completed validation of application configuration, errors: {}, warnings: {}",
                result.getErrorCount(),
                result.getWarningCount());
        return result;
    }

}
