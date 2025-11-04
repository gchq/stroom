package stroom.pathways.shared.otel.trace;

import stroom.util.shared.AbstractBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@JsonInclude(Include.NON_NULL)
public class TraceRoot {

    @JsonProperty
    private final String traceId;
    @JsonProperty
    private final String name;
    @JsonProperty
    private final NanoTime startTime;
    @JsonProperty
    private final NanoTime endTime;
    @JsonProperty
    private final int services;
    @JsonProperty
    private final int depth;
    @JsonProperty
    private final int totalSpans;

    public TraceRoot(final Trace trace) {
        this.traceId = trace.getTraceId();
        this.name = trace.root().getName();
        this.startTime = trace.root().start();
        this.endTime = trace.root().end();
        this.services = services(trace);
        this.depth = depth(trace);
        this.totalSpans = totalSpans(trace);
    }

    private static int services(final Trace trace) {
        final Set<String> set = trace.getParentSpanIdMap()
                .values()
                .stream()
                .flatMap(List::stream)
                .map(Span::getName)
                .collect(Collectors.toSet());
        return set.size();
    }

    private static int depth(final Trace trace) {
        final Span root = trace.root();
        int depth = 1;
        depth = Math.max(depth, depth(trace, root) + 1);
        return depth;
    }

    private static int depth(final Trace trace, final Span span) {
        int depth = 0;
        final List<Span> children = trace.getParentSpanIdMap().get(span.getSpanId());
        if (children == null || children.isEmpty()) {
            return 0;
        }
        depth = 1;
        for (final Span child : children) {
            depth = Math.max(depth, depth(trace, child) + 1);
        }
        return depth;
    }

    private static int totalSpans(final Trace trace) {
        return trace.getParentSpanIdMap()
                .values()
                .stream()
                .mapToInt(List::size)
                .sum();
    }

    @JsonCreator
    public TraceRoot(@JsonProperty("traceId") final String traceId,
                     @JsonProperty("name") final String name,
                     @JsonProperty("startTime") final NanoTime startTime,
                     @JsonProperty("endTime") final NanoTime endTime,
                     @JsonProperty("services") final int services,
                     @JsonProperty("depth") final int depth,
                     @JsonProperty("totalSpans") final int totalSpans) {
        this.traceId = traceId;
        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
        this.services = services;
        this.depth = depth;
        this.totalSpans = totalSpans;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getName() {
        return name;
    }

    public NanoTime getStartTime() {
        return startTime;
    }

    public NanoTime getEndTime() {
        return endTime;
    }

    public int getServices() {
        return services;
    }

    public int getDepth() {
        return depth;
    }

    public int getTotalSpans() {
        return totalSpans;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TraceRoot traceRoot = (TraceRoot) o;
        return services == traceRoot.services &&
               depth == traceRoot.depth &&
               totalSpans == traceRoot.totalSpans &&
               Objects.equals(traceId, traceRoot.traceId) &&
               Objects.equals(name, traceRoot.name) &&
               Objects.equals(startTime, traceRoot.startTime) &&
               Objects.equals(endTime, traceRoot.endTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(traceId, name, startTime, endTime, services, depth, totalSpans);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder extends AbstractBuilder<TraceRoot, Builder> {

        private String traceId;
        private String name;
        private NanoTime startTime;
        private NanoTime endTime;
        private int services;
        private int depth;
        private int totalSpans;

        private Builder() {
        }

        private Builder(final TraceRoot traceRoot) {
            this.traceId = traceRoot.traceId;
            this.name = traceRoot.name;
            this.startTime = traceRoot.startTime;
            this.endTime = traceRoot.endTime;
            this.services = traceRoot.services;
            this.depth = traceRoot.depth;
            this.totalSpans = traceRoot.totalSpans;
        }

        public Builder traceId(final String traceId) {
            this.traceId = traceId;
            return self();
        }

        public Builder name(final String name) {
            this.name = name;
            return self();
        }

        public Builder startTime(final NanoTime startTime) {
            this.startTime = startTime;
            return self();
        }

        public Builder endTime(final NanoTime endTime) {
            this.endTime = endTime;
            return self();
        }

        public Builder services(final int services) {
            this.services = services;
            return self();
        }

        public Builder depth(final int depth) {
            this.depth = depth;
            return self();
        }

        public Builder totalSpans(final int totalSpans) {
            this.totalSpans = totalSpans;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public TraceRoot build() {
            return new TraceRoot(
                    traceId,
                    name,
                    startTime,
                    endTime,
                    services,
                    depth,
                    totalSpans
            );
        }
    }
}
