package stroom.planb.impl.serde.trace;

import stroom.pathways.shared.otel.trace.KeyValue;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.otel.trace.SpanEvent;
import stroom.pathways.shared.otel.trace.SpanKind;
import stroom.pathways.shared.otel.trace.SpanLink;
import stroom.pathways.shared.otel.trace.SpanStatus;
import stroom.planb.impl.db.trace.NanoTimeUtil;
import stroom.util.shared.AbstractBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class SpanValue {

    @JsonProperty("insertTime")
    private final NanoTime insertTime;

    @JsonProperty("traceState")
    private final String traceState;

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
    public SpanValue(@JsonProperty("insertTime") final NanoTime insertTime,
                     @JsonProperty("traceState") final String traceState,
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
        this.insertTime = insertTime;
        this.traceState = traceState;
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

    public NanoTime getInsertTime() {
        return insertTime;
    }

    public String getTraceState() {
        return traceState;
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
        final SpanValue span = (SpanValue) o;
        return flags == span.flags &&
               droppedAttributesCount == span.droppedAttributesCount &&
               droppedEventsCount == span.droppedEventsCount &&
               droppedLinksCount == span.droppedLinksCount &&
               Objects.equals(traceState, span.traceState) &&
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
        return Objects.hash(
                traceState,
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
               "traceState='" + traceState + '\'' +
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

    public static SpanValue create(final Span span) {
        return new Builder(span).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder extends AbstractBuilder<SpanValue, SpanValue.Builder> {

        private NanoTime insertTime;
        private String traceState;
        private int flags;
        private String name;
        private SpanKind kind;
        private String startTimeUnixNano;
        private String endTimeUnixNano;
        private List<KeyValue> attributes;
        private int droppedAttributesCount;
        private List<SpanEvent> events;
        private int droppedEventsCount;
        private List<SpanLink> links;
        private int droppedLinksCount;
        private SpanStatus status;

        private Builder() {
        }

        private Builder(final SpanValue span) {
            this.insertTime = span.insertTime;
            this.traceState = span.traceState;
            this.flags = span.flags;
            this.name = span.name;
            this.kind = span.kind;
            this.startTimeUnixNano = span.startTimeUnixNano;
            this.endTimeUnixNano = span.endTimeUnixNano;
            this.attributes = span.attributes;
            this.droppedAttributesCount = span.droppedAttributesCount;
            this.events = span.events;
            this.droppedEventsCount = span.droppedEventsCount;
            this.links = span.links;
            this.droppedLinksCount = span.droppedLinksCount;
            this.status = span.status;
        }

        public Builder(final Span span) {
            this.insertTime = NanoTimeUtil.now();
            this.traceState = span.getTraceState();
            this.flags = span.getFlags();
            this.name = span.getName();
            this.kind = span.getKind();
            this.startTimeUnixNano = span.getStartTimeUnixNano();
            this.endTimeUnixNano = span.getEndTimeUnixNano();
            this.attributes = span.getAttributes();
            this.droppedAttributesCount = span.getDroppedAttributesCount();
            this.events = span.getEvents();
            this.droppedEventsCount = span.getDroppedEventsCount();
            this.links = span.getLinks();
            this.droppedLinksCount = span.getDroppedLinksCount();
            this.status = span.getStatus();
        }

        public Builder insertTime(final NanoTime insertTime) {
            this.insertTime = insertTime;
            return self();
        }

        public Builder traceState(final String traceState) {
            this.traceState = traceState;
            return self();
        }

        public Builder flags(final int flags) {
            this.flags = flags;
            return self();
        }

        public Builder name(final String name) {
            this.name = name;
            return self();
        }

        public Builder kind(final SpanKind kind) {
            this.kind = kind;
            return self();
        }

        public Builder startTimeUnixNano(final NanoTime startTimeUnixNano) {
            this.startTimeUnixNano = startTimeUnixNano.toNanoEpochString();
            return self();
        }

        public Builder startTimeUnixNano(final String startTimeUnixNano) {
            this.startTimeUnixNano = startTimeUnixNano;
            return self();
        }

        public Builder endTimeUnixNano(final NanoTime endTimeUnixNano) {
            this.endTimeUnixNano = endTimeUnixNano.toNanoEpochString();
            return self();
        }

        public Builder endTimeUnixNano(final String endTimeUnixNano) {
            this.endTimeUnixNano = endTimeUnixNano;
            return self();
        }

        public Builder attributes(final List<KeyValue> attributes) {
            this.attributes = attributes;
            return self();
        }

        public Builder droppedAttributesCount(final int droppedAttributesCount) {
            this.droppedAttributesCount = droppedAttributesCount;
            return self();
        }

        public Builder events(final List<SpanEvent> events) {
            this.events = events;
            return self();
        }

        public Builder droppedEventsCount(final int droppedEventsCount) {
            this.droppedEventsCount = droppedEventsCount;
            return self();
        }

        public Builder links(final List<SpanLink> links) {
            this.links = links;
            return self();
        }

        public Builder droppedLinksCount(final int droppedLinksCount) {
            this.droppedLinksCount = droppedLinksCount;
            return self();
        }

        public Builder status(final SpanStatus status) {
            this.status = status;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public SpanValue build() {
            return new SpanValue(
                    insertTime,
                    traceState,
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
                    status
            );
        }
    }
}
