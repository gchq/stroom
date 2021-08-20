package stroom.util.config;

import stroom.util.config.ConfigValidator.Result;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.validation.ValidationSeverity;

import javax.inject.Inject;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;

public class AppConfigValidator {

    private final ConfigValidator<AbstractConfig> configValidator;

    @Inject
    public AppConfigValidator(final Validator validator) {
        this.configValidator = new ConfigValidator<>(validator, AbstractConfig.class);
    }

    public Result<AbstractConfig> validateValue(final Class<? extends AbstractConfig> configClass,
                                           final String propertyName,
                                           final Object value) {
        return configValidator.validateValue(configClass, propertyName, value, AbstractConfig.class);
    }

    public Result<AbstractConfig> validate(final AbstractConfig config) {
        return configValidator.validate(config, AbstractConfig.class);
    }

    public Result<AbstractConfig> validateRecursively(final AbstractConfig config) {
        return configValidator.validateRecursively(config, AbstractConfig.class);
    }

    public static void logConstraintViolation(final ConstraintViolation<? extends AbstractConfig> constraintViolation,
                                              final ValidationSeverity severity) {
        ConfigValidator.logConstraintViolation(constraintViolation, severity, AbstractConfig.class);
    }
}
