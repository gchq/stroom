/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.pathways.impl;

import stroom.pathways.shared.FindTraceCriteria;
import stroom.pathways.shared.GetSpansRequest;
import stroom.pathways.shared.GetTraceOverviewRequest;
import stroom.pathways.shared.GetTraceRequest;
import stroom.pathways.shared.TraceOverview;
import stroom.pathways.shared.TracePersistence;
import stroom.pathways.shared.TraceSpanPage;
import stroom.pathways.shared.TraceSpanRow;
import stroom.pathways.shared.TraceWriter;
import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
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

    @Override
    public TraceSpanPage getSpans(final GetSpansRequest request) {
        final TraceBuilder traceBuilder = traces.traceMap.get(request.getTraceId());
        final Trace trace = traceBuilder == null ? null : traceBuilder.build();
        final List<TraceSpanRow> all = flattenPreorder(trace);
        final int from = Math.min(Math.max(0, request.getOffset()), all.size());
        final int to = request.getLimit() <= 0
                ? from
                : Math.min(from + request.getLimit(), all.size());
        return new TraceSpanPage(new ArrayList<>(all.subList(from, to)), to < all.size(), null);
    }

    @Override
    public TraceOverview getTraceOverview(final GetTraceOverviewRequest request) {
        final TraceBuilder traceBuilder = traces.traceMap.get(request.getTraceId());
        final List<Span> spans = new ArrayList<>();
        if (traceBuilder != null) {
            traceBuilder.build().getParentSpanIdMap().values().forEach(spans::addAll);
        }
        return new TraceOverview(spans);
    }

    // Flattens a trace to pre-order (tree) order, tagging each span with its depth. Roots are spans
    // whose parent is not itself a span in the trace; a visited set guards against malformed cycles.
    private static List<TraceSpanRow> flattenPreorder(final Trace trace) {
        final List<TraceSpanRow> rows = new ArrayList<>();
        if (trace == null || trace.getParentSpanIdMap() == null) {
            return rows;
        }
        final Map<String, List<Span>> byParent = trace.getParentSpanIdMap();
        final Set<String> spanIds = new HashSet<>();
        byParent.values().forEach(list -> list.forEach(s -> spanIds.add(s.getSpanId())));

        final List<Span> roots = new ArrayList<>();
        for (final List<Span> list : byParent.values()) {
            for (final Span span : list) {
                if (span.getParentSpanId() == null || !spanIds.contains(span.getParentSpanId())) {
                    roots.add(span);
                }
            }
        }
        roots.sort(Comparator.comparing(Span::start, Comparator.nullsLast(Comparator.naturalOrder())));

        final Set<String> visited = new HashSet<>();
        for (final Span root : roots) {
            appendSubtree(root, 0, byParent, rows, visited);
        }
        return rows;
    }

    private static void appendSubtree(final Span span,
                                      final int depth,
                                      final Map<String, List<Span>> byParent,
                                      final List<TraceSpanRow> rows,
                                      final Set<String> visited) {
        if (!visited.add(span.getSpanId())) {
            return;
        }
        rows.add(new TraceSpanRow(span, depth));
        final List<Span> children = byParent.get(span.getSpanId());
        if (children != null) {
            final List<Span> sorted = new ArrayList<>(children);
            sorted.sort(Comparator.comparing(Span::start, Comparator.nullsLast(Comparator.naturalOrder())));
            for (final Span child : sorted) {
                appendSubtree(child, depth + 1, byParent, rows, visited);
            }
        }
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
