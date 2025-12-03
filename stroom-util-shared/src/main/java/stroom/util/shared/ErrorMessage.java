package stroom.util.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({"severity", "message", "node"})
@JsonInclude(Include.NON_NULL)
public final class ErrorMessage {
    @JsonProperty
    private final Severity severity;

    @JsonProperty
    private final String message;

    @JsonProperty
    private final String node;

    @JsonCreator
    public ErrorMessage(@JsonProperty("severity") final Severity severity,
                        @JsonProperty("message") final String message,
                        @JsonProperty("node") final String node) {
        this.severity = severity == null ? Severity.ERROR : severity;
        this.message = message;
        this.node = node;
    }

    public ErrorMessage(final Severity severity, final String message) {
        this(severity, message, null);
    }


    public Severity getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public String getNode() {
        return node;
    }

    @Override
    public String toString() {
        return "ErrorMessage{" +
               "severity=" + severity +
               ", message='" + message + '\'' +
               ", node='" + node + "'" +
               '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ErrorMessage that = (ErrorMessage) o;
        return severity == that.severity &&
               Objects.equals(message, that.message) &&
               Objects.equals(node, that.node);
    }

    @Override
    public int hashCode() {
        return Objects.hash(severity, message, node);
    }
}
