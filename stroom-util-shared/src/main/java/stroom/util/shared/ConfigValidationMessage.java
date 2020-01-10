package stroom.util.shared;

public class ConfigValidationMessage {
    private final ConfigValidationResults.Severity severity;
    private final IsConfig config;
    private final String propertyName;
    private final String message;

    ConfigValidationMessage(final ConfigValidationResults.Severity severity,
                            final IsConfig config,
                            final String propertyName,
                            final String message) {
        this.severity = severity;
        this.config = config;
        this.propertyName = propertyName;
        this.message = message;
    }

    public static ConfigValidationMessage error(final IsConfig config,
                                                final String propertyName,
                                                final String message) {
        return new ConfigValidationMessage(
            ConfigValidationResults.Severity.ERROR,
            config,
            propertyName,
            message);
    }

    public static ConfigValidationMessage warn(final IsConfig config,
                                               final String propertyName,
                                               final String message) {
        return new ConfigValidationMessage(
            ConfigValidationResults.Severity.WARN,
            config,
            propertyName,
            message);
    }

    public ConfigValidationResults.Severity getSeverity() {
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
