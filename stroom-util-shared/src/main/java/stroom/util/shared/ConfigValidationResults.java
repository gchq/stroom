package stroom.util.shared;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
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
        return hasMessagesWithSeverity(Severity.ERROR);
    }

    public boolean hasWarnings() {
        return hasMessagesWithSeverity(Severity.WARN);
    }

    public long getErrorCount() {
        return getCountBySeverity(Severity.ERROR);
    }

    public long getWarningCount() {
        return getCountBySeverity(Severity.WARN);
    }

    public List<ValidationMessage> getErrors() {
        return getBySeverity(Severity.ERROR);
    }

    public List<ValidationMessage> getWarnings() {
        return getBySeverity(Severity.WARN);
    }

    /**
     * Create a builder for validating the config object and recording the results
     * @param config The config object that is being validate.  All property names
     *               are relative to this object
     */
    public static Builder builder(final IsConfig config) {
        return new Builder(config);
    }

    /**
     * Create an aggregator for aggregating multiple set of validation results
     */
    public static Aggregator aggregator() {
        return new Aggregator();
    }

    private boolean hasMessagesWithSeverity(final Severity severity) {
        if (validationMessages.isEmpty()) {
            return false;
        }
        return validationMessages.stream()
            .anyMatch(validationMessage ->
                validationMessage.getSeverity().equals(severity));
    }

    private List<ValidationMessage> getBySeverity(final Severity severity) {
        if (validationMessages.isEmpty()) {
            return Collections.emptyList();
        }
        return validationMessages.stream()
            .filter(validationMessage ->
                validationMessage.getSeverity().equals(severity))
            .collect(Collectors.toList());
    }

    private long getCountBySeverity(final Severity severity) {
        if (validationMessages.isEmpty()) {
            return 0;
        }
        return validationMessages.stream()
            .filter(validationMessage ->
                validationMessage.getSeverity().equals(severity))
            .count();
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
            addMessageWhen(Severity.ERROR, test, propertyName, message);
            return this;
        }

        /**
         * Adds a warning when test is true, i.e. testing for failure
         */
        public Builder addWarningWhen(final boolean test,
                                      final String propertyName,
                                      final String message) {
            addMessageWhen(Severity.WARN, test, propertyName, message);
            return this;
        }

        public Builder addError(final String propertyName,
                                final String message) {
            addMessage(Severity.ERROR, propertyName, message);
            return this;
        }

        public Builder addWarning(final String propertyName,
                                  final String message) {
            addMessage(Severity.WARN, propertyName, message);
            return this;
        }

        public Builder addErrorWhenEmpty(final String value,
                                         final String propertyName) {
            addMessageWhenEmpty(Severity.ERROR, value, propertyName);
            return this;
        }

        public Builder addWarningWhenEmpty(final String value,
                                           final String propertyName) {
            addMessageWhenEmpty(Severity.WARN, value, propertyName);
            return this;
        }

        public Builder addErrorWhenUnset(final Object value,
                                         final String propertyName) {
            addMessageWhenUnset(Severity.ERROR, value, propertyName);
            return this;
        }

        public Builder addWarningWhenUnset(final Object value,
                                           final String propertyName) {
            addMessageWhenUnset(Severity.WARN, value, propertyName);
            return this;
        }

        public Builder addErrorWhenNoRegexMatch(final String value,
                                                   final String pattern,
                                                   final String propertyName) {
            addMessageWhenNoRegexMatch(Severity.ERROR, value, pattern, propertyName);
            return this;
        }

        public Builder addWarningWhenNoRegexMatch(final String value,
                                                  final String pattern,
                                                  final String propertyName) {
            addMessageWhenNoRegexMatch(Severity.WARN, value, pattern, propertyName);
            return this;
        }

        public Builder addErrorWhenPatternInvalid(final String pattern,
                                                   final String propertyName) {
            addMessageWhenPatternInvalid(Severity.ERROR, pattern, propertyName);
            return this;
        }

        public Builder addWarningWhenPatternInvalid(final String pattern,
                                                    final String propertyName) {
            addMessageWhenPatternInvalid(Severity.WARN, pattern, propertyName);
            return this;
        }

        public Builder addErrorWhenModelStringDurationInvalid(final String duration,
                                                                 final String propertyName) {
            addMessageWhenModelStringDurationInvalid(Severity.ERROR, duration, propertyName);
            return this;
        }

        public Builder addWarningWhenModelStringDurationInvalid(final String duration,
                                                                final String propertyName) {
            addMessageWhenModelStringDurationInvalid(Severity.WARN, duration, propertyName);
            return this;
        }

        public ConfigValidationResults build() {
            if (validationMessages.isEmpty()) {
                return ConfigValidationResults.healthy();
            } else {
                return new ConfigValidationResults(validationMessages);
            }
        }

        /**
         * Adds a message with the supplied severity when test is
         * true, i.e. testing for failure.
         */
        private Builder addMessageWhen(final Severity severity,
                                       final boolean test,
                                       final String propertyName,
                                       final String message) {
            if (test) {
                addMessage(severity, propertyName, message);
            }
            return this;
        }

        private Builder addMessageWhenEmpty(final Severity severity,
                                            final String value,
                                            final String propertyName) {
            if (value == null || value.isEmpty()) {
                addMessage(severity, propertyName, "No value has been supplied");
            }
            return this;
        }

        private Builder addMessageWhenUnset(final Severity severity,
                                            final Object value,
                                            final String propertyName) {
            if (value == null) {
                addMessage(severity, propertyName, "No value has been supplied");
            }
            return this;
        }

        private Builder addMessageWhenNoRegexMatch(final Severity severity,
                                                   final String value,
                                                   final String pattern,
                                                   final String propertyName) {
            if (value == null) {
                addMessage(severity, propertyName, "No value has been supplied");
            } else {
                if (!value.matches(pattern)) {
                    addMessage(severity, propertyName, String.format("Value [%s] does not match regex [%s]",
                        value, pattern));
                }
            }
            return this;
        }

        private Builder addMessageWhenPatternInvalid(final Severity severity,
                                                     final String pattern,
                                                     final String propertyName) {
            try {
                Pattern.compile(pattern);
            } catch (PatternSyntaxException e) {
                addMessage(severity, propertyName, String.format("Regex pattern [%s] is not valid. %s",
                    pattern, e.getMessage()));
            }
            return this;
        }

        private Builder addMessageWhenModelStringDurationInvalid(final Severity severity,
                                                                 final String duration,
                                                                 final String propertyName) {
            try {
                ModelStringUtil.parseDurationString(duration);
            } catch (NumberFormatException e) {
                addMessage(severity, propertyName, String.format("Duration string [%s] is not valid. %s",
                    duration, e.getMessage()));
            }
            return this;
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
            if (validationMessages.isEmpty()) {
                return ConfigValidationResults.healthy();
            } else {
                return new ConfigValidationResults(validationMessages);
            }
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

