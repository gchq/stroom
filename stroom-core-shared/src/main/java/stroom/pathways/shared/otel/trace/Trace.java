package stroom.pathways.shared.otel.trace;

import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Trace {

    private final String traceId;
    private final Map<String, Span> spanIdMap = new HashMap<>();
    private final Map<String, List<Span>> parentSpanIdMap = new HashMap<>();

    public Trace(final String traceId) {
        this.traceId = traceId;
    }

    public String getTraceId() {
        return traceId;
    }

    public Span getRoot() {
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

    public List<Span> getChildren(final Span span) {
        return NullSafe.list(parentSpanIdMap.get(span.getSpanId()));
    }

    public void addSpan(final Span span) {
        spanIdMap.put(span.getSpanId(), span);
        parentSpanIdMap.computeIfAbsent(
                        NullSafe.getOrElse(span, Span::getParentSpanId, ""), k -> new ArrayList<>())
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
        children.sort(Comparator.comparing(span -> NanoTime.fromString(span.getStartTimeUnixNano())));

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
}
