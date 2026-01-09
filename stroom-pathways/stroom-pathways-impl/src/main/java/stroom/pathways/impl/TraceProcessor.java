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

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.pathway.PathKey;
import stroom.pathways.shared.pathway.PathNode;
import stroom.pathways.shared.pathway.Pathway;
import stroom.planb.impl.db.LmdbWriter;
import stroom.planb.impl.db.trace.NanoTimeUtil;
import stroom.planb.impl.db.trace.PathwaysDb;
import stroom.planb.impl.db.trace.PathwaysDb.SimpleDb;
import stroom.planb.impl.serde.trace.HexStringUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Severity;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Function;

public class TraceProcessor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PathwaysProcessor.class);
    private static final ByteBuffer PROCESSED = ByteBuffer.allocateDirect(0);

    private final ByteBuffers byteBuffers;
    private final PathwaySerde pathwaySerde;

    public TraceProcessor(final ByteBuffers byteBuffers,
                          final PathwaySerde pathwaySerde) {
        this.byteBuffers = byteBuffers;
        this.pathwaySerde = pathwaySerde;
    }

    public void processTrace(final LmdbWriter writer,
                             final PathwaysDb pathwaysDb,
                             final byte[] traceId,
                             final Function<byte[], Trace> traceFunction,
                             final PathwaysDoc doc,
                             final MessageReceiver messageReceiver) {
        try {
            byteBuffers.useBytes(traceId, keyByteBuffer -> {
                final SimpleDb processingStatus = pathwaysDb.getProcessingStatus();
                final boolean processed = processingStatus
                        .get(writer.getWriteTxn(), keyByteBuffer.duplicate(), Objects::nonNull);
                if (!processed) {
                    // Get the full trace.
                    final Trace trace = traceFunction.apply(traceId);

                    // If we have no status then process this trace root.
                    LOGGER.debug(() -> "\n" + trace.toString());

                    // Construct known paths for all traces.
                    buildPathways(writer, trace, doc, messageReceiver, pathwaysDb);

                    // After processing record that we have processed.
                    processingStatus.insert(writer, keyByteBuffer, PROCESSED);

                    writer.tryCommit();
                }
            });
        } catch (final RuntimeException e) {
            LOGGER.error("Error processing trace {}", HexStringUtil.encode(traceId), e);
        }
    }

    private void buildPathways(final LmdbWriter writer,
                               final Trace trace,
                               final PathwaysDoc doc,
                               final MessageReceiver messageReceiver,
                               final PathwaysDb pathwaysDb) {
        final Comparator<Span> spanComparator = new CloseSpanComparator(doc.getTemporalOrderingTolerance());
        final PathKeyFactory pathKeyFactory = new PathKeyFactoryImpl();
        final NodeMutatorImpl nodeMutator = new NodeMutatorImpl(spanComparator, pathKeyFactory);

        final Span root = trace.root();
        final PathKey pathKey = pathKeyFactory.create(Collections.singletonList(root));

        // Load current path.
        final SimpleDb pathways = pathwaysDb.getPathways();
        final byte[] keyBytes = pathKey.toString().getBytes(StandardCharsets.UTF_8);
        byteBuffers.useBytes(keyBytes, keyByteBuffer -> {
            Pathway pathway = pathways.get(writer.getWriteTxn(), keyByteBuffer, valueByteBuffer -> {
                if (valueByteBuffer == null) {
                    messageReceiver.log(Severity.INFO, () -> "Adding new root path: " + root.getName());
                    final PathNode pathNode = new PathNode(root.getName());
                    final Instant now = Instant.now();
                    final NanoTime nanoTime = NanoTimeUtil.fromInstant(now);
                    return Pathway.builder()
                            .name(pathKey.toString())
                            .createTime(nanoTime)
                            .lastUsedTime(nanoTime)
                            .pathKey(pathKey)
                            .root(pathNode)
                            .build();
                }
                return pathwaySerde.readPathway(valueByteBuffer);
            });

            PathNode pathNode = pathway.getRoot();
            pathNode = nodeMutator.process(trace, pathKey, pathNode, messageReceiver, doc);

            // Update pathway in database.
            final Instant now = Instant.now();
            final NanoTime nanoTime = NanoTimeUtil.fromInstant(now);
            pathway = pathway
                    .copy()
                    .updateTime(nanoTime)
                    .root(pathNode)
                    .build();

            // Write pathway.
            pathwaySerde.writePathway(pathway, byteBuffer ->
                    pathways.insert(writer, keyByteBuffer, byteBuffer));
        });
    }
}
