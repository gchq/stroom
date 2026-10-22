/*
 * Copyright 2026 Crown Copyright
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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.MergedPipeline;
import stroom.ai.impl.tools.MergedPipeline.ElementProperty;
import stroom.ai.impl.tools.StreamReader;
import stroom.docref.DocRef;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexResource;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.LuceneIndexDoc;
import stroom.meta.shared.Meta;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.processor.shared.ProcessorFilter;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Selection;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// What indexing a build's sample needs of the index it goes into: the Lucene index an indexing pipeline writes to,
/// whether the build may empty it, what the cluster admin drops first for Elasticsearch, and the small batch
/// Elasticsearch indexing runs in while it is developed. Ported from the Stroom MCP server's
/// `tools/processing_writes.py` (`lucene_index`, `_own_index`, `_writes_refused`, `drop_request`, `_fresh_index`,
/// `_delete_index_shards` and `development_batch`), for the processing tools and promotion to share.
///
/// A Lucene index's shards are deleted through Stroom's own index service ([IndexResource], as its Shards tab
/// deletes them: the user needs the Manage Index Shards permission). Elasticsearch is never written to: dropping an
/// index there is a request for the cluster admin.
class IndexingRuns {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexingRuns.class);

    /// While a pipeline is developed in the workspace, Elasticsearch indexing runs in small batches: a rejected
    /// document is then reported with Elasticsearch's own reason, whole, rather than cut short in one large bulk
    /// response.
    static final int DEV_BATCH_SIZE = 10;
    /// What a result says of the small batch.
    static final String BATCH_NOTE = "batch size " + DEV_BATCH_SIZE + " while developing, so each document "
                                     + "Elasticsearch rejects is reported with its reason; the template's default "
                                     + "comes back once indexing completes without errors";
    /// The details key for an index this pipeline wrote to before, which is dropped (or emptied) by the user first.
    static final String ALREADY_INDEXED = "already indexed";

    // What Elasticsearch says when it refuses the writes themselves: the account, or an index it won't make.
    private static final Pattern REFUSED = Pattern.compile(
            "security_exception|is unauthorized for|index_not_found_exception|no such index",
            Pattern.CASE_INSENSITIVE);
    // The most of an Error stream read for Elasticsearch's reason.
    private static final int ERROR_BYTES = 100_000;
    // The most streams a pipeline's filters processed whose Error streams are read.
    private static final int REFUSED_STREAMS = 10;
    private static final int MAX_SHARDS = 1_000;
    private static final String EVERY = "every document goes with it, so this processes all the Events streams the "
                                        + "index should hold (reprocess_streams takes those this pipeline indexed "
                                        + "before)";

    private final PipelineShapes pipelineShapes;
    private final PipelineDocs pipelineDocs;
    private final WriteGuard guard;
    private final StreamProcessing streamProcessing;
    private final Provider<IndexResource> indexResourceProvider;
    private final ErrorText errorText;

    /// @param pipelineShapes        Reads a pipeline as Stroom runs it.
    /// @param pipelineDocs          Reads and saves pipelines as the user.
    /// @param guard                 Says what is in a build.
    /// @param streamProcessing      Reads what a pipeline produced from a stream.
    /// @param indexResourceProvider Finds and deletes a Lucene index's shards as the user.
    /// @param streamReader          Reads Error streams as the user (with the View Data permission: without it,
    ///                              Elasticsearch's refusal of earlier writes is not read, and the index is
    ///                              taken to hold what the pipeline indexed before).
    @Inject
    IndexingRuns(final PipelineShapes pipelineShapes,
                 final PipelineDocs pipelineDocs,
                 final WriteGuard guard,
                 final StreamProcessing streamProcessing,
                 final Provider<IndexResource> indexResourceProvider,
                 final StreamReader streamReader) {
        this(pipelineShapes, pipelineDocs, guard, streamProcessing, indexResourceProvider,
                streamId -> streamReader.errorStreamText(streamId, ERROR_BYTES).text());
    }

    /// As the injected constructor, with how an Error stream's text is read given, for tests.
    IndexingRuns(final PipelineShapes pipelineShapes,
                 final PipelineDocs pipelineDocs,
                 final WriteGuard guard,
                 final StreamProcessing streamProcessing,
                 final Provider<IndexResource> indexResourceProvider,
                 final ErrorText errorText) {
        this.pipelineShapes = pipelineShapes;
        this.pipelineDocs = pipelineDocs;
        this.guard = guard;
        this.streamProcessing = streamProcessing;
        this.indexResourceProvider = indexResourceProvider;
        this.errorText = errorText;
    }

    /// @param pipelineUuid A pipeline.
    /// @return The Lucene index doc it writes to; empty for other pipelines (`lucene_index`).
    /// @throws PipelineUnreadableException if Stroom can't load the pipeline.
    Optional<DocRef> luceneIndex(final String pipelineUuid) {
        final MergedPipeline merged = pipelineShapes.merged(pipelineUuid);
        final Set<String> elements = elementsOf(merged, "IndexingFilter");
        for (final Map.Entry<ElementProperty, Object> entry : merged.properties().entrySet()) {
            if (elements.contains(entry.getKey().element()) && "index".equals(entry.getKey().name())) {
                if (entry.getValue() instanceof final Map<?, ?> ref && ref.get("uuid") != null) {
                    return Optional.of(new DocRef(NullSafe.nonBlankStringElse(
                            NullSafe.get(ref.get("type"), String::valueOf), LuceneIndexDoc.TYPE),
                            String.valueOf(ref.get("uuid")), NullSafe.get(ref.get("name"), String::valueOf)));
                }
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /// @param pipelineUuid A pipeline.
    /// @param index        The Lucene index it writes to.
    /// @return Whether the index is the build's own and no other pipeline of the build writes to it (`_own_index`).
    boolean ownIndex(final String pipelineUuid, final DocRef index) {
        final Optional<String> build = guard.buildOf(index);
        if (build.isEmpty()) {
            return false;
        }
        for (final WriteGuard.BuildDoc doc : guard.folderContents(build.get())) {
            if (PipelineDoc.TYPE.equals(doc.type()) && !doc.uuid().equals(pipelineUuid)) {
                final Optional<DocRef> other = luceneIndex(doc.uuid());
                if (other.isPresent() && other.get().getUuid().equals(index.getUuid())) {
                    return false;
                }
            }
        }
        return true;
    }

    /// Elasticsearch's reason, when it refused every stream this pipeline's filters processed (each has an Error
    /// stream saying so, and nothing else came of it): what it was sent is not in the index (`_writes_refused`).
    ///
    /// @param pipelineUuid A pipeline.
    /// @param filters      Its processor filters.
    /// @return The reason, e.g. `security_exception`; empty otherwise, or when unsure.
    Optional<String> writesRefused(final String pipelineUuid, final List<ProcessorFilter> filters) {
        final Set<Long> processed = new TreeSet<>();
        filters.forEach(filter -> processed.addAll(ProcessorFilterWrites.selectedIds(
                ProcessorFilterWrites.expressionOf(filter))));
        if (processed.isEmpty()) {
            return Optional.empty();
        }
        String reason = null;
        for (final Long streamId : processed.stream().limit(REFUSED_STREAMS).toList()) {
            final List<Meta> errors = streamProcessing.outputs(streamId, pipelineUuid, null).stream()
                    .filter(meta -> "Error".equals(meta.getTypeName()))
                    .toList();
            if (errors.isEmpty()) {
                // Processed without an error: its documents are in the index.
                return Optional.empty();
            }
            final String text;
            try {
                text = errorText.read(errors.getFirst().getId());
            } catch (final IOException | RuntimeException e) {
                LOGGER.debug(() -> LogUtil.message("Unable to read Error stream {}", errors.getFirst().getId()), e);
                return Optional.empty();
            }
            final Optional<Matcher> hit = NullSafe.string(text).lines()
                    .map(REFUSED::matcher)
                    .filter(Matcher::find)
                    .findFirst();
            if (hit.isEmpty()) {
                return Optional.empty();
            }
            if (reason == null) {
                reason = hit.get().group();
            }
        }
        return Optional.ofNullable(reason);
    }

    /// The Dev Tools request dropping an Elasticsearch index: its data stream, as the agreed template makes it, or
    /// the index (`drop_request`).
    ///
    /// @param pipeline  The indexing pipeline, with its agreed template.
    /// @param indexName The index.
    /// @return The request.
    static String dropRequest(final PipelineDoc pipeline, final String indexName) {
        final Map<String, Object> agreed = KeptMapping.readAgreedTemplate(pipeline.getDescription()).orElse(Map.of());
        final String devTools = NullSafe.string(NullSafe.get(agreed.get("dev_tools"), String::valueOf));
        final int newline = devTools.indexOf('\n');
        final Object body;
        try {
            if (newline < 0) {
                throw new IllegalArgumentException("No body");
            }
            body = JsonUtil.readValue(devTools.substring(newline + 1), Object.class);
        } catch (final RuntimeException e) {
            return "DELETE _data_stream/" + indexName + " (a data stream), or DELETE " + indexName + " (an index)";
        }
        return holdsDataStream(body)
                ? "DELETE _data_stream/" + indexName
                : "DELETE " + indexName;
    }

    /// What an index this pipeline already wrote to needs before it indexes again, and whether the tools empty it
    /// once the user approves (`_fresh_index`). Seen in testing: the Events translation amended and its sample
    /// processed again, indexing the new Events left the superseded Events' documents beside them. The whole index
    /// is dropped and starts fresh (asked for by the user: simpler than deleting by stream, and no tracking of
    /// superseded streams, which may have aged off anyway).
    ///
    /// @param pipeline    The indexing pipeline.
    /// @param destination Its Elasticsearch destination, or null.
    /// @param lucene      Its Lucene index, or null.
    /// @param filters     Its processor filters.
    /// @return The details for the approval, and whether the build's own Lucene index is emptied first.
    FreshIndex freshIndex(final PipelineDoc pipeline,
                          final Map<String, Object> destination,
                          final DocRef lucene,
                          final List<ProcessorFilter> filters) {
        final Map<String, String> details = new LinkedHashMap<>();
        final Optional<String> refused = destination != null
                ? writesRefused(pipeline.getUuid(), filters)
                : Optional.empty();
        if (refused.isPresent()) {
            // Nothing to drop: Elasticsearch refused every run's writes (seen in testing: a 403, the index dropped
            // anyway, and the next run's auto-create refused again for an account that can't create it).
            details.put("earlier runs", "Elasticsearch refused what this pipeline sent '"
                                        + destination.get("index name") + "' before (" + refused.get() + "), so it "
                                        + "holds nothing of this pipeline's: nothing is dropped. Approve only once "
                                        + "what refused it is fixed, or it is refused again");
            return new FreshIndex(details, false);
        }
        if (destination != null) {
            final String index = String.valueOf(destination.get("index name"));
            details.put(ALREADY_INDEXED, "'" + index + "' holds what this pipeline indexed before: the cluster admin "
                                         + "drops it first, so it starts fresh: " + dropRequest(pipeline, index)
                                         + ". The agreed template stays on the cluster and makes the index again as "
                                         + "documents arrive, if the cluster connection's account may create it (the "
                                         + "index privilege auto_configure or create_index); if it can't, the admin "
                                         + "creates it again after the drop; " + EVERY);
            return new FreshIndex(details, false);
        }
        if (lucene != null && ownIndex(pipeline.getUuid(), lucene)) {
            details.put("earlier documents", "deleted first: '" + lucene.getName() + "' is this build's index, "
                                             + "emptied so it starts fresh (its shards are deleted); " + EVERY);
            return new FreshIndex(details, true);
        }
        if (lucene != null) {
            details.put(ALREADY_INDEXED, "'" + lucene.getName() + "' holds what this pipeline indexed before, and "
                                         + "isn't this build's own (or another pipeline writes to it), so it isn't "
                                         + "emptied: delete its shards (the index's Shards tab) first, or it holds "
                                         + "those events twice");
        }
        return new FreshIndex(details, false);
    }

    /// Deletes a Lucene index's shards, and with them every document in it (`_delete_index_shards`), through
    /// Stroom's own index service, node by node as its Shards tab does.
    ///
    /// @param indexUuid The index.
    /// @return How many documents the shards held.
    long deleteIndexShards(final String indexUuid) {
        final IndexResource indexResource = indexResourceProvider.get();
        final FindIndexShardCriteria only = FindIndexShardCriteria.builder()
                .pageRequest(new PageRequest(0, MAX_SHARDS))
                .indexUuidSet(new Selection<>(false, Set.of(indexUuid)))
                .build();
        final List<IndexShard> shards = NullSafe.list(NullSafe.get(indexResource.find(only), ResultPage::getValues))
                .stream()
                .filter(shard -> indexUuid.equals(shard.getIndexUuid())
                                 && shard.getStatus() != IndexShardStatus.DELETED)
                .toList();
        final Map<String, Set<Long>> nodes = new LinkedHashMap<>();
        shards.forEach(shard -> nodes.computeIfAbsent(NullSafe.string(shard.getNodeName()), node -> new TreeSet<>())
                .add(shard.getId()));
        nodes.forEach((node, ids) -> indexResource.deleteIndexShards(node, only.copy()
                .indexShardIdSet(new Selection<>(false, ids))
                .build()));
        return shards.stream().mapToLong(IndexShard::getDocumentCount).sum();
    }

    /// Sets the workspace Elasticsearch indexing pipeline's own batchSize to [#DEV_BATCH_SIZE] (small), or removes
    /// it so the template's (or Stroom's) default applies again (`development_batch`). Production pipelines are
    /// left alone.
    ///
    /// @param pipelineUuid A pipeline.
    /// @param small        True for the development batch, false for the default.
    /// @return True if it changed.
    boolean developmentBatch(final String pipelineUuid, final boolean small) {
        final MergedPipeline merged = pipelineShapes.merged(pipelineUuid);
        final Optional<String> element = elementsOf(merged, "ElasticIndexingFilter").stream().findFirst();
        if (element.isEmpty()) {
            return false;
        }
        final PipelineDoc doc = pipelineDocs.read(pipelineUuid);
        if (!guard.tags(ProcessorFilterWrites.ref(doc)).contains(WriteGuard.MANAGED)) {
            return false;
        }
        final List<PipelineProperty> added = PipelineDocs.addedProperties(doc);
        final List<PipelineProperty> own = added.stream()
                .filter(property -> PipelineDocs.isProperty(property, element.get(), "batchSize"))
                .toList();
        if (small && !own.isEmpty()
            && Objects.equals(NullSafe.get(own.getFirst().getValue(), PipelinePropertyValue::getInteger),
                DEV_BATCH_SIZE)) {
            return false;
        }
        if (!small && own.isEmpty()) {
            return false;
        }
        final List<PipelineProperty> rest = new ArrayList<>(added.stream()
                .filter(property -> !own.contains(property))
                .toList());
        if (small) {
            rest.add(new PipelineProperty(element.get(), "batchSize", new PipelinePropertyValue(DEV_BATCH_SIZE)));
        }
        pipelineDocs.write(PipelineDocs.withAddedProperties(doc, rest));
        return true;
    }

    // The ids of the pipeline's elements of a type, in order.
    private static Set<String> elementsOf(final MergedPipeline merged, final String type) {
        final Set<String> ids = new LinkedHashSet<>();
        merged.elements().forEach((id, elementType) -> {
            if (type.equals(elementType)) {
                ids.add(id);
            }
        });
        return ids;
    }

    // Whether a template's body makes a data stream, as Python's `'data_stream' in body` reads it.
    private static boolean holdsDataStream(final Object body) {
        if (body instanceof final Map<?, ?> map) {
            return map.containsKey("data_stream");
        }
        if (body instanceof final Collection<?> items) {
            return items.contains("data_stream");
        }
        return body instanceof final String text && text.contains("data_stream");
    }


    // --------------------------------------------------------------------------------


    /// Reads an Error stream's text.
    @FunctionalInterface
    interface ErrorText {

        /// @param streamId An Error stream.
        /// @return Its text, or the start of it.
        /// @throws IOException if it can't be read.
        String read(long streamId) throws IOException;
    }

    /// What an index needs before a pipeline indexes into it again.
    ///
    /// @param details What the approval says of it, by key (`already indexed`, `earlier documents`,
    ///                `earlier runs`).
    /// @param empty   True if the build's own Lucene index is emptied (its shards deleted) once approved.
    record FreshIndex(Map<String, String> details, boolean empty) {

    }
}
