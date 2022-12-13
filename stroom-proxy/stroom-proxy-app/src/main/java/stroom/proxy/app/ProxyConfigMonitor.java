package stroom.proxy.app;

import stroom.proxy.app.guice.ProxyConfigProvider;
import stroom.util.HasHealthCheck;
import stroom.util.config.AbstractFileChangeMonitor;
import stroom.util.config.ConfigLocation;
import stroom.util.config.ConfigValidator;
import stroom.util.config.PropertyPathDecorator;
import stroom.util.shared.IsProxyConfig;

import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ProxyConfigMonitor extends AbstractFileChangeMonitor implements Managed, HasHealthCheck {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyConfigMonitor.class);

    private final Path configFile;
    private final ProxyConfigValidator proxyConfigValidator;
    private final ProxyConfigProvider proxyConfigProvider;

    @Inject
    public ProxyConfigMonitor(final ConfigLocation configLocation,
                              final ProxyConfigValidator proxyConfigValidator,
                              final ProxyConfigProvider proxyConfigProvider) {
        super(configLocation.getConfigFilePath());
        this.configFile = configLocation.getConfigFilePath();
        this.proxyConfigValidator = proxyConfigValidator;
        this.proxyConfigProvider = proxyConfigProvider;
    }

    @Override
    protected void onFileChange() {
        if (configFile != null) {
            updateProxyConfigFromFile();
        } else {
            LOGGER.warn("configFile is null, unable to update config from file");
        }
    }

    private void updateProxyConfigFromFile() {
        ProxyConfig newProxyConfig;
        LOGGER.info("Reading updated config file");

        try {
            newProxyConfig = ProxyYamlUtil.readProxyConfig(configFile);
        } catch (Exception e) {
            // Swallow error as we don't want to break the app because the file is bad.
            // Admin can fix file and try again.
            if (LOGGER.isDebugEnabled()) {
                LOGGER.error("{}\nThe config has not been updated. Fix the errors and save the file. " +
                        "Use DEBUG for stacktrace.", e.getMessage(), e);
            } else {
                LOGGER.error("{}\nThe config has not been updated. Fix the errors and save the file.",
                        e.getMessage());
            }
            newProxyConfig = null;
        }

        if (newProxyConfig != null) {
            final ConfigValidator.Result<IsProxyConfig> result = validateNewConfig(newProxyConfig);

            if (result.hasErrors()) {
                LOGGER.error("Unable to update application config from file {} because it failed validation. " +
                                "Fix the errors and save the file.",
                        configFile.toAbsolutePath().normalize());
            } else {
                try {
                    LOGGER.info("Updating application config from file.");
                    proxyConfigProvider.rebuildConfigInstances(newProxyConfig);
                } catch (Throwable e) {
                    // Swallow error as we don't want to break the app because the new config is bad
                    // The admins can fix the problem and let it have another go.
                    LOGGER.error("Error updating runtime configuration from file {}",
                            configFile.toAbsolutePath().normalize(), e);
                }
            }
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
