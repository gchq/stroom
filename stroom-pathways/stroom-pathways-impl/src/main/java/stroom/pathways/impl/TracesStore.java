package stroom.pathways.impl;

import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.util.shared.NullSafe;

import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Singleton
public class TracesStore {

    private final Map<String, Map<String, Map<String, Span>>> traceMap = new HashMap<>();

    public void addSpan(final Span span) {
        traceMap.computeIfAbsent(span.getTraceId(), k -> new ConcurrentHashMap<>())
                .computeIfAbsent(NullSafe.getOrElse(span, Span::getParentSpanId, ""),
                        k -> new ConcurrentHashMap<>())
                .put(span.getSpanId(), span);
    }

    public Collection<Trace> getTraces() {
        return traceMap.entrySet().stream().map(e -> {
            final Map<String, List<Span>> parentSpanIdMap = e
                    .getValue()
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            Entry::getKey,
                            entry -> entry.getValue().values().stream().toList()));
            return new Trace(e.getKey(), parentSpanIdMap);
        }).collect(Collectors.toSet());
    }
}
