package stroom.pathways.shared.otel.trace;

import stroom.util.shared.AbstractBuilder;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@JsonInclude(Include.NON_NULL)
public class Trace {

    @JsonProperty
    private final String traceId;
    @JsonProperty
    private final Map<String, List<Span>> parentSpanIdMap;

    @JsonCreator
    public Trace(@JsonProperty("traceId") final String traceId,
                 @JsonProperty("parentSpanIdMap") final Map<String, List<Span>> parentSpanIdMap) {
        this.traceId = traceId;
        this.parentSpanIdMap = parentSpanIdMap;
    }

    public String getTraceId() {
        return traceId;
    }

    public Map<String, List<Span>> getParentSpanIdMap() {
        return parentSpanIdMap;
    }

    public Span root() {
        final List<Span> roots = parentSpanIdMap.get("");
        if (roots != null) {
            if (roots.size() == 1) {
                return roots.get(0);
            } else if (roots.isEmpty()) {
                throw new RuntimeException("No root found");
            } else {
                throw new RuntimeException("Multiple roots found");
            }
        }
        throw new RuntimeException("No root found");
    }

    public List<Span> children(final Span span) {
        return NullSafe.list(parentSpanIdMap.get(span.getSpanId()));
    }

    public int services() {
        final Set<String> set = parentSpanIdMap
                .values()
                .stream()
                .flatMap(List::stream)
                .map(Span::getName)
                .collect(Collectors.toSet());
        return set.size();
    }

    public int depth() {
        final Span root = root();
        int depth = 1;
        depth = Math.max(depth, depth(root) + 1);
        return depth;
    }

    private int depth(final Span span) {
        int depth = 0;
        final List<Span> children = parentSpanIdMap.get(span.getSpanId());
        if (children == null || children.isEmpty()) {
            return 0;
        }
        depth = 1;
        for (final Span child : children) {
            depth = Math.max(depth, depth(child) + 1);
        }
        return depth;
    }

    public int totalSpans() {
        return parentSpanIdMap
                .values()
                .stream()
                .mapToInt(List::size)
                .sum();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        appendChild(sb, "", 0);
        return sb.toString();
    }

    private void appendChild(final StringBuilder sb, final String parentId, final int depth) {
        final List<Span> children = NullSafe.list(parentSpanIdMap.get(parentId));
        for (final Span span : children) {
            final NanoTime startTime = NanoTime.fromString(span.getStartTimeUnixNano());
            final NanoTime endTime = NanoTime.fromString(span.getEndTimeUnixNano());
            final NanoTime duration = endTime.subtract(startTime);

            for (int i = 0; i < depth * 3; i++) {
                sb.append(' ');
            }

            // Append name
            sb.append(span.getName());

            // Append duration
            sb.append(" (");

            duration.append(sb);

            sb.append(")\n");

            appendChild(sb, span.getSpanId(), depth + 1);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder extends AbstractBuilder<Trace, Builder> {

        private String traceId;
        private Map<String, List<Span>> parentSpanIdMap;

        private Builder() {
        }

        private Builder(final Trace trace) {
            this.traceId = trace.traceId;
            this.parentSpanIdMap = trace.parentSpanIdMap;
        }

        public Builder traceId(final String traceId) {
            this.traceId = traceId;
            return self();
        }

        public Builder parentSpanIdMap(final Map<String, List<Span>> parentSpanIdMap) {
            this.parentSpanIdMap = parentSpanIdMap;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public Trace build() {
            return new Trace(
                    traceId,
                    parentSpanIdMap
            );
        }
    }
}
