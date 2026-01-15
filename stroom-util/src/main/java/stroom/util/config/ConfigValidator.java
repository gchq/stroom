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

package stroom.util.config;

import stroom.util.config.PropertyUtil.Prop;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.HasPropertyPath;
import stroom.util.shared.NullSafe;
import stroom.util.shared.validation.ValidationSeverity;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ConfigValidator<T> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ConfigValidator.class);


    private final Validator validator;
    private final Class<T> configSuperType;

    public ConfigValidator(final Validator validator,
                           final Class<T> configSuperType) {
        this.validator = validator;
        this.configSuperType = configSuperType;
    }

    /**
     * Validates a single config property value
     */
    public Result<T> validateValue(final Class<? extends T> configClass,
                                   final String propertyName,
                                   final Object value,
                                   final Class<T> configSuperType) {

        final Set<? extends ConstraintViolation<? extends T>> constraintViolations =
                validator.validateValue(configClass, propertyName, value);

        return Result.of(constraintViolations, configSuperType);
    }

    /**
     * Default validation that logs errors/warnings to the logger as well as returning the results.
     * Will only validate child objects marked with @Valid.
     */
    public Result<T> validate(final T config, final Class<T> configSuperType) {
        LOGGER.debug(() -> LogUtil.message("Validating class {}", config.getClass()));
        final Set<ConstraintViolation<T>> constraintViolations = validator.validate(config);
        return Result.of(constraintViolations, configSuperType);
    }

    /**
     * Walks the config object tree validating each branch regardless of whether they have @Valid
     * annotations.
     */
    public Result<T> validateRecursively(final T config, final Class<T> configSuperType) {
        final List<Result<T>> resultList = new ArrayList<>();

        // Validate the top level AppConfig object
        final Result<T> rootResult = validate(config, configSuperType);
        if (rootResult.hasErrorsOrWarnings()) {
            resultList.add(rootResult);
        }

        // Now validate each of the branches recursively
        // We could do this by setting @Valid on each config getter but that is liable to
        // be forgotten on new config objects
        PropertyUtil.walkObjectTree(
                config,
                prop ->
                        canValidateProp(configSuperType, prop),
                prop ->
                        validateProp(prop, resultList));

        return Result.of(resultList, configSuperType);
    }

    private void validateProp(final Prop prop, final List<Result<T>> resultList) {

        if (Collection.class.isAssignableFrom(prop.getValueClass())) {
            // e.g. prop: forwardHttpDestinations
            ((Collection<?>) prop.getValueFromConfigObject()).forEach(item -> {
                if (item != null && configSuperType.isAssignableFrom(item.getClass())) {
                    // Validate this list item which is a Stroom/Proxy config class
                    doValidate(configSuperType.cast(item), resultList);

                    // Now recurse into its child props to see if we need to validate any of them.
                    PropertyUtil.walkObjectTree(
                            item,
                            childProp -> {
                                final boolean canValidate = canValidateProp(configSuperType, childProp);
                                if (!canValidate) {
                                    LOGGER.debug("Not validating childProp: {}, prop: {}", childProp, prop);
                                }
                                return canValidate;
                            },
                            childProp ->
                                    validateProp(childProp, resultList));
                }
            });
        } else {
            doValidate(prop, resultList);
        }
    }

    private void doValidate(final Prop prop, final List<Result<T>> resultList) {
        LOGGER.debug("Validating prop: {}", prop);
        final T configObject = (T) prop.getValueFromConfigObject();
        final Result<T> result = validate(configObject, configSuperType);
        if (result.hasErrorsOrWarnings()) {
            resultList.add(result);
        }
    }

    private void doValidate(final T configObject, final List<Result<T>> resultList) {
        LOGGER.debug("Validating configObject: {}", configObject);
        final Result<T> result = validate(configObject, configSuperType);
        if (result.hasErrorsOrWarnings()) {
            resultList.add(result);
        }
    }

    private boolean canValidateProp(final Class<T> configSuperType, final Prop prop) {
        // Only want to validate config objects
        final Class<?> valueClass = prop.getValueClass();

        final boolean isIncludedInValidation;

        if (prop.getValueFromConfigObject() == null) {
            isIncludedInValidation = false;
        } else if (configSuperType.isAssignableFrom(valueClass)) {
            // One of our config classes
            isIncludedInValidation = true;
        } else if (Collection.class.isAssignableFrom(valueClass)) {
            // e.g. List<?>, as in forwardStreamConfig.forwardDestinations
            final List<Type> genericTypes = PropertyUtil.getGenericTypes(prop.getValueType());
            // TODO what to do about maps? Hopefully we won't have maps containing config objects
            if (genericTypes.size() == 1) {
                final Class<?> genericTypeClass = PropertyUtil.getDataType(genericTypes.get(0));
                if (configSuperType.isAssignableFrom(genericTypeClass)) {
                    // e.g. List<XxxxConfig>, Set<XxxxxConfig>, etc.
                    isIncludedInValidation = true;
                } else {
                    // e.g. List<String>
                    isIncludedInValidation = false;
                }
            } else {
                isIncludedInValidation = false;
            }
        } else {
            isIncludedInValidation = false;
        }

        if (LOGGER.isDebugEnabled() && valueClass.getName().startsWith("stroom")) {
            LOGGER.debug(() -> LogUtil.message(
                    "Testing class {}, isIncluded {}",
                    valueClass.getSimpleName(),
                    isIncludedInValidation));
        }
        return isIncludedInValidation;
    }

    public static <T> void logConstraintViolation(
            final ConstraintViolation<? extends T> constraintViolation,
            final ValidationSeverity severity,
            final Class<T> configSuperType) {

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
        for (final jakarta.validation.Path.Node node : constraintViolation.getPropertyPath()) {
            propName = node.getName();
        }
        final T config = (T) constraintViolation.getLeafBean();

        final String path;
        if (config instanceof HasPropertyPath) {
            final HasPropertyPath hasPropertyPath = ((HasPropertyPath) config);
            final String propPath = hasPropertyPath.getFullPathStr(propName);
            path = propPath != null
                    ? propPath
                    : config.getClass().getName();
        } else {
            // No path so make do with the class name
            path = config.getClass().getName();
        }
        // Value might be a collection so strip the square brackets as
        final String valueStr = NullSafe.getOrElse(
                constraintViolation.getInvalidValue(),
                Object::toString,
                str -> str.startsWith("[") && str.endsWith("]")
                        ? str
                        : "[" + str + "]",
                "[null]");

        logFunc.accept(LogUtil.message("  Validation {} for property {} with value {} - {}",
                severityStr,
                path,
                valueStr,
                constraintViolation.getMessage()));
    }


    // --------------------------------------------------------------------------------


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
