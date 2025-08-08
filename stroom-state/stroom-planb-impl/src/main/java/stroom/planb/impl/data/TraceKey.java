package stroom.planb.impl.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Arrays;
import java.util.Objects;

@JsonPropertyOrder({"traceId", "parentSpanId", "spanId"})
@JsonInclude(Include.NON_NULL)
public class TraceKey {

    /**
     * A valid trace identifier is a 16-byte array with at least one non-zero byte.
     */
    @JsonProperty
    private final byte[] traceId;
    /**
     * A valid span identifier is an 8-byte array with at least one non-zero byte.
     */
    @JsonProperty
    private final byte[] parentSpanId;
    /**
     * A valid span identifier is an 8-byte array with at least one non-zero byte.
     */
    @JsonProperty
    private final byte[] spanId;

    @JsonCreator
    public TraceKey(@JsonProperty("traceId") final byte[] traceId,
                    @JsonProperty("parentSpanId") final byte[] parentSpanId,
                    @JsonProperty("spanId") final byte[] spanId) {
        this.traceId = traceId;
        this.parentSpanId = parentSpanId;
        this.spanId = spanId;
    }

    public byte[] getTraceId() {
        return traceId;
    }

    public byte[] getParentSpanId() {
        return parentSpanId;
    }

    public byte[] getSpanId() {
        return spanId;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TraceKey traceKey = (TraceKey) o;
        return Objects.deepEquals(traceId, traceKey.traceId) &&
               Objects.deepEquals(parentSpanId, traceKey.parentSpanId) &&
               Objects.deepEquals(spanId, traceKey.spanId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(traceId), Arrays.hashCode(parentSpanId), Arrays.hashCode(spanId));
    }

    @Override
    public String toString() {
        return "TraceKey{" +
               "traceId=" + Arrays.toString(traceId) +
               ", parentSpanId=" + Arrays.toString(parentSpanId) +
               ", spanId=" + Arrays.toString(spanId) +
               '}';
    }
}
