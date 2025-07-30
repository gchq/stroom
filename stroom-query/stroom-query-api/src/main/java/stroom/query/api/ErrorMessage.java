package stroom.query.api;

import stroom.util.shared.Severity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({"severity", "message"})
@JsonInclude(Include.NON_NULL)
public final class ErrorMessage {
    @JsonProperty
    private final Severity severity;

    @JsonProperty
    private final String message;

    @JsonCreator
    public ErrorMessage(@JsonProperty("severity") final Severity severity,
                        @JsonProperty("message") final String message) {
        this.severity = severity;
        this.message = message;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "ErrorMessage{" +
               "severity=" + severity +
               ", message='" + message + '\'' +
               '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ErrorMessage that = (ErrorMessage) o;
        return severity == that.severity && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(severity, message);
    }
}
