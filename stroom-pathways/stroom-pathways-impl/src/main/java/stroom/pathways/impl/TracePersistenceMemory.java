package stroom.pathways.impl;

import stroom.pathways.shared.FindTraceCriteria;
import stroom.pathways.shared.GetTraceRequest;
import stroom.pathways.shared.TracePersistence;
import stroom.pathways.shared.TraceWriter;
import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class TracePersistenceMemory implements TracePersistence {

    private final Traces traces = new Traces();

    @Override
    public TraceWriter createWriter() {
        return new TraceWriter() {
            @Override
            public void addSpan(final Span span) {
                traces.addSpan(span);
            }

            @Override
            public void close() {

            }
        };
    }

    public Collection<Trace> getTraces() {
        return traces.getTraces();
    }

    @Override
    public ResultPage<TraceRoot> findTraces(final FindTraceCriteria criteria) {
        final Comparator<Span> spanComparator = new CloseSpanComparator(criteria.getTemporalOrderingTolerance());
        final PathKeyFactory pathKeyFactory = new PathKeyFactoryImpl();
        final Collection<Trace> traces = getTraces();
        if (criteria.getPathway() != null) {
            final TracePredicate tracePredicate = new TracePredicate(
                    spanComparator,
                    pathKeyFactory,
                    Map.of(criteria.getPathway().getPathKey(), criteria.getPathway().getRoot()));
            final List<TraceRoot> filtered = traces
                    .stream()
                    .filter(tracePredicate)
                    .map(TraceRoot::new)
                    .toList();
            return ResultPage.createPageLimitedList(filtered, criteria.getPageRequest());

        } else {
            return ResultPage.createPageLimitedList(traces
                    .stream()
                    .map(TraceRoot::new)
                    .toList(), criteria.getPageRequest());
        }
    }

    @Override
    public Trace getTrace(final GetTraceRequest request) {
        final TraceBuilder traceBuilder = traces.traceMap.get(request.getTraceId());
        if (traceBuilder == null) {
            return null;
        }
        return traceBuilder.build();
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
                            entry -> entry
                                    .getValue()
                                    .values()
                                    .stream()
                                    .sorted(Comparator.comparing(Span::start))
                                    .toList()));
            return Trace.builder().traceId(traceId).parentSpanIdMap(parentSpanIdMap).build();
        }
    }
}
