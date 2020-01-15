package stroom.config.global.impl.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.global.impl.ConfigMapper;
import stroom.util.logging.LogUtil;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.ValidationSeverity;

import javax.inject.Inject;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ConfigValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigValidator.class);

    private final ConfigMapper configMapper;
    private final Validator validator;

    @Inject
    public ConfigValidator(final ConfigMapper configMapper, final Validator validator) {
        this.configMapper = configMapper;
        this.validator = validator;
    }

    /**
     * Default validation that logs errors/warnings to the logger.
     */
    public Result validate(final AbstractConfig config) {
        return validate(config, this::logConstraintViolation);
    }

    public Result validate(final AbstractConfig config,
                           final BiConsumer<ConstraintViolation<AbstractConfig>, ValidationSeverity> constraintViolationConsumer) {

        final Set<ConstraintViolation<AbstractConfig>> constraintViolations = validator.validate(config);

        int errorCount = 0;
        int warningCount = 0;

        for (final ConstraintViolation<AbstractConfig> constraintViolation : constraintViolations) {
            LOGGER.debug("constraintViolation - prop: {}, value: [{}], object: {}",
                constraintViolation.getPropertyPath().toString(),
                constraintViolation.getInvalidValue(),
                constraintViolation.getLeafBean().getClass().getCanonicalName());

            ValidationSeverity severity = ValidationSeverity.fromPayloads(
                constraintViolation.getConstraintDescriptor().getPayload());

            if (severity.equals(ValidationSeverity.WARNING)) {
                warningCount++;
            } else if (severity.equals(ValidationSeverity.ERROR)) {
                errorCount++;
            }

            constraintViolationConsumer.accept(constraintViolation, severity);
        }
        return new Result(errorCount, warningCount);
    }

    private void logConstraintViolation(final ConstraintViolation<AbstractConfig> constraintViolation,
                                        final ValidationSeverity severity) {

        final Consumer<String> logFunc;
        final String severityStr;
        if (severity.equals(ValidationSeverity.WARNING)) {
            logFunc = LOGGER::warn;
            severityStr = "warning";
        } else {
            logFunc = LOGGER::error;
            severityStr = "error";
        }

        String propName = null;
        for (javax.validation.Path.Node node : constraintViolation.getPropertyPath()) {
            propName = node.getName();
        }
        AbstractConfig config = (AbstractConfig) constraintViolation.getLeafBean();

        final String path = config.getFullPath(propName);

        logFunc.accept(LogUtil.message("  Validation {} for {} [{}] - {}",
            severityStr,
            path,
            constraintViolation.getInvalidValue(),
            constraintViolation.getMessage()));
    }

    public static class Result {
        private final int errorCount;
        private final int warningCount;

        public Result(final int errorCount, final int warningCount) {
            this.errorCount = errorCount;
            this.warningCount = warningCount;
        }

        public int getErrorCount() {
            return errorCount;
        }

        public int getWarningCount() {
            return warningCount;
        }

        public boolean hasErrors() {
            return errorCount > 0;
        }

        public boolean hasWarnings() {
            return warningCount > 0;
        }

        public boolean hasErrorsOrWarnings() {
            return errorCount > 0 || warningCount > 0;
        }
    }
}
