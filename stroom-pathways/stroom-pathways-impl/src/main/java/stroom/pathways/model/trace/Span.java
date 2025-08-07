package stroom.pathways.model.trace;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class Span {

    @JsonProperty("traceId")
    private final String traceId;

    @JsonProperty("spanId")
    private final String spanId;

    @JsonProperty("traceState")
    private final String traceState;

    @JsonProperty("parentSpanId")
    private final String parentSpanId;

    @JsonProperty("flags")
    private final int flags;

    @JsonProperty("name")
    private final String name;

    @JsonProperty("kind")
    private final SpanKind kind;

    @JsonProperty("startTimeUnixNano")
    private final String startTimeUnixNano;

    @JsonProperty("endTimeUnixNano")
    private final String endTimeUnixNano;

    @JsonProperty("attributes")
    private final List<KeyValue> attributes;

    @JsonProperty("droppedAttributesCount")
    private final int droppedAttributesCount;

    @JsonProperty("events")
    private final List<SpanEvent> events;

    @JsonProperty("droppedEventsCount")
    private final int droppedEventsCount;

    @JsonProperty("links")
    private final List<SpanLink> links;

    @JsonProperty("droppedLinksCount")
    private final int droppedLinksCount;

    @JsonProperty("status")
    private final SpanStatus status;

    @JsonCreator
    public Span(@JsonProperty("traceId") final String traceId,
                @JsonProperty("spanId") final String spanId,
                @JsonProperty("traceState") final String traceState,
                @JsonProperty("parentSpanId") final String parentSpanId,
                @JsonProperty("flags") final int flags,
                @JsonProperty("name") final String name,
                @JsonProperty("kind") final SpanKind kind,
                @JsonProperty("startTimeUnixNano") final String startTimeUnixNano,
                @JsonProperty("endTimeUnixNano") final String endTimeUnixNano,
                @JsonProperty("attributes") final List<KeyValue> attributes,
                @JsonProperty("droppedAttributesCount") final int droppedAttributesCount,
                @JsonProperty("events") final List<SpanEvent> events,
                @JsonProperty("droppedEventsCount") final int droppedEventsCount,
                @JsonProperty("links") final List<SpanLink> links,
                @JsonProperty("droppedLinksCount") final int droppedLinksCount,
                @JsonProperty("status") final SpanStatus status) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.traceState = traceState;
        this.parentSpanId = parentSpanId;
        this.flags = flags;
        this.name = name;
        this.kind = kind;
        this.startTimeUnixNano = startTimeUnixNano;
        this.endTimeUnixNano = endTimeUnixNano;
        this.attributes = attributes;
        this.droppedAttributesCount = droppedAttributesCount;
        this.events = events;
        this.droppedEventsCount = droppedEventsCount;
        this.links = links;
        this.droppedLinksCount = droppedLinksCount;
        this.status = status;
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

    public String getParentSpanId() {
        return parentSpanId;
    }

    public int getFlags() {
        return flags;
    }

    public String getName() {
        return name;
    }

    public SpanKind getKind() {
        return kind;
    }

    public String getStartTimeUnixNano() {
        return startTimeUnixNano;
    }

    public String getEndTimeUnixNano() {
        return endTimeUnixNano;
    }

    public List<KeyValue> getAttributes() {
        return attributes;
    }

    public int getDroppedAttributesCount() {
        return droppedAttributesCount;
    }

    public List<SpanEvent> getEvents() {
        return events;
    }

    public int getDroppedEventsCount() {
        return droppedEventsCount;
    }

    public List<SpanLink> getLinks() {
        return links;
    }

    public int getDroppedLinksCount() {
        return droppedLinksCount;
    }

    public SpanStatus getStatus() {
        return status;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Span span = (Span) o;
        return flags == span.flags &&
               droppedAttributesCount == span.droppedAttributesCount &&
               droppedEventsCount == span.droppedEventsCount &&
               droppedLinksCount == span.droppedLinksCount &&
               Objects.equals(traceId, span.traceId) &&
               Objects.equals(spanId, span.spanId) &&
               Objects.equals(traceState, span.traceState) &&
               Objects.equals(parentSpanId, span.parentSpanId) &&
               Objects.equals(name, span.name) &&
               kind == span.kind &&
               Objects.equals(startTimeUnixNano, span.startTimeUnixNano) &&
               Objects.equals(endTimeUnixNano, span.endTimeUnixNano) &&
               Objects.equals(attributes, span.attributes) &&
               Objects.equals(events, span.events) &&
               Objects.equals(links, span.links) &&
               Objects.equals(status, span.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(traceId,
                spanId,
                traceState,
                parentSpanId,
                flags,
                name,
                kind,
                startTimeUnixNano,
                endTimeUnixNano,
                attributes,
                droppedAttributesCount,
                events,
                droppedEventsCount,
                links,
                droppedLinksCount,
                status);
    }

    @Override
    public String toString() {
        return "Span{" +
               "traceId='" + traceId + '\'' +
               ", spanId='" + spanId + '\'' +
               ", traceState='" + traceState + '\'' +
               ", parentSpanId='" + parentSpanId + '\'' +
               ", flags=" + flags +
               ", name='" + name + '\'' +
               ", kind=" + kind +
               ", startTimeUnixNano='" + startTimeUnixNano + '\'' +
               ", endTimeUnixNano='" + endTimeUnixNano + '\'' +
               ", attributes=" + attributes +
               ", droppedAttributesCount=" + droppedAttributesCount +
               ", events=" + events +
               ", droppedEventsCount=" + droppedEventsCount +
               ", links=" + links +
               ", droppedLinksCount=" + droppedLinksCount +
               ", status=" + status +
               '}';
    }
}
