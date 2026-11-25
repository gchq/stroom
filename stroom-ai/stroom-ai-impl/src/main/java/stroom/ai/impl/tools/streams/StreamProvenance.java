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

package stroom.ai.impl.tools.streams;

import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.documents.KeptMapping;
import stroom.ai.impl.tools.processing.MergedPipeline;
import stroom.ai.impl.tools.processing.XsltStages;
import stroom.ai.impl.tools.processing.XsltStages.Stage;
import stroom.ai.impl.tools.processing.XsltStages.StagedXslt;
import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaExpressionUtil;
import stroom.meta.shared.MetaRow;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/// Where streams came from (decided with the user, 2026-11-25: provenance by stream, never by feed name or index
/// alone): for each stream, its feed, the pipeline that produced it (its meta's pipeline, and the processor), and
/// its parent stream; and for each producing pipeline, read as Stroom runs it, its XSLTs by stage ([XsltStages]),
/// the mapping kept with its translation XSLT, the source notes doc that XSLT links to ([KeptMapping#SOURCE_NOTES]),
/// and the index plan kept with its indexing XSLT, if it has one. An indexed event's provenance is that of its
/// `StreamId`'s stream.
///
/// The streams are looked up in batches, as the user: a stream of a feed the user may not view (or one that doesn't
/// exist) is counted, never read or named; a producing pipeline the user can't read (or whose template they
/// can't) is named by its UUID only, with nothing of its documents.
public class StreamProvenance {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StreamProvenance.class);

    /// The most streams looked up in one search.
    static final int BATCH = 500;

    private final StreamReader streamReader;
    private final DocumentAccess documentAccess;
    private final XsltStages xsltStages;

    /// @param streamReader   Finds the streams' meta, as the user.
    /// @param documentAccess Reads the pipelines and XSLTs, as the user.
    /// @param xsltStages     Tells each XSLT's stage.
    @Inject
    public StreamProvenance(final StreamReader streamReader,
                            final DocumentAccess documentAccess,
                            final XsltStages xsltStages) {
        this.streamReader = streamReader;
        this.documentAccess = documentAccess;
        this.xsltStages = xsltStages;
    }

    /// Where each stream came from.
    ///
    /// @param streamIds The streams, e.g. the distinct `StreamId`s of the events a read found; duplicates and nulls
    ///                  are ignored.
    /// @return Each stream the user may see, with its producing pipeline's facts (each pipeline read once), and how
    /// many were not seen.
    public Provenance of(final Collection<Long> streamIds) {
        final Set<Long> wanted = new LinkedHashSet<>();
        NullSafe.collection(streamIds).stream().filter(Objects::nonNull).forEach(wanted::add);
        final Map<Long, StreamSource> streams = new LinkedHashMap<>();
        final List<Long> batch = new ArrayList<>();
        for (final Long id : wanted) {
            batch.add(id);
            if (batch.size() == BATCH) {
                find(batch, streams);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            find(batch, streams);
        }
        final Map<String, PipelineFacts> pipelines = new LinkedHashMap<>();
        for (final StreamSource stream : streams.values()) {
            final String uuid = NullSafe.get(stream.pipeline(), DocRef::getUuid);
            if (NullSafe.isNonBlankString(uuid) && !pipelines.containsKey(uuid)) {
                pipelines.put(uuid, facts(stream.pipeline()));
            }
        }
        // In the order asked for.
        final Map<Long, StreamSource> ordered = new LinkedHashMap<>();
        wanted.forEach(id -> NullSafe.consume(streams.get(id), found -> ordered.put(id, found)));
        return new Provenance(ordered, wanted.size() - ordered.size(), pipelines);
    }

    /// What a pipeline is, for the streams it produced: its XSLTs by stage, its translation's kept mapping and
    /// source notes link, and its indexing XSLT's plan.
    ///
    /// @param pipeline A pipeline.
    /// @return Its facts; only its reference where the user can't read it, or its template.
    public PipelineFacts facts(final DocRef pipeline) {
        final Optional<MergedPipeline> merged;
        try {
            merged = MergedPipeline.load(documentAccess, pipeline.getUuid());
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Pipeline {} not read: {}", pipeline.getUuid(), e.getMessage()), e);
            return PipelineFacts.unread(pipeline);
        }
        if (merged.isEmpty()) {
            return PipelineFacts.unread(pipeline);
        }
        final List<StagedXslt> staged = xsltStages.classify(merged.get());
        final Optional<XsltDoc> translation = XsltStages.first(staged, Stage.TRANSLATION)
                .map(StagedXslt::xslt)
                .filter(Objects::nonNull)
                .flatMap(xslt -> documentAccess.read(xslt, XsltDoc.class));
        final Map<String, Object> mapping = translation
                .flatMap(doc -> KeptMapping.readMapping(doc.getDescription()))
                .filter(kept -> KeptMapping.TRANSLATION.equals(kept.kind()))
                .map(KeptMapping.Kept::payload)
                .orElse(null);
        final DocRef notes = translation
                .flatMap(doc -> KeptMapping.readSourceNotes(doc.getDescription()))
                .orElse(null);
        Map<String, Object> plan = null;
        for (final StagedXslt xslt : XsltStages.of(staged, Stage.INDEXING)) {
            plan = NullSafe.get(xslt.xslt(), ref -> documentAccess.read(ref, XsltDoc.class)
                    .flatMap(doc -> KeptMapping.readMapping(doc.getDescription()))
                    .filter(kept -> "index".equals(kept.kind()))
                    .map(KeptMapping.Kept::payload)
                    .orElse(null));
            if (plan != null) {
                break;
            }
        }
        final PipelineDoc doc = merged.get().doc();
        return new PipelineFacts(new DocRef(PipelineDoc.TYPE, pipeline.getUuid(), doc != null
                ? doc.getName()
                : pipeline.getName()), true, staged, mapping, notes, plan);
    }

    // The streams of one batch the user may see, by id.
    private void find(final List<Long> ids, final Map<Long, StreamSource> into) {
        final ResultPage<MetaRow> page;
        try {
            page = streamReader.find(MetaExpressionUtil.createDataIdSetExpression(new LinkedHashSet<>(ids)),
                    ids.size(), false);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Streams {} not looked up: {}", ids, e.getMessage()), e);
            return;
        }
        for (final MetaRow row : NullSafe.list(NullSafe.get(page, ResultPage::getValues))) {
            final Meta meta = row.getMeta();
            if (meta == null || !ids.contains(meta.getId())) {
                continue;
            }
            final DocRef pipeline = row.getPipeline() != null
                    ? row.getPipeline()
                    : NullSafe.isNonBlankString(meta.getPipelineUuid())
                            ? new DocRef(PipelineDoc.TYPE, meta.getPipelineUuid())
                            : null;
            into.put(meta.getId(), new StreamSource(meta.getId(), meta.getFeedName(), meta.getTypeName(), pipeline,
                    meta.getProcessorUuid(), meta.getParentMetaId()));
        }
    }


    // --------------------------------------------------------------------------------


    /// Where streams came from.
    ///
    /// @param streams   Each stream the user may see, by id, in the order asked for.
    /// @param notSeen   How many of the streams asked for the user may not see (a feed they may not view), or
    ///                  don't exist: counted, never named.
    /// @param pipelines Each producing pipeline's facts, by its UUID.
    public record Provenance(Map<Long, StreamSource> streams, int notSeen, Map<String, PipelineFacts> pipelines) {

        /// Copies the collections, in order.
        public Provenance {
            streams = Collections.unmodifiableMap(new LinkedHashMap<>(NullSafe.map(streams)));
            pipelines = Collections.unmodifiableMap(new LinkedHashMap<>(NullSafe.map(pipelines)));
        }

        /// @param streamId A stream.
        /// @return Its producing pipeline's facts, or empty if the stream wasn't seen or has no pipeline.
        public Optional<PipelineFacts> pipelineOf(final long streamId) {
            return Optional.ofNullable(streams.get(streamId))
                    .map(StreamSource::pipeline)
                    .map(DocRef::getUuid)
                    .map(pipelines::get);
        }

        /// @return The streams by feed, each feed in the order first seen.
        public Map<String, List<Long>> byFeed() {
            final Map<String, List<Long>> out = new LinkedHashMap<>();
            streams.values().forEach(stream -> out.computeIfAbsent(NullSafe.string(stream.feed()),
                    k -> new ArrayList<>()).add(stream.streamId()));
            return out;
        }

        /// @return The streams by producing pipeline's UUID ("" for none), each in the order first seen.
        public Map<String, List<Long>> byPipeline() {
            final Map<String, List<Long>> out = new LinkedHashMap<>();
            streams.values().forEach(stream -> out.computeIfAbsent(NullSafe.string(NullSafe.get(stream.pipeline(),
                    DocRef::getUuid)), k -> new ArrayList<>()).add(stream.streamId()));
            return out;
        }
    }


    // --------------------------------------------------------------------------------


    /// One stream's source.
    ///
    /// @param streamId      The stream.
    /// @param feed          Its feed's name.
    /// @param type          Its stream type, e.g. `Events`.
    /// @param pipeline      The pipeline that produced it, or null (a received stream).
    /// @param processorUuid The processor that produced it, or null.
    /// @param parentId      The stream it was produced from, or null.
    public record StreamSource(long streamId, String feed, String type, DocRef pipeline, String processorUuid,
                               Long parentId) {

    }


    // --------------------------------------------------------------------------------


    /// A producing pipeline, read as Stroom runs it.
    ///
    /// @param pipeline    The pipeline (named where it can be read).
    /// @param readable    False if the user can't read it (or its template): nothing else is given.
    /// @param xslts       Its XSLTs in processing order, each with its stage.
    /// @param mapping     The mapping kept with its translation XSLT (`schema_version`, `mapping`), or null.
    /// @param sourceNotes The source notes doc its translation XSLT links to, or null.
    /// @param indexPlan   The index plan kept with its indexing XSLT, or null.
    public record PipelineFacts(DocRef pipeline,
                                boolean readable,
                                List<StagedXslt> xslts,
                                Map<String, Object> mapping,
                                DocRef sourceNotes,
                                Map<String, Object> indexPlan) {

        /// Copies the XSLTs.
        public PipelineFacts {
            xslts = List.copyOf(NullSafe.list(xslts));
        }

        /// @param pipeline A pipeline the user can't read.
        /// @return Its reference alone.
        static PipelineFacts unread(final DocRef pipeline) {
            return new PipelineFacts(new DocRef(PipelineDoc.TYPE, pipeline.getUuid()), false, List.of(), null, null,
                    null);
        }

        /// @param stage A stage.
        /// @return Its XSLTs of that stage, in processing order.
        public List<StagedXslt> of(final Stage stage) {
            return XsltStages.of(xslts, stage);
        }
    }
}
