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

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.docref.DocRef;
import stroom.node.api.NodeInfo;
import stroom.pathways.shared.FindPathwayCriteria;
import stroom.pathways.shared.PathwayResultPage;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.pathway.Pathway;
import stroom.planb.impl.data.ShardManager;
import stroom.planb.impl.db.Count;
import stroom.planb.impl.db.LmdbWriter;
import stroom.planb.impl.db.trace.PathwaysDb;
import stroom.planb.impl.db.trace.TraceDb;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class PathwaysProcessor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PathwaysProcessor.class);
    private static final ByteBuffer PROCESSED = ByteBuffer.allocate(0);

    private final PathwaysStore pathwaysStore;
    private final MessageReceiverFactory messageReceiverFactory;
    private final ByteBuffers byteBuffers;
    private final Path dbPath;
    private final Map<String, PathwaysDb> pathwaysDbMap = new ConcurrentHashMap<>();
    private final PathwaySerde pathwaySerde;
    private final ShardManager shardManager;
    private final NodeInfo nodeInfo;

    @Inject
    public PathwaysProcessor(final PathwaysStore pathwaysStore,
                             final MessageReceiverFactory messageReceiverFactory,
                             final PathCreator pathCreator,
                             final ByteBuffers byteBuffers,
                             final PathwaySerde pathwaySerde,
                             final ShardManager shardManager,
                             final NodeInfo nodeInfo) {
        this.pathwaysStore = pathwaysStore;
        this.messageReceiverFactory = messageReceiverFactory;
        this.byteBuffers = byteBuffers;
        this.pathwaySerde = pathwaySerde;
        this.shardManager = shardManager;
        this.nodeInfo = nodeInfo;

        dbPath = pathCreator.toAppPath("${stroom.home}/pathways");
    }

    public void exec() {
        final List<DocRef> docRefs = pathwaysStore.list();
        for (final DocRef docRef : NullSafe.list(docRefs)) {
            final PathwaysDoc doc = pathwaysStore.readDocument(docRef);
            if (doc != null &&
                doc.getTracesDocRef() != null &&
                Objects.equals(doc.getProcessingNode(), nodeInfo.getThisNodeName())) {

                // Check that this is the node that trace stores are likely to be located.
                if (shardManager.isSnapshotNode()) {
                    throw new RuntimeException("Attempt to run pathways processing on different node to trace store");
                }

                // Load pathways DB for doc.
                final PathwaysDb pathwaysDb = getPathwaysDb(docRef);

                final DocRef infoFeed = doc.getInfoFeed();
                if (infoFeed != null && infoFeed.getName() != null) {
                    messageReceiverFactory.create(infoFeed.getName(), messageReceiver -> {

                        shardManager.get(doc.getTracesDocRef().getName(), db -> {
                            if (db instanceof final TraceDb traceDb) {

                                try (final LmdbWriter writer = pathwaysDb.createWriter()) {
                                    final TraceProcessor traceProcessor =
                                            new TraceProcessor(byteBuffers, pathwaySerde);
                                    traceDb.iterateTraces((traceId, function) ->
                                            traceProcessor.processTrace(writer,
                                                    pathwaysDb,
                                                    traceId,
                                                    function,
                                                    doc,
                                                    messageReceiver));
                                    writer.commit();
                                }
                            }
                            return null;
                        });
                    });
                }
            }
        }
    }

    private PathwaysDb getPathwaysDb(final DocRef docRef) {
        return pathwaysDbMap.computeIfAbsent(docRef.getUuid(), k -> {
            try {
                final Path processingPath = dbPath.resolve("pathways").resolve(docRef.getUuid());
                Files.createDirectories(processingPath);
                return PathwaysDb.create(processingPath, byteBuffers, false);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public PathwayResultPage findPathways(final FindPathwayCriteria criteria) {
        final PathwaysDb pathwaysDb = getPathwaysDb(criteria.getDataSourceRef());
        final Count count = new Count();
        final List<Pathway> list = new ArrayList<>();
        final PageRequest pageRequest = criteria.getPageRequest();
        pathwaysDb.getPathways().iterate((key, val) -> {
            boolean match = false;
            if (NullSafe.isNonEmptyString(criteria.getFilter())) {
                final String string = ByteBufferUtils.byteBufferToString(key);
                if (string.contains(criteria.getFilter())) {
                    match = true;
                }
            } else {
                match = true;
            }

            if (match) {
                final long pos = count.getAndIncrement();
                if (pos >= criteria.getPageRequest().getOffset() &&
                    pos < criteria.getPageRequest().getOffset() + criteria.getPageRequest().getLength()) {
                    list.add(pathwaySerde.readPathway(val));
                }
            }
        });

        final PageResponse pageResponse = PageResponse
                .builder()
                .offset(pageRequest.getOffset())
                .length(list.size())
                .total(count.get())
                .exact(true)
                .build();
        return new PathwayResultPage(list, pageResponse);
    }
}
