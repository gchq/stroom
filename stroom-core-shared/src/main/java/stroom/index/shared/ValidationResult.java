package stroom.index.shared;

import stroom.util.shared.Severity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class ValidationResult {

    private static final ValidationResult OK = new ValidationResult(null, null);

    @JsonProperty
    private final Severity severity;
    @JsonProperty
    private final String message;

    @JsonCreator
    public ValidationResult(@JsonProperty("severity") final Severity severity,
                            @JsonProperty("message") final String message) {
        this.severity = severity;
        this.message = message;
    }

    public static ValidationResult ok() {
        return OK;
    }

    public static ValidationResult error(final String message) {
        return new ValidationResult(Severity.ERROR, message);
    }

    public static ValidationResult warning(final String message) {
        return new ValidationResult(Severity.WARNING, message);
    }

    public static ValidationResult fatal(final String message) {
        return new ValidationResult(Severity.FATAL_ERROR, message);
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    @JsonIgnore
    public boolean isOk() {
        return severity == null;
    }

    @JsonIgnore
    public boolean isWarning() {
        return Severity.WARNING.equals(severity);
    }

    @JsonIgnore
    public boolean isError() {
        return Severity.ERROR.equals(severity);
    }

    public boolean hasMessage() {
        return message != null && !message.isEmpty();
    }
}
