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
import stroom.pathways.shared.FindTraceCriteria;
import stroom.pathways.shared.GetTraceRequest;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.TracePersistence;
import stroom.pathways.shared.TraceWriter;
import stroom.pathways.shared.TracesStore;
import stroom.pathways.shared.otel.trace.ExportTraceServiceRequest;
import stroom.pathways.shared.otel.trace.NanoDuration;
import stroom.pathways.shared.otel.trace.ResourceSpans;
import stroom.pathways.shared.otel.trace.ScopeSpans;
import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.pathways.shared.pathway.PathKey;
import stroom.pathways.shared.pathway.PathNode;
import stroom.planb.shared.PlanBDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.string.StringIdUtil;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class TraceLoader {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TraceLoader.class);
    private static final ObjectMapper MAPPER = createMapper(true);

    private static final DocRef TRACE_STORE_DOC_REF = DocRef.builder().type(PlanBDoc.TYPE).uuid("traces").build();
    private static final PathwaysDoc PATHWAYS_DOC = PathwaysDoc.builder().uuid("1").build();

    public void addOneMore(final TracePersistence persistence) {
        try (final TraceWriter writer = persistence.createWriter()) {
            final Path path = Paths.get("src/test/resources/" + StringIdUtil.idToString(18) + ".dat");
            loadData(path, writer);
        }
    }

    public void load(final TracePersistence persistence) {
        try (final TraceWriter writer = persistence.createWriter()) {
            for (int i = 1; i <= 13; i++) {
                final Path path = Paths.get("src/test/resources/" + StringIdUtil.idToString(i) + ".dat");
                loadData(path, writer);
            }
        }

        // Output the tree for each trace.
        Collection<Trace> traces = getTraces(persistence);
        assertThat(traces.size()).isEqualTo(48);
        for (final Trace trace : traces) {
            LOGGER.info("\n" + trace.toString());
        }

        final StringBuilder messages = new StringBuilder();
        final MessageReceiver messageReceiver = (severity, message) -> {
            messages.append(severity.getDisplayValue());
            messages.append(": ");
            messages.append(message.get());
            messages.append("\n");
        };

        // Construct known paths for all traces.
        final Map<PathKey, PathNode> pathRoots = buildPathways(traces, messageReceiver);

        // Output found pathways.
        for (final PathNode node : pathRoots.values()) {
            LOGGER.info("\n" + node.toString());
        }

        // Validate traces against known paths.
        traces = getTraces(persistence);
        assertThat(traces.size()).isEqualTo(48);
        validate(traces, pathRoots, messageReceiver);

        // Introduce an invalid pathway.
        try (final TraceWriter writer = persistence.createWriter()) {
            for (int i = 14; i <= 17; i++) {
                final Path path = Paths.get("src/test/resources/" + StringIdUtil.idToString(i) + ".dat");
                loadData(path, writer);
            }
        }
        traces = getTraces(persistence);
        assertThat(traces.size()).isEqualTo(69);
        validate(traces, pathRoots, messageReceiver);
        assertThat(messages.toString()).contains("ERROR: [GET /people] thread.id '125' not equal");
    }

    private Collection<Trace> getTraces(final TracesStore tracesStore) {
        final FindTraceCriteria findTraceCriteria = new FindTraceCriteria(
                PageRequest.unlimited(),
                Collections.emptyList(),
                TRACE_STORE_DOC_REF,
                SimpleDuration.ZERO);
        final List<TraceRoot> traceRoots = tracesStore.findTraces(findTraceCriteria).getValues();
        final List<Trace> traces = new ArrayList<>(traceRoots.size());
        for (final TraceRoot traceRoot : traceRoots) {
            final Trace trace = tracesStore
                    .getTrace(new GetTraceRequest(TRACE_STORE_DOC_REF, traceRoot.getTraceId(), SimpleDuration.ZERO));
            if (trace != null) {
                traces.add(trace);
            }
        }
        return traces;
    }

    private Map<PathKey, PathNode> buildPathways(final Collection<Trace> traces,
                                                 final MessageReceiver messageReceiver) {
        final Comparator<Span> spanComparator = new CloseSpanComparator(NanoDuration.ofMillis(10));
        final PathKeyFactory pathKeyFactory = new PathKeyFactoryImpl();
        final TraceWalker traceProcessor = new NodeMutatorImpl(spanComparator, pathKeyFactory);
        final Map<PathKey, PathNode> roots = new HashMap<>();
        for (final Trace trace : traces) {
            traceProcessor.process(trace, roots, messageReceiver, PATHWAYS_DOC);
        }
        return roots;
    }

    private void validate(final Collection<Trace> traces,
                          final Map<PathKey, PathNode> roots,
                          final MessageReceiver messageReceiver) {
        final Comparator<Span> spanComparator = new CloseSpanComparator(NanoDuration.ofMillis(10));
        final PathKeyFactory pathKeyFactory = new PathKeyFactoryImpl();
        final TraceWalker traceProcessor = new TraceValidator(spanComparator, pathKeyFactory);
        for (final Trace trace : traces) {
            traceProcessor.process(trace, roots, messageReceiver, PATHWAYS_DOC);
        }
    }

    private void loadData(final Path path,
                          final TraceWriter writer) {
        try (final BufferedReader lineReader = Files.newBufferedReader(path)) {
            final String line = lineReader.readLine();
            final ExportTraceServiceRequest exportRequest =
                    MAPPER.readValue(line, ExportTraceServiceRequest.class);
            for (final ResourceSpans resourceSpans : NullSafe.list(exportRequest.getResourceSpans())) {
                for (final ScopeSpans scopeSpans : NullSafe.list(resourceSpans.getScopeSpans())) {
                    for (final Span span : NullSafe.list(scopeSpans.getSpans())) {
                        writer.addSpan(span);
                    }
                }
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
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
