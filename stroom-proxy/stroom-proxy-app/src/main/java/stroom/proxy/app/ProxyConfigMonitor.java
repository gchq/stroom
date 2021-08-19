package stroom.proxy.app;

import stroom.util.HasHealthCheck;
import stroom.util.config.AbstractFileChangeMonitor;
import stroom.util.config.ConfigLocation;
import stroom.util.config.ConfigValidator;
import stroom.util.config.FieldMapper;
import stroom.util.config.PropertyPathDecorator;
import stroom.util.shared.IsProxyConfig;

import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;

public class ProxyConfigMonitor extends AbstractFileChangeMonitor implements Managed, HasHealthCheck {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyConfigMonitor.class);

    private final ProxyConfig proxyConfig;
    private final ConfigLocation configLocation;
    private final Path configFile;
    private final ProxyConfigValidator proxyConfigValidator;

    @Inject
    public ProxyConfigMonitor(final ProxyConfig proxyConfig,
                              final ConfigLocation configLocation,
                              final ProxyConfigValidator proxyConfigValidator) {
        super(configLocation.getConfigFilePath());
        this.proxyConfig = proxyConfig;
        this.configFile = configLocation.getConfigFilePath();
        this.configLocation = configLocation;
        this.proxyConfigValidator = proxyConfigValidator;
    }

    @Override
    protected void onFileChange() {
        updateProxyConfigFromFile();
    }

    private void updateProxyConfigFromFile() {
        final ProxyConfig newProxyConfig;
        try {
            LOGGER.info("Reading updated config file");
            newProxyConfig = ProxyYamlUtil.readProxyConfig(configFile);

            final ConfigValidator.Result<IsProxyConfig> result = validateNewConfig(newProxyConfig);

            if (result.hasErrors()) {
                LOGGER.error("Unable to update application config from file {} because it failed validation. " +
                                "Fix the errors and save the file.",
                        configFile.toAbsolutePath().normalize().toString());
            } else {
                try {
                    final AtomicInteger updateCount = new AtomicInteger(0);
                    final FieldMapper.UpdateAction updateAction =
                            (destParent, prop, sourcePropValue, destPropValue) -> {
                                logUpdate(destParent, prop, sourcePropValue, destPropValue);
                                updateCount.incrementAndGet();
                            };

                    LOGGER.info("Updating application config from file.");
                    // Copy changed values from the newly modified appConfig into the guice bound one
                    FieldMapper.copy(newProxyConfig, this.proxyConfig, updateAction);
                    LOGGER.info("Property update count: {}", updateCount.get());
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


    private ConfigValidator.Result<IsProxyConfig> validateNewConfig(final ProxyConfig newProxyConfig) {
        // Decorate the new config tree so it has all the paths,
        // i.e. call setBasePath on each branch in the newAppConfig tree so if we get any violations we
        // can log their locations with full paths.
        PropertyPathDecorator.decoratePaths(newProxyConfig, ProxyConfig.ROOT_PROPERTY_PATH);

        LOGGER.info("Validating modified config file (home: {})", newProxyConfig.getPathConfig().getHome());
        final ConfigValidator.Result<IsProxyConfig> result = proxyConfigValidator.validateRecursively(newProxyConfig);
        result.handleViolations(ProxyConfigValidator::logConstraintViolation);

        LOGGER.info("Completed validation of application configuration, errors: {}, warnings: {}",
                result.getErrorCount(),
                result.getWarningCount());
        return result;
    }
}
