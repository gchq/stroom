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

import stroom.docref.DocRef;
import stroom.pathways.shared.FindPathwayCriteria;
import stroom.pathways.shared.FindTraceCriteria;
import stroom.pathways.shared.GetTraceRequest;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.TracesStore;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.pathways.shared.pathway.PathKey;
import stroom.pathways.shared.pathway.PathNode;
import stroom.pathways.shared.pathway.Pathway;
import stroom.planb.impl.db.trace.NanoTimeUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class Pathways {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Pathways.class);

    private final DocRef docRef;
    private final PathwaysStore pathwaysStore;
    private final TracesStore tracesStore;
    private final MessageReceiverFactory messageReceiverFactory;
    private final Map<String, Pathway> pathways = new ConcurrentHashMap<>();
    private final Map<PathKey, PathNode> roots = new ConcurrentHashMap<>();
    private final Set<Trace> addedTraces = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public Pathways(final DocRef docRef,
                    final PathwaysStore pathwaysStore,
                    final TracesStore tracesStore,
                    final MessageReceiverFactory messageReceiverFactory) {
        this.docRef = docRef;
        this.pathwaysStore = pathwaysStore;
        this.tracesStore = tracesStore;
        this.messageReceiverFactory = messageReceiverFactory;
    }

    public void process() {
        final PathwaysDoc doc = pathwaysStore.readDocument(docRef);
        if (doc != null && doc.getTracesDocRef() != null) {
            final FindTraceCriteria criteria =
                    new FindTraceCriteria(PageRequest.unlimited(),
                            null,
                            doc.getTracesDocRef(),
                            doc.getTemporalOrderingTolerance());
            final ResultPage<TraceRoot> traces = tracesStore.findTraces(criteria);
            if (!NullSafe.isEmptyCollection(traces.getValues())) {
                addTraces(traces.getValues(), doc);
            }
        }
    }

    private void addTraces(final Collection<TraceRoot> traces,
                           final PathwaysDoc doc) {
        final DocRef infoFeed = doc.getInfoFeed();
        if (infoFeed != null && infoFeed.getName() != null) {
            messageReceiverFactory.create(infoFeed.getName(), messageReceiver -> {
                // Output the tree for each trace.
                for (final TraceRoot traceRoot : traces) {
                    final GetTraceRequest request = new GetTraceRequest(
                            doc.getTracesDocRef(),
                            traceRoot.getTraceId(),
                            doc.getTemporalOrderingTolerance());
                    final Trace trace = tracesStore.getTrace(request);
                    if (addedTraces.add(trace)) {
                        LOGGER.info("\n" + trace.toString());

                        // Construct known paths for all traces.
                        buildPathways(trace, doc, messageReceiver);

                        // Output found pathways.
                        for (final PathNode node : roots.values()) {
                            LOGGER.info("\n" + node.toString());
                        }

                        final Instant now = Instant.now();
                        final NanoTime nanoTime = NanoTimeUtil.fromInstant(now);
                        roots.forEach((key, value) -> pathways.compute(
                                key.toString(),
                                (k, v) -> {
                                    if (v == null) {
                                        return Pathway.builder()
                                                .name(key.toString())
                                                .createTime(nanoTime)
                                                .lastUsedTime(nanoTime)
                                                .pathKey(key)
                                                .root(value)
                                                .build();
                                    } else {
                                        return v
                                                .copy()
                                                .updateTime(nanoTime)
                                                .root(value)
                                                .build();
                                    }
                                }));
                    }
                }
            });
        }
    }

//    private void loadData(final Path path,
//                          final Map<String, Trace> traceMap) {
//        try (final BufferedReader lineReader = Files.newBufferedReader(path)) {
//            final String line = lineReader.readLine();
//            final ExportTraceServiceRequest exportRequest =
//                    MAPPER.readValue(line, ExportTraceServiceRequest.class);
//            for (final ResourceSpans resourceSpans : NullSafe.list(exportRequest.getResourceSpans())) {
//                for (final ScopeSpans scopeSpans : NullSafe.list(resourceSpans.getScopeSpans())) {
//                    for (final Span span : NullSafe.list(scopeSpans.getSpans())) {
//                        traceMap.computeIfAbsent(span.getTraceId(), Trace::new).addSpan(span);
//                    }
//                }
//            }
//
//        } catch (final IOException e) {
//            throw new UncheckedIOException(e);
//        }
//    }

    private void buildPathways(final Trace trace,
                               final PathwaysDoc doc,
                               final MessageReceiver messageReceiver) {
        final Comparator<Span> spanComparator = new CloseSpanComparator(doc.getTemporalOrderingTolerance());
        final PathKeyFactory pathKeyFactory = new PathKeyFactoryImpl();
        final TraceWalker traceProcessor = new NodeMutatorImpl(spanComparator, pathKeyFactory);
        traceProcessor.process(trace, roots, messageReceiver, doc);
    }

    public ResultPage<Pathway> findPathways(final FindPathwayCriteria criteria) {
        // Update traces to build pathways.
        // TODO : Do this with a background process.
        process();

        final List<Pathway> list = pathways
                .values()
                .stream()
                .sorted(Comparator.comparing(Pathway::getName))
                .collect(Collectors.toList());
        return ResultPage.createPageLimitedList(list, criteria.getPageRequest());
    }

    public Boolean addPathway(final Pathway pathway) {
        pathways.put(pathway.getName(), pathway);
        return true;
    }

    public Boolean updatePathway(final String existingName, final Pathway pathway) {
        pathways.remove(existingName);
        pathways.put(pathway.getName(), pathway);
        return true;
    }

    public Boolean deletePathway(final String name) {
        return pathways.remove(name) != null;
    }

//    private static ObjectMapper createMapper(final boolean indent) {
//        final ObjectMapper mapper = new ObjectMapper();
//        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
//        mapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
//        mapper.configure(SerializationFeature.INDENT_OUTPUT, indent);
//        mapper.setSerializationInclusion(Include.NON_NULL);
//        return mapper;
//    }
}
