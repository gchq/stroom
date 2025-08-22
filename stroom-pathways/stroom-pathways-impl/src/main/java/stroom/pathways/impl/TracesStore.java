package stroom.pathways.impl;

import stroom.docref.DocRef;
import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.util.shared.NullSafe;

import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Singleton
public class TracesStore {

    private final Map<DocRef, Traces> tracesMap = new ConcurrentHashMap<>();

    public void addSpan(final DocRef docRef, final Span span) {
        tracesMap.computeIfAbsent(docRef, k -> new Traces()).addSpan(span);
    }

    public Collection<Trace> getTraces(final DocRef docRef) {
        final Traces traces = tracesMap.get(docRef);
        if (traces == null) {
            return Collections.emptyList();
        }
        return traces.getTraces();
    }

    private static class Traces {

        private final Map<String, TraceBuilder> traceMap = new ConcurrentHashMap<>();

        public void addSpan(final Span span) {
            traceMap.computeIfAbsent(span.getTraceId(), TraceBuilder::new)
                    .addSpan(span);
        }

        public Collection<Trace> getTraces() {
            return traceMap.values().stream().map(TraceBuilder::build).collect(Collectors.toSet());
        }
    }

    private static class TraceBuilder {

        private final String traceId;
        private final Map<String, Map<String, Span>> traceMap = new ConcurrentHashMap<>();

        public TraceBuilder(final String traceId) {
            this.traceId = traceId;
        }

        public void addSpan(final Span span) {
            traceMap.computeIfAbsent(NullSafe.getOrElse(span, Span::getParentSpanId, ""),
                            k -> new ConcurrentHashMap<>())
                    .put(span.getSpanId(), span);
        }

        public Trace build() {
            final Map<String, List<Span>> parentSpanIdMap = traceMap
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            Entry::getKey,
                            entry -> entry.getValue().values().stream().toList()));
            return new Trace(traceId, parentSpanIdMap);
        }
    }
}
