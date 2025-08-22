package stroom.pathways.impl;

import stroom.docref.DocRef;
import stroom.pathways.shared.AddPathway;
import stroom.pathways.shared.DeletePathway;
import stroom.pathways.shared.FindPathwayCriteria;
import stroom.pathways.shared.UpdatePathway;
import stroom.pathways.shared.pathway.Pathway;
import stroom.util.shared.ResultPage;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class PathwaysService {

//    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PathwaysService.class);
//
//    private static final ObjectMapper MAPPER = createMapper(true);


    private final PathwaysStore pathwaysStore;
    private final TracesStore tracesStore;
    private final MessageReceiverFactory messageReceiverFactory;
    private final Map<DocRef, Pathways> pathwaysMap = new ConcurrentHashMap<>();

    @Inject
    public PathwaysService(final PathwaysStore pathwaysStore,
                           final TracesStore tracesStore,
                           final MessageReceiverFactory messageReceiverFactory) {
        this.pathwaysStore = pathwaysStore;
        this.tracesStore = tracesStore;
        this.messageReceiverFactory = messageReceiverFactory;

//        // FIXME: TEMPORARY - Construct test pathways
//
//
//        // Read in sample data and create a map of traces.
//        final Map<String, Trace> traceMap = new HashMap<>();
//        for (int i = 1; i <= 17; i++) {
//            final Path path = Paths.get(
//                    "/home/stroomdev66/work/stroom-master-temp2/" +
//                    "stroom-pathways/stroom-pathways-impl/src/test/resources/" + StringIdUtil.idToString(
//                            i) + ".dat");
//            loadData(path, traceMap);
//        }
//
//        addTraces(traceMap.values());
    }

//    public void addTraces(final Collection<Trace> traces) {
//        messageReceiverFactory.create("PATHWAY_INFO", messageReceiver -> {
//
//            // Output the tree for each trace.
//            for (final Trace trace : traces) {
//                if (addedTraces.add(trace)) {
//                    LOGGER.info("\n" + trace.toString());
//
//                    // Construct known paths for all traces.
//                    buildPathways(trace, messageReceiver);
//
//                    // Output found pathways.
//                    for (final PathNode node : roots.values()) {
//                        LOGGER.info("\n" + node.toString());
//                    }
//
//                    final Instant now = Instant.now();
//                    final NanoTime nanoTime = new NanoTime(now.getEpochSecond(), now.getNano());
//                    roots.forEach((key, value) -> pathways.compute(
//                            key.toString(),
//                            (k, v) -> {
//                                if (v == null) {
//                                    return Pathway.builder()
//                                            .name(key.toString())
//                                            .createTime(nanoTime)
//                                            .lastUsedTime(nanoTime)
//                                            .pathKey(key)
//                                            .root(value)
//                                            .build();
//                                } else {
//                                    return v
//                                            .copy()
//                                            .updateTime(nanoTime)
//                                            .root(value)
//                                            .build();
//                                }
//                            }));
//                }
//            }
//        });
//    }

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

//    private void buildPathways(final Trace trace,
//                               final MessageReceiver messageReceiver) {
//        final Comparator<Span> spanComparator = new CloseSpanComparator(NanoTime.ofMillis(10));
//        final PathKeyFactory pathKeyFactory = new PathKeyFactoryImpl();
//        final TraceProcessor traceProcessor = new NodeMutatorImpl(spanComparator, pathKeyFactory);
//        traceProcessor.process(trace, roots, messageReceiver);
//    }

    public ResultPage<Pathway> findPathways(final FindPathwayCriteria criteria) {
        final Pathways pathways = getPathways(criteria.getDataSourceRef());
        return pathways.findPathways(criteria);
    }

    private Pathways getPathways(final DocRef docRef) {
        return pathwaysMap.computeIfAbsent(docRef, k ->
                new Pathways(k, pathwaysStore, tracesStore, messageReceiverFactory));
    }

    public Boolean addPathway(final AddPathway addPathway) {
        final Pathways pathways = getPathways(addPathway.getDocRef());
        return pathways.addPathway(addPathway.getPathway());
    }

    public Boolean updatePathway(final UpdatePathway updatePathway) {
        final Pathways pathways = getPathways(updatePathway.getDocRef());
        return pathways.updatePathway(updatePathway.getName(), updatePathway.getPathway());
    }

    public Boolean deletePathway(final DeletePathway deletePathway) {
        final Pathways pathways = getPathways(deletePathway.getDocRef());
        return pathways.deletePathway(deletePathway.getName());
    }

    private static ObjectMapper createMapper(final boolean indent) {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        mapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, indent);
        mapper.setSerializationInclusion(Include.NON_NULL);
        return mapper;
    }
}
