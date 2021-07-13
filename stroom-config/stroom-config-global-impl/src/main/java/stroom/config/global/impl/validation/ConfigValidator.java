package stroom.config.global.impl.validation;

import stroom.util.config.PropertyUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.validation.ValidationSeverity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;

public class ConfigValidator<T> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ConfigValidator.class);


    private final Validator validator;
    private final Class<T> configSuperType;

    @Inject
    public ConfigValidator(final Validator validator,
                           final Class<T> configSuperType) {
        this.validator = validator;
        this.configSuperType = configSuperType;
    }

    /**
     * Validates a single config property value
     */
    public Result validateValue(final Class<? extends T> configClass,
                                final String propertyName,
                                final Object value) {

        final Set<? extends ConstraintViolation<? extends T>> constraintViolations =
                validator.validateValue(configClass, propertyName, value);

        return Result.of(constraintViolations);
    }

    /**
     * Default validation that logs errors/warnings to the logger as well as returning the results.
     * Will only validate child objects marked with @Valid.
     */
    public Result validate(final T config) {
        final Set<ConstraintViolation<T>> constraintViolations = validator.validate(config);
        return Result.of(constraintViolations);
    }

    /**
     * Walks the config object tree validating each branch regardless of whether they have @Valid
     * annotations.
     */
    public Result validateRecursively(final T config) {
        final List<Result> resultList = new ArrayList<>();

        // Validate the top level AppConfig object
        final ConfigValidator.Result rootResult = validate(config);
        if (rootResult.hasErrorsOrWarnings()) {
            resultList.add(rootResult);
        }

        // Now validate each of the branches recursively
        // We could do this by setting @Valid on each config getter but that is liable to
        // be forgotten on new config objects
        PropertyUtil.walkObjectTree(
                config,
                prop ->
                        // Only want to validate config objects
                        configSuperType.isAssignableFrom(prop.getValueClass())
                                && prop.getValueFromConfigObject() != null,
                prop -> {
                    final T configObject = (T) prop.getValueFromConfigObject();
                    final ConfigValidator.Result result = validate(configObject);
                    if (result.hasErrorsOrWarnings()) {
                        resultList.add(result);
                    }
                });

        return ConfigValidator.Result.of(resultList);
    }

    public static void logConstraintViolation(
            final ConstraintViolation<? extends AbstractConfig> constraintViolation,
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
        final AbstractConfig config = (AbstractConfig) constraintViolation.getLeafBean();

        final String path = config.getFullPath(propName);

        logFunc.accept(LogUtil.message("  Validation {} for {} [{}] - {}",
                severityStr,
                path,
                constraintViolation.getInvalidValue(),
                constraintViolation.getMessage()));
    }

    public static class Result<T> {

        private final int errorCount;
        private final int warningCount;
        private final Class<T> configSuperType;
        private final Set<? extends ConstraintViolation<? extends T>> constraintViolations;

        private Result(final Set<? extends ConstraintViolation<? extends T>> constraintViolations,
                       final Class<T> configSuperType) {
            this.configSuperType = configSuperType;
            if (constraintViolations == null || constraintViolations.isEmpty()) {
                this.errorCount = 0;
                this.warningCount = 0;
                this.constraintViolations = Collections.emptySet();
            } else {
                int errorCount = 0;
                int warningCount = 0;

                for (final ConstraintViolation<? extends T> constraintViolation : constraintViolations) {
                    LOGGER.debug(() -> LogUtil.message("constraintViolation - prop: {}, value: [{}], object: {}",
                            constraintViolation.getPropertyPath().toString(),
                            constraintViolation.getInvalidValue(),
                            constraintViolation.getLeafBean() != null
                                    ? constraintViolation.getLeafBean().getClass().getCanonicalName()
                                    : "NULL"));

                    final ValidationSeverity severity = ValidationSeverity.fromPayloads(
                            constraintViolation.getConstraintDescriptor().getPayload());

                    if (severity.equals(ValidationSeverity.WARNING)) {
                        warningCount++;
                    } else if (severity.equals(ValidationSeverity.ERROR)) {
                        errorCount++;
                    }
                }
                this.errorCount = errorCount;
                this.warningCount = warningCount;
                this.constraintViolations = constraintViolations;
            }
        }

        public static <T> Result<T> of(
                final Set<? extends ConstraintViolation<? extends T>> constraintViolations,
                final Class<T> configSuperType) {

            if (constraintViolations == null || constraintViolations.isEmpty()) {
                return new Result<>(Collections.emptySet(), configSuperType);
            } else {
                return new Result(constraintViolations, configSuperType);
            }
        }

        public static <T> Result<T> of(
                final Collection<Result<T>> results,
                final Class<T> configSuperType) {
            final Set<ConstraintViolation<? extends T>> constraintViolations = new HashSet<>();

            results.forEach(result -> {
                if (result.hasErrorsOrWarnings()) {
                    constraintViolations.addAll(result.constraintViolations);
                }
            });

            return Result.of(constraintViolations, configSuperType);
        }

        public static <T> Result<T> merge(final Result<T> result1, final Result<T> result2) {
            final Set<ConstraintViolation<? extends T>> constraintViolations = new HashSet<>();
            constraintViolations.addAll(result1.constraintViolations);
            constraintViolations.addAll(result2.constraintViolations);
            return Result.of(constraintViolations, result1.configSuperType);
        }


        public static Result empty() {
            return EMPTY;
        }

        /**
         * The passed constraintViolationConsumer is called for each violation in the result set. If there are no
         * errors or warnings the consumer will not be called.
         */
        public void handleViolations(
                final BiConsumer<ConstraintViolation<? extends T>, ValidationSeverity> consumer) {

            for (final ConstraintViolation<? extends T> constraintViolation : constraintViolations) {
                final ValidationSeverity severity = ValidationSeverity.fromPayloads(
                        constraintViolation.getConstraintDescriptor().getPayload());

                consumer.accept(constraintViolation, severity);
            }
        }

        /**
         * The passed errorConsumer is called for each ERROR violation in the result set. If there are no
         * errors the consumer will not be called.
         */
        public void handleErrors(
                final Consumer<ConstraintViolation<? extends T>> errorConsumer) {

            handleViolations(buildFilteringConsumer(ValidationSeverity.ERROR, errorConsumer));
        }

        /**
         * The passed errorConsumer is called for each WARNING violation in the result set. If there are no
         * errors the consumer will not be called.
         */
        public void handleWarnings(
                final Consumer<ConstraintViolation<? extends T>> warningConsumer) {

            handleViolations(buildFilteringConsumer(ValidationSeverity.WARNING, warningConsumer));
        }

        private BiConsumer<ConstraintViolation<? extends T>, ValidationSeverity> buildFilteringConsumer(
                final ValidationSeverity requiredSeverity,
                final Consumer<ConstraintViolation<? extends T>> errorConsumer) {
            Objects.requireNonNull(requiredSeverity);

            return (constraintViolation, severity) -> {
                if (requiredSeverity.equals(severity)) {
                    errorConsumer.accept(constraintViolation);
                }
            };
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

        @Override
        public String toString() {
            return "Result{" +
                    "errorCount=" + errorCount +
                    ", warningCount=" + warningCount +
                    '}';
        }
    }
}
