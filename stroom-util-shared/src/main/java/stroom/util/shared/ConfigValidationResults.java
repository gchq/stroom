package stroom.util.shared;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

public class ConfigValidationResults {

    private static final ConfigValidationResults HEALTHY_INSTANCE = new ConfigValidationResults(Collections.emptyList());

    private final List<ConfigValidationMessage> configValidationMessages;

    private ConfigValidationResults(final List<ConfigValidationMessage> configValidationMessages) {
        this.configValidationMessages = configValidationMessages;
    }

    public static ConfigValidationResults healthy() {
        return HEALTHY_INSTANCE;
    }

    public List<ConfigValidationMessage> getConfigValidationMessages() {
        return configValidationMessages;
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

    public List<ConfigValidationMessage> getErrors() {
        return getBySeverity(Severity.ERROR);
    }

    public List<ConfigValidationMessage> getWarnings() {
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
        if (configValidationMessages.isEmpty()) {
            return false;
        }
        return configValidationMessages.stream()
            .anyMatch(configValidationMessage ->
                configValidationMessage.getSeverity().equals(severity));
    }

    private List<ConfigValidationMessage> getBySeverity(final Severity severity) {
        if (configValidationMessages.isEmpty()) {
            return Collections.emptyList();
        }
        return configValidationMessages.stream()
            .filter(configValidationMessage ->
                configValidationMessage.getSeverity().equals(severity))
            .collect(Collectors.toList());
    }

    private long getCountBySeverity(final Severity severity) {
        if (configValidationMessages.isEmpty()) {
            return 0;
        }
        return configValidationMessages.stream()
            .filter(configValidationMessage ->
                configValidationMessage.getSeverity().equals(severity))
            .count();
    }

    public static class Builder {

        private final List<ConfigValidationMessage> configValidationMessages = new ArrayList<>();
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
            if (configValidationMessages.isEmpty()) {
                return ConfigValidationResults.healthy();
            } else {
                return new ConfigValidationResults(configValidationMessages);
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
            configValidationMessages.add(
                new ConfigValidationMessage(severity, config, propertyName, message));
        }
    }


    public static class Aggregator {

        private final List<ConfigValidationMessage> configValidationMessages = new ArrayList<>();

        public Aggregator addAll(final ConfigValidationResults configValidationResults) {
            configValidationMessages.addAll(configValidationResults.getConfigValidationMessages());
            return this;
        }

        public ConfigValidationResults aggregate() {
            if (configValidationMessages.isEmpty()) {
                return ConfigValidationResults.healthy();
            } else {
                return new ConfigValidationResults(configValidationMessages);
            }
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

