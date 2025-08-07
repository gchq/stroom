package stroom.pathways;

import stroom.pathways.model.trace.Span;
import stroom.util.shared.NullSafe;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Trace {

    private static final DurationUtil DURATION_UTIL = new DurationUtil();

    private final String traceId;
    private final Map<String, Span> spanIdMap = new HashMap<>();
    private final Map<String, List<Span>> parentSpanIdMap = new HashMap<>();

    public Trace(final String traceId) {
        this.traceId = traceId;
    }

    public Span getRoot() {
        final List<Span> roots = parentSpanIdMap.get("");
        if (roots != null) {
            if (roots.size() == 1) {
                return roots.getFirst();
            } else if (roots.isEmpty()) {
                throw new RuntimeException("No root found");
            } else {
                throw new RuntimeException("Multiple roots found");
            }
        }
        throw new RuntimeException("No root found");
    }

    public List<Span> getChildren(final Span span) {
        return NullSafe.list(parentSpanIdMap.get(span.getSpanId()));
    }

    public void addSpan(final Span span) {
        spanIdMap.put(span.getSpanId(), span);
        parentSpanIdMap.computeIfAbsent(
                        Objects.requireNonNullElse(span.getParentSpanId(), ""), k -> new ArrayList<>())
                .add(span);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        appendChild(sb, "", 0);
        return sb.toString();
    }

    private void appendChild(final StringBuilder sb, final String parentId, final int depth) {
        final List<Span> children = new ArrayList<>(NullSafe.list(parentSpanIdMap.get(parentId)));
        children.sort(Comparator.comparing(span -> NanoTimeUtil.get(span.getStartTimeUnixNano())));

        for (final Span span : children) {
            final Instant startTime = NanoTimeUtil.get(span.getStartTimeUnixNano());
            final Instant endTime = NanoTimeUtil.get(span.getEndTimeUnixNano());
            final Duration duration = Duration.between(startTime, endTime);

            for (int i = 0; i < depth * 3; i++) {
                sb.append(' ');
            }

            // Append name
            sb.append(span.getName());

            // Append duration
            sb.append(" (");

            DURATION_UTIL.append(sb, duration);

            sb.append(")\n");

            appendChild(sb, span.getSpanId(), depth + 1);
        }
    }
}
