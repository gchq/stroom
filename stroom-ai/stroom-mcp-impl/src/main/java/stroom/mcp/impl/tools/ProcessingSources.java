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

import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.validation.PythonText;
import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.Status;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/// Which streams a pipeline in a build may process: an indexing pipeline only the Events of its source events
/// pipeline (never Events Stroom has since superseded), and a translation pipeline only the build's own feeds, or
/// the existing feeds the build works from, its Events then written to the build's test feed. Ported from the Stroom
/// MCP server's `tools/processing_writes.py` (`_events_source`, `_refuse_superseded` and `_build_feeds_only`).
class ProcessingSources {

    /// The consent action for indexing Events from a pipeline the tools did not build.
    static final String OTHER_SOURCE_ACTION = "index_events_from_other_pipeline";
    // The most streams in the place of a superseded Events stream looked at.
    private static final int SUPERSEDING = 20;

    private final PipelineOutputs pipelineOutputs;
    private final StreamProcessing streamProcessing;
    private final StreamReader streamReader;
    private final PipelineDocs pipelineDocs;
    private final WriteGuard guard;
    private final Consent consent;
    private final BuildPlan plan;
    private final FeedNames feedNames;
    private final OutputFeeds outputFeeds;

    /// @param pipelineOutputs  Says whether a pipeline indexes.
    /// @param streamProcessing Reads streams' meta as the user.
    /// @param streamReader     Finds the streams in a superseded one's place.
    /// @param pipelineDocs     Reads the source pipeline.
    /// @param guard            Says whether the tools built a pipeline.
    /// @param consent          Asks the user to confirm another source.
    /// @param plan             Says which build a document is in, and which feeds it works from.
    /// @param feedNames        Finds feeds by name, in any case.
    /// @param outputFeeds      Sets a build's test feed on its pipeline.
    @Inject
    ProcessingSources(final PipelineOutputs pipelineOutputs,
                      final StreamProcessing streamProcessing,
                      final StreamReader streamReader,
                      final PipelineDocs pipelineDocs,
                      final WriteGuard guard,
                      final Consent consent,
                      final BuildPlan plan,
                      final FeedNames feedNames,
                      final OutputFeeds outputFeeds) {
        this.pipelineOutputs = pipelineOutputs;
        this.streamProcessing = streamProcessing;
        this.streamReader = streamReader;
        this.pipelineDocs = pipelineDocs;
        this.guard = guard;
        this.consent = consent;
        this.plan = plan;
        this.feedNames = feedNames;
        this.outputFeeds = outputFeeds;
    }

    /// For an indexing pipeline reading Events: the events pipeline they must come from, checked
    /// (`_events_source`).
    ///
    /// Indexing filters over Events always carry `Pipeline IS_DOC_REF <source>`, so they never pick up Events
    /// streams from anywhere else. The source must be an events pipeline the tools built, and every given stream
    /// must be an Events stream it produced. Raw input (a discovery index) has no source pipeline.
    ///
    /// @param call           The call, whose id tells its forms apart.
    /// @param context        The call's context.
    /// @param pipeline       The indexing (or other) pipeline.
    /// @param sourceUuid     The source events pipeline the call named, or null.
    /// @param streamIds      The streams to process, or none for a feed's.
    /// @param streamType     The stream type a feed's filter selects.
    /// @param confirmationId The id of the user's confirmation of a source the tools didn't build, or null.
    /// @return The source, none, or the confirmation round to return.
    /// @throws ToolRefusal if the streams can't be tied to the source.
    EventsSource eventsSource(final ToolCall call,
                              final ToolCallContext context,
                              final PipelineDoc pipeline,
                              final String sourceUuid,
                              final List<Long> streamIds,
                              final String streamType,
                              final String confirmationId) {
        if (!pipelineOutputs.isIndexing(pipeline.getUuid())) {
            if (NullSafe.isNonBlankString(sourceUuid)) {
                throw new ToolRefusal("source_pipeline_uuid is only for indexing pipelines");
            }
            return EventsSource.NONE;
        }
        List<Meta> metas = List.of();
        if (!streamIds.isEmpty()) {
            metas = streamProcessing.metas(streamIds);
            final Set<String> types = new LinkedHashSet<>();
            metas.forEach(meta -> types.add(meta.getTypeName()));
            if (!types.contains(ProcessorFilters.EVENTS)) {
                if (NullSafe.isNonBlankString(sourceUuid)) {
                    throw new ToolRefusal("These streams are not Events streams; source_pipeline_uuid does not "
                                          + "apply");
                }
                return EventsSource.NONE;
            }
            if (!types.equals(Set.of(ProcessorFilters.EVENTS))) {
                final Set<String> sorted = new TreeSet<>();
                types.forEach(type -> sorted.add(NullSafe.nonBlankStringElse(type, "?")));
                throw new ToolRefusal("Mixed stream types " + PythonText.list(sorted) + ": process Events streams "
                                      + "on their own");
            }
        } else if (!ProcessorFilters.EVENTS.equals(streamType)) {
            if (NullSafe.isNonBlankString(sourceUuid)) {
                throw new ToolRefusal("source_pipeline_uuid only applies when the feed scope's stream_type is "
                                      + "Events");
            }
            return EventsSource.NONE;
        }
        if (NullSafe.isBlankString(sourceUuid)) {
            throw new ToolRefusal("Indexing Events needs source_pipeline_uuid: the events pipeline this server built "
                                  + "that produced them. The filter only selects Events from that pipeline.");
        }
        final PipelineDoc source = pipelineDocs.read(sourceUuid);
        final DocRef sourceRef = ProcessorFilterWrites.ref(source);
        try {
            guard.checkBuilt(context.session(), sourceRef);
        } catch (final ToolRefusal e) {
            // Not one of ours: allowed only when the user confirms this exact source pipeline.
            final Map<String, Object> details = new LinkedHashMap<>();
            details.put("source pipeline", source.getName());
            details.put("uuid", sourceUuid);
            details.put("indexing pipeline", pipeline.getName());
            final Optional<Map<String, Object>> gate = consent.require(call, context, Consent.Request.confirmation(
                    OTHER_SOURCE_ACTION, "Index Events from pipeline '" + source.getName() + "', which this server "
                                         + "did not build? Only its Events are selected (Pipeline condition).",
                    details, confirmationId).keepingId());
            if (gate.isPresent()) {
                return new EventsSource(null, gate.get());
            }
        }
        if (pipelineOutputs.isIndexing(sourceUuid)) {
            throw new ToolRefusal("'" + source.getName() + "' is an indexing pipeline, not the events pipeline that "
                                  + "produced the Events");
        }
        refuseSuperseded(metas);
        final List<Meta> foreign = metas.stream()
                .filter(meta -> !Objects.equals(meta.getPipelineUuid(), sourceUuid))
                .toList();
        final Set<Long> found = new TreeSet<>();
        metas.forEach(meta -> found.add(meta.getId()));
        final List<Long> missing = new ArrayList<>(new TreeSet<>(streamIds.stream()
                .filter(id -> !found.contains(id))
                .toList()));
        if (!foreign.isEmpty() || !missing.isEmpty()) {
            throw new ToolRefusal(notProduced(source, foreign, missing));
        }
        return new EventsSource(sourceRef, null);
    }

    /// Events streams Stroom marked deleted when their raw stream was processed again (an events pipeline updated
    /// and reprocessed), named with the Events now in their place (`_refuse_superseded`). Seen in production:
    /// after the parent events pipeline was updated, indexing was given the earlier Events streams' ids, and the
    /// filter selected streams Stroom no longer processes.
    ///
    /// @param metas Events streams.
    /// @throws ToolRefusal if any was superseded.
    void refuseSuperseded(final List<Meta> metas) {
        final List<Meta> gone = metas.stream()
                .filter(meta -> meta.getStatus() == Status.DELETED)
                .toList();
        if (gone.isEmpty()) {
            return;
        }
        final Map<Long, List<Long>> current = new TreeMap<>();
        for (final Meta meta : gone) {
            final Long parent = meta.getParentMetaId();
            if (parent == null) {
                continue;
            }
            final ExpressionOperator expression = ExpressionOperator.builder().op(Op.AND)
                    .addIdTerm(MetaFields.PARENT_ID, Condition.EQUALS, parent)
                    .addTextTerm(MetaFields.TYPE, Condition.EQUALS, NullSafe.nonBlankStringElse(meta.getTypeName(),
                            ProcessorFilters.EVENTS))
                    .build();
            current.put(meta.getId(), NullSafe.list(NullSafe.get(streamReader.find(expression, SUPERSEDING, true),
                            ResultPage::getValues)).stream()
                    .map(MetaRow::getMeta)
                    .filter(Objects::nonNull)
                    .filter(row -> row.getStatus() != Status.DELETED
                                   && Objects.equals(row.getPipelineUuid(), meta.getPipelineUuid()))
                    .map(Meta::getId)
                    .sorted()
                    .toList());
        }
        final List<Long> ids = gone.stream().map(Meta::getId).sorted().toList();
        final String told = String.join("; ", ids.stream()
                .map(id -> id + " -> " + (NullSafe.isEmptyCollection(current.get(id))
                        ? "none yet"
                        : PythonText.numbers(current.get(id))))
                .toList());
        throw new ToolRefusal("Events stream(s) " + PythonText.numbers(ids) + " were superseded: their raw streams "
                              + "were processed again (the events pipeline was updated), and Stroom marked them "
                              + "deleted. The Events now in their place: " + told + ". Use those ids; 'none yet' "
                              + "means processing hasn't finished (wait_for_processing on the events pipeline "
                              + "first).");
    }

    /// Translation pipelines write their Events into the input's feed, so they only process the build's feeds, or
    /// the existing feeds the build works from: their Events then go to the build's test feed ([OutputFeeds]), and
    /// the output feed is returned (`_build_feeds_only`). Indexing pipelines write to an index, not to a feed, so
    /// they are not limited this way.
    ///
    /// @param pipeline  The pipeline.
    /// @param streamIds The streams to process, or none.
    /// @param feed      The feed to process, or null.
    /// @return The build's test feed the Events go to; empty when they go into the input's feed.
    /// @throws ToolRefusal if a feed is neither the build's nor one it works from.
    Optional<OutputFeeds.OutputFeed> buildFeedsOnly(final DocRef pipeline,
                                                    final List<Long> streamIds,
                                                    final String feed) {
        if (pipelineOutputs.isIndexing(pipeline.getUuid())) {
            return Optional.empty();
        }
        final String build = plan.buildOf(pipeline).orElse(null);
        final Set<String> feeds = new TreeSet<>();
        if (feed != null) {
            feeds.add(feed);
        } else {
            streamProcessing.metas(streamIds).stream()
                    .map(Meta::getFeedName)
                    .filter(NullSafe::isNonEmptyString)
                    .forEach(feeds::add);
        }
        final List<String> outside = new ArrayList<>();
        for (final String name : feeds) {
            final Optional<DocRef> ref = feedNames.exact(name);
            if (build == null || ref.isEmpty() || !build.equals(plan.buildOf(ref.get()).orElse(null))) {
                // Stream data names a feed as Stroom first spelt it: the build's feed may be the same name in
                // another case.
                final String uuid = ref.map(DocRef::getUuid).orElse(null);
                final boolean inBuild = build != null && feedNames.feedsNamed(name).stream()
                        .filter(twin -> !Objects.equals(twin.getUuid(), uuid))
                        .anyMatch(twin -> build.equals(plan.buildOf(twin).orElse(null)));
                if (!inBuild) {
                    outside.add(name);
                }
            }
        }
        if (!outside.isEmpty() && build != null) {
            final List<String> sources = plan.worksFrom(build);
            final Set<String> lowerSources = lower(sources);
            if (!sources.isEmpty() && lowerSources.containsAll(lower(outside))) {
                // Asked for by the user: an existing feed's streams are processed where they are, their Events
                // written to a test feed in the build. Seen before (Haiku, asked to onboard an existing feed):
                // refused here and told to copy the records into a test feed, it made one; with sample files in
                // the workspace, those were what it would have uploaded.
                return outputFeeds.outputFeed(pipeline, sources, build);
            }
        }
        if (!outside.isEmpty()) {
            throw new ToolRefusal("Feed(s) " + PythonText.list(outside) + " are not in this build. A translation "
                                  + "pipeline writes its Events into the input's feed, so it only processes the "
                                  + "build's own feeds, or the existing feeds the build works from: start_build "
                                  + "build=<this build> feeds=[the feed] makes it work from that feed, and its "
                                  + "Events then go to a test feed in the build. Or step the records where they are "
                                  + "(step_records). Create no feed and upload nothing for them.");
        }
        return Optional.empty();
    }

    // Why the streams can't be indexed from the source: produced by another pipeline, or not found.
    private String notProduced(final PipelineDoc source, final List<Meta> foreign, final List<Long> missing) {
        final Set<String> producers = new TreeSet<>();
        foreign.stream()
                .map(Meta::getPipelineUuid)
                .filter(NullSafe::isNonBlankString)
                .forEach(producers::add);
        final List<String> names = new ArrayList<>();
        for (final String uuid : producers.stream().limit(3).toList()) {
            try {
                names.add("'" + pipelineDocs.read(uuid).getName() + "' (" + uuid + ")");
            } catch (final RuntimeException e) {
                // The name is extra: the refusal stands without it.
                names.add(uuid);
            }
        }
        final List<Long> ids = new ArrayList<>(foreign.stream().map(Meta::getId).sorted().toList());
        ids.addAll(missing);
        return "Stream(s) " + PythonText.numbers(ids) + " were not produced by '" + source.getName() + "'"
               + (missing.isEmpty()
                ? ""
                : " (" + PythonText.numbers(missing) + " not found)")
               + (names.isEmpty()
                ? ""
                : "; they were produced by " + String.join(", ", names) + ": if the events pipeline was updated as "
                  + "a new version, give that version as source_pipeline_uuid")
               + ": only the source pipeline's Events can be indexed here";
    }

    private static Set<String> lower(final List<String> names) {
        final Set<String> out = new LinkedHashSet<>();
        names.forEach(name -> out.add(name.toLowerCase(Locale.ROOT)));
        return out;
    }


    // --------------------------------------------------------------------------------


    /// The events pipeline an indexing pipeline's Events must come from.
    ///
    /// @param pipeline The source pipeline, or null for none (not indexing Events) or while the user is asked.
    /// @param round    The confirmation round to return as the tool's result, or null.
    record EventsSource(DocRef pipeline, Map<String, Object> round) {

        /// No source: the pipeline doesn't index Events.
        static final EventsSource NONE = new EventsSource(null, null);
    }
}
