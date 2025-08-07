package stroom.pathways.model.trace;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class SpanStatus {

    @JsonProperty("message")
    private final String message;

    @JsonProperty("code")
    private final StatusCode code;

    @JsonCreator
    public SpanStatus(@JsonProperty("message") final String message,
                      @JsonProperty("code") final StatusCode code) {
        this.message = message;
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public StatusCode getCode() {
        return code;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SpanStatus that = (SpanStatus) o;
        return Objects.equals(message, that.message) &&
               code == that.code;
    }

    @Override
    public int hashCode() {
        return Objects.hash(message, code);
    }

    @Override
    public String toString() {
        return "SpanStatus{" +
               "message='" + message + '\'' +
               ", code=" + code +
               '}';
    }
}
