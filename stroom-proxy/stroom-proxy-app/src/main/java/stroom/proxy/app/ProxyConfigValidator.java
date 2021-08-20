package stroom.proxy.app;

import stroom.util.config.ConfigValidator;
import stroom.util.config.ConfigValidator.Result;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.validation.ValidationSeverity;

import javax.inject.Inject;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;

public class ProxyConfigValidator {

    private final ConfigValidator<IsProxyConfig> configValidator;

    @Inject
    public ProxyConfigValidator(final Validator validator) {
        this.configValidator = new ConfigValidator<>(validator, IsProxyConfig.class);
    }

    public Result<IsProxyConfig> validateValue(final Class<? extends IsProxyConfig> configClass,
                                               final String propertyName,
                                               final Object value) {
        return configValidator.validateValue(configClass, propertyName, value, IsProxyConfig.class);
    }

    public Result<IsProxyConfig> validate(final IsProxyConfig config) {
        return configValidator.validate(config, IsProxyConfig.class);
    }

    public Result<IsProxyConfig> validateRecursively(final IsProxyConfig config) {
        return configValidator.validateRecursively(config, IsProxyConfig.class);
    }

    public static void logConstraintViolation(final ConstraintViolation<? extends IsProxyConfig> constraintViolation,
                                              final ValidationSeverity severity) {
        ConfigValidator.logConstraintViolation(constraintViolation, severity, IsProxyConfig.class);
    }
}
