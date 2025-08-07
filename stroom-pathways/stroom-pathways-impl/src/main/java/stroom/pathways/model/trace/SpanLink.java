package stroom.pathways.model.trace;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class SpanLink {

    @JsonProperty("traceId")
    private final String traceId;

    @JsonProperty("spanId")
    private final String spanId;

    @JsonProperty("traceState")
    private final String traceState;

    @JsonProperty("attributes")
    private final List<KeyValue> attributes;

    @JsonProperty("droppedAttributesCount")
    private final int droppedAttributesCount;

    @JsonCreator
    public SpanLink(@JsonProperty("traceId") final String traceId,
                    @JsonProperty("spanId") final String spanId,
                    @JsonProperty("traceState") final String traceState,
                    @JsonProperty("attributes") final List<KeyValue> attributes,
                    @JsonProperty("droppedAttributesCount") final int droppedAttributesCount) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.traceState = traceState;
        this.attributes = attributes;
        this.droppedAttributesCount = droppedAttributesCount;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public String getTraceState() {
        return traceState;
    }

    public List<KeyValue> getAttributes() {
        return attributes;
    }

    public int getDroppedAttributesCount() {
        return droppedAttributesCount;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SpanLink spanLink = (SpanLink) o;
        return droppedAttributesCount == spanLink.droppedAttributesCount &&
               Objects.equals(traceId, spanLink.traceId) &&
               Objects.equals(spanId, spanLink.spanId) &&
               Objects.equals(traceState, spanLink.traceState) &&
               Objects.equals(attributes, spanLink.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(traceId, spanId, traceState, attributes, droppedAttributesCount);
    }

    @Override
    public String toString() {
        return "SpanLink{" +
               "traceId='" + traceId + '\'' +
               ", spanId='" + spanId + '\'' +
               ", traceState='" + traceState + '\'' +
               ", attributes=" + attributes +
               ", droppedAttributesCount=" + droppedAttributesCount +
               '}';
    }
}
