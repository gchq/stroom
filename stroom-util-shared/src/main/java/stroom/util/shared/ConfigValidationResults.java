package stroom.util.shared;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ConfigValidationResults {

    private static final ConfigValidationResults HEALTHY_INSTANCE = new ConfigValidationResults(Collections.emptyList());
    private final List<ValidationMessage> validationMessages;

    private ConfigValidationResults(final List<ValidationMessage> validationMessages) {
        this.validationMessages = validationMessages;
    }

    public static ConfigValidationResults healthy() {
        return HEALTHY_INSTANCE;
    }

    public List<ValidationMessage> getValidationMessages() {
        return validationMessages;
    }

    public boolean hasErrors() {
        if (validationMessages.isEmpty()) {
            return false;
        }
        return validationMessages.stream()
            .anyMatch(validationMessage ->
                validationMessage.getSeverity().equals(Severity.ERROR));
    }

    public boolean hasWarnings() {
        if (validationMessages.isEmpty()) {
            return false;
        }
        return validationMessages.stream()
            .anyMatch(validationMessage ->
                validationMessage.getSeverity().equals(Severity.WARN));
    }

    public long getErrorCount() {
        return validationMessages.stream()
            .filter(validationMessage ->
                validationMessage.getSeverity().equals(Severity.ERROR))
            .count();
    }

    public long getWarningCount() {
        return validationMessages.stream()
            .filter(validationMessage ->
                validationMessage.getSeverity().equals(Severity.WARN))
            .count();
    }

    public List<ValidationMessage> getErrors() {
        return validationMessages.stream()
            .filter(validationMessage ->
                validationMessage.getSeverity().equals(Severity.ERROR))
            .collect(Collectors.toList());
    }

    public List<ValidationMessage> getWarnings() {
        return validationMessages.stream()
            .filter(validationMessage ->
                validationMessage.getSeverity().equals(Severity.WARN))
            .collect(Collectors.toList());
    }

    public static Builder builder(final IsConfig config) {
        return new Builder(config);
    }

    public static Aggregator aggregator() {
        return new Aggregator();
    }


    public static class Builder {

        private final List<ValidationMessage> validationMessages = new ArrayList<>();
        private final IsConfig config;

        public Builder(final IsConfig config) {
            this.config = config;
        }

        /**
         * Adds an error when test is true, i.e. testing for failure
         */
        public Builder addErrorWhen(final boolean test,
                                    final String propertyName,
                                    final String message) {
            if (test) {
                addMessage(Severity.ERROR, propertyName, message);
            }
            return this;
        }

        public Builder addError(final String propertyName,
                                final String message) {
            addMessage(Severity.ERROR, propertyName, message);
            return this;
        }

        public Builder addErrorWhenEmpty(final String value,
                                         final String propertyName) {
            if (value == null || value.isEmpty()) {
                addMessage(Severity.ERROR, propertyName, "No value has been supplied");
            }
            return this;
        }

        public Builder addWarningWhenEmpty(final String value,
                                           final String propertyName) {
            if (value == null || value.isEmpty()) {
                addMessage(Severity.WARN, propertyName, "No value has been supplied");
            }
            return this;
        }

        public Builder addErrorWhenUnset(final Object value,
                                         final String propertyName) {
            if (value == null) {
                addMessage(Severity.ERROR, propertyName, "No value has been supplied");
            }
            return this;
        }

        public Builder addWarningWhenUnset(final Object value,
                                           final String propertyName) {
            if (value == null) {
                addMessage(Severity.WARN, propertyName, "No value has been supplied");
            }
            return this;
        }

        /**
         * Adds a warning when test is true, i.e. testing for failure
         */
        public Builder addWarningWhen(final boolean test,
                                      final String propertyName,
                                      final String message) {
            if (test) {
                addMessage(Severity.WARN, propertyName, message);
            }
            return this;
        }

        public Builder addWarning(final String propertyName,
                                  final String message) {
            addMessage(Severity.WARN, propertyName, message);
            return this;
        }

        public ConfigValidationResults build() {
            return new ConfigValidationResults(validationMessages);
        }

        private void addMessage(final Severity severity,
                                final String propertyName,
                                final String message) {
            validationMessages.add(
                new ValidationMessage(severity, config, propertyName, message));
        }
    }


    public static class Aggregator {

        private final List<ValidationMessage> validationMessages = new ArrayList<>();

        public Aggregator addAll(final ConfigValidationResults configValidationResults) {
            validationMessages.addAll(configValidationResults.getValidationMessages());
            return this;
        }

        public ConfigValidationResults aggregate() {
            return new ConfigValidationResults(validationMessages);
        }
    }

    public static class ValidationMessage {
        private final Severity severity;
        private final IsConfig config;
        private final String propertyName;
        private final String message;

        private ValidationMessage(final Severity severity,
                                  final IsConfig config,
                                  final String propertyName,
                                  final String message) {
            this.severity = severity;
            this.config = config;
            this.propertyName = propertyName;
            this.message = message;
        }

        public static ValidationMessage error(final IsConfig config,
                                              final String propertyName,
                                              final String message) {
            return new ValidationMessage(
                Severity.ERROR,
                config,
                propertyName,
                message);
        }

        public static ValidationMessage warn(final IsConfig config,
                                             final String propertyName,
                                             final String message) {
            return new ValidationMessage(
                Severity.WARN,
                config,
                propertyName,
                message);
        }

        public Severity getSeverity() {
            return severity;
        }

        public IsConfig getConfigInstance() {
            return config;
        }

        public String getPropertyName() {
            return propertyName;
        }

        public String getMessage() {
            return message;
        }
    }

    public enum Severity {
        ERROR("error"),
        WARN("warning");

        private final String longName;

        Severity(final String longName) {
            this.longName = longName;
        }

        public String getLongName() {
            return longName;
        }
    }

}

