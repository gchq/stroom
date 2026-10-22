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

import stroom.ai.impl.tools.StroomLinks;
import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;

/// What a build's pipelines go on processing once promoted, and the feed-wide filters made for them, disabled for
/// the user to enable. Ported from the Stroom MCP server's `tools/processing_writes.py` (`promotion_processing`,
/// `_test_feed`, `create_promotion_filters`, `_covering_filter` and `_before_promoted_indexing`), for the builds'
/// port (`promote_build`) to use.
class PromotionProcessing {

    private final ProcessorFilterWrites filterWrites;
    private final StreamProcessing streamProcessing;
    private final PipelineOutputs pipelineOutputs;
    private final IndexTemplateAgreements agreements;
    private final IndexingRuns indexingRuns;
    private final PipelineDocs pipelineDocs;
    private final LongSupplier clockMs;

    /// @param filterWrites     Makes and reads processor filters.
    /// @param streamProcessing Reads streams' meta.
    /// @param pipelineOutputs  Says whether a pipeline indexes.
    /// @param agreements       Says where an Elasticsearch indexing pipeline writes.
    /// @param indexingRuns     Says which Lucene index a pipeline writes to, and how its Elasticsearch index is
    ///                         dropped.
    /// @param pipelineDocs     Reads pipelines.
    @Inject
    PromotionProcessing(final ProcessorFilterWrites filterWrites,
                        final StreamProcessing streamProcessing,
                        final PipelineOutputs pipelineOutputs,
                        final IndexTemplateAgreements agreements,
                        final IndexingRuns indexingRuns,
                        final PipelineDocs pipelineDocs) {
        this(filterWrites, streamProcessing, pipelineOutputs, agreements, indexingRuns, pipelineDocs,
                System::currentTimeMillis);
    }

    /// As the injected constructor, with the clock given, for tests.
    PromotionProcessing(final ProcessorFilterWrites filterWrites,
                        final StreamProcessing streamProcessing,
                        final PipelineOutputs pipelineOutputs,
                        final IndexTemplateAgreements agreements,
                        final IndexingRuns indexingRuns,
                        final PipelineDocs pipelineDocs,
                        final LongSupplier clockMs) {
        this.filterWrites = filterWrites;
        this.streamProcessing = streamProcessing;
        this.pipelineOutputs = pipelineOutputs;
        this.agreements = agreements;
        this.indexingRuns = indexingRuns;
        this.pipelineDocs = pipelineDocs;
        this.clockMs = clockMs;
    }

    /// What each promoted pipeline should go on processing: the feeds and stream types of its sample filters
    /// (`promotion_processing`).
    ///
    /// A build on data already in Stroom works from its source feeds (the feed it surveyed, the one a production
    /// pipeline it copies processes, those start_build was given): a translation pipeline processes those, never the
    /// test feeds that held its copies of their records (seen in VS Code: the promoted filter selected the test
    /// feed, named like any other). A pipeline built by stepping only has no sample filters: it processes the
    /// source feeds.
    ///
    /// @param pipelines The pipelines promoted.
    /// @param sources   The build's source feeds.
    /// @param testFeeds The build's test feeds.
    /// @return A filter to make for each, in order.
    List<Entry> promotionProcessing(final List<DocRef> pipelines,
                                    final List<String> sources,
                                    final List<String> testFeeds) {
        final Set<String> tests = new HashSet<>();
        testFeeds.forEach(feed -> tests.add(feed.toLowerCase(Locale.ROOT)));
        final List<Entry> plan = new ArrayList<>();
        for (final DocRef pipeline : pipelines) {
            final Map<FeedAndType, List<ExpressionTerm>> targets = new LinkedHashMap<>();
            for (final ProcessorFilter filter : filterWrites.filters(pipeline)) {
                final ExpressionOperator expression = ProcessorFilterWrites.expressionOf(filter);
                final List<Long> ids = List.copyOf(ProcessorFilterWrites.selectedIds(expression));
                final List<Meta> metas = ids.isEmpty()
                        ? List.of()
                        : streamProcessing.metas(ids);
                for (final Meta meta : metas) {
                    targets.putIfAbsent(new FeedAndType(meta.getFeedName(), meta.getTypeName()),
                            ProcessorFilterWrites.pipelineTerms(expression));
                }
            }
            if (!pipelineOutputs.isIndexing(pipeline.getUuid())) {
                targets.entrySet().removeIf(entry -> entry.getValue().isEmpty()
                                                     && testFeed(entry.getKey().feed(), tests));
                if (targets.isEmpty()) {
                    for (final String feed : sources) {
                        // The feed's own stream type (a reference feed's is Raw Reference), not Raw Events assumed.
                        targets.putIfAbsent(new FeedAndType(feed, filterWrites.feedStreamType(feed)), List.of());
                    }
                }
            }
            final Set<List<String>> bySource = new HashSet<>();
            targets.entrySet().stream()
                    .sorted(Comparator.comparing((Map.Entry<FeedAndType, List<ExpressionTerm>> entry) ->
                                    NullSafe.string(entry.getKey().feed()))
                            .thenComparing(entry -> NullSafe.string(entry.getKey().streamType())))
                    .forEach(entry -> {
                        final String feed = entry.getKey().feed();
                        final String streamType = entry.getKey().streamType();
                        final List<ExpressionTerm> extra = entry.getValue();
                        final List<String> sourcesOf = ProcessorFilterWrites.pipelineUuids(extra).stream()
                                .map(NullSafe::string)
                                .sorted()
                                .toList();
                        if (!sourcesOf.isEmpty()) {
                            // Named by its source pipeline, not its feed: one filter whatever feeds the sample was
                            // in (a test feed among them), as the pipeline's Events are those meant.
                            final List<String> key = new ArrayList<>();
                            key.add(streamType);
                            key.addAll(sourcesOf);
                            if (bySource.add(key)) {
                                plan.add(new Entry(pipeline, null, streamType, extra));
                            }
                        } else if (NullSafe.isNonEmptyString(feed) && !testFeed(feed, tests)) {
                            plan.add(new Entry(pipeline, feed, streamType, extra));
                        }
                    });
        }
        return plan;
    }

    /// Feed-wide filters for promoted pipelines, created disabled for the user (`create_promotion_filters`). No
    /// minimum stream creation time (asked for by the user): enabled, a filter processes the feed's data, existing
    /// data included, and what the pipeline processed in its build too: Stroom does those again for a new filter.
    /// An index starts fresh: dropped (or emptied) before its filter is enabled, it holds each event once (asked for
    /// by the user, in place of leaving out the streams processed in the build, which an index would otherwise hold
    /// twice). A pipeline with an enabled filter already selecting what arrives is given none (asked for by the
    /// user): a second would process everything twice.
    ///
    /// @param links How the caller links to Stroom.
    /// @param plan  What [#promotionProcessing] planned.
    /// @return What was made for each entry, in order, as the Stroom MCP server gave it.
    List<Map<String, Object>> createPromotionFilters(final StroomLinks links, final List<Entry> plan) {
        final List<Map<String, Object>> made = new ArrayList<>();
        for (final Entry entry : plan) {
            final String link = links.documentTarget(entry.pipeline());
            final Optional<ProcessorFilter> covering = coveringFilter(entry);
            if (covering.isPresent()) {
                final Map<String, Object> existing = entryFor(covering.get().getId(), entry, true);
                existing.put("existing", true);
                existing.put("note", "no new filter: enabled filter " + covering.get().getId() + " already selects "
                                     + "what arrives");
                existing.put("pipeline_link", link);
                made.add(existing);
                continue;
            }
            final List<ExpressionItem> children = new ArrayList<>();
            if (entry.feed() != null) {
                children.add(filterWrites.feedTerm(entry.feed()));
            }
            children.add(ProcessorFilterWrites.term(MetaFields.FIELD_TYPE, entry.streamType()));
            children.addAll(entry.extraTerms());
            final ProcessorFilter created = filterWrites.createFilter(entry.pipeline(),
                    ProcessorFilterWrites.allOf(children), ProcessorFilterWrites.DEFAULT_PRIORITY,
                    ProcessorFilterWrites.MAX_FEED_FILTER_TASKS, null, false);
            final BeforeEnabling before = beforePromotedIndexing(links, entry.pipeline());
            final String selects = entry.feed() != null
                    ? "processes feed " + entry.feed() + " (" + entry.streamType() + "), existing data included"
                    : "processes " + entry.streamType() + " from its source pipeline, existing data included";
            final Map<String, Object> result = entryFor(created.getId(), entry, false);
            if (before.what() != null) {
                result.put("before_enabling", before.what());
            }
            result.put("pipeline_link", link);
            final List<Map<String, Object>> actions = new ArrayList<>();
            if (before.action() != null) {
                actions.add(before.action());
            }
            actions.add(UserActionEntries.enableFilter(links, created.getId(), entry.pipeline(), selects,
                    before.action() != null
                            ? List.of(String.valueOf(before.action().get("id")))
                            : List.of()));
            result.put(UserActionEntries.USER_ACTIONS, actions);
            made.add(result);
        }
        enableEventsPipelinesFirst(plan, made);
        return made;
    }

    // An index reading the Events of a pipeline promoted with it: the events pipeline's new filter goes first. Seen
    // in e2e: both enabled together, the indexing filter took the build's Events while they were still current,
    // then their re-translation too, and the index held the sample twice.
    @SuppressWarnings("unchecked")
    private static void enableEventsPipelinesFirst(final List<Entry> plan, final List<Map<String, Object>> made) {
        final Map<String, Map<String, Object>> fresh = new LinkedHashMap<>();
        for (int i = 0; i < plan.size(); i++) {
            if (!Boolean.TRUE.equals(made.get(i).get("existing"))) {
                fresh.put(plan.get(i).pipeline().getUuid(), made.get(i));
            }
        }
        for (int i = 0; i < plan.size(); i++) {
            final Map<String, Object> result = made.get(i);
            final Map<String, Object> first = ProcessorFilterWrites.pipelineUuids(plan.get(i).extraTerms()).stream()
                    .filter(fresh::containsKey)
                    .map(fresh::get)
                    .findFirst()
                    .orElse(null);
            if (result.get("before_enabling") != null && first != null) {
                final Object filterId = first.get("filter_id");
                result.put("before_enabling", result.get("before_enabling") + "; and only once filter " + filterId
                                              + " (pipeline '" + first.get("pipeline") + "') has processed what the "
                                              + "build processed again: enabled before that, it indexes those Events "
                                              + "too, then their replacements");
                final List<Map<String, Object>> actions =
                        (List<Map<String, Object>>) result.get(UserActionEntries.USER_ACTIONS);
                final List<String> after = (List<String>) actions.getLast()
                        .computeIfAbsent("after", key -> new ArrayList<String>());
                after.add("enable-" + filterId);
                after.add("filter " + filterId + " has processed what the build processed again");
            }
        }
    }

    // An enabled filter of the pipeline (its processor on) that selects the streams arriving from now on for this
    // entry: its feed and type, Events from its source pipeline. A filter by stream id (a build's sample) doesn't.
    private Optional<ProcessorFilter> coveringFilter(final Entry entry) {
        final List<String> sources = ProcessorFilterWrites.pipelineUuids(entry.extraTerms());
        final Meta arriving = Meta.builder()
                .id(-1)
                .feedName(NullSafe.string(entry.feed()))
                .typeName(entry.streamType())
                .pipelineUuid(sources.isEmpty()
                        ? null
                        : sources.getFirst())
                .createMs(clockMs.getAsLong())
                .build();
        for (final ProcessorFilter filter : filterWrites.filters(entry.pipeline())) {
            final Processor processor = filter.getProcessor();
            if (processor != null && !processor.isEnabled()) {
                continue;
            }
            final ExpressionOperator expression = ProcessorFilterWrites.expressionOf(filter);
            if (entry.feed() == null && !ProcessorFilterWrites.namesPipeline(expression)) {
                // Any feed's Events: only a filter naming the source pipeline is the same selection.
                continue;
            }
            if (StreamProcessing.willProcess(filter, arriving)) {
                return Optional.of(filter);
            }
        }
        return Optional.empty();
    }

    // What a promoted indexing pipeline's index needs before its filter is enabled, dropped or emptied so it starts
    // fresh, and the button for it; nothing for any other pipeline.
    private BeforeEnabling beforePromotedIndexing(final StroomLinks links, final DocRef pipeline) {
        final Optional<Map<String, Object>> destination = agreements.elasticDestination(pipeline.getUuid());
        if (destination.isPresent()) {
            final String index = String.valueOf(destination.get().get("index name"));
            final String request = IndexingRuns.dropRequest(pipelineDocs.read(pipeline.getUuid()), index);
            return new BeforeEnabling("the cluster admin drops '" + index + "' first, so it starts fresh with what "
                                      + "this pipeline indexes, the Events indexed in the build among them: "
                                      + request + ". The agreed template stays on the cluster and makes the index "
                                      + "again as documents arrive",
                    UserActionEntries.esRequest("drop-" + index, request, "Drop '" + index + "' so it starts "
                                                                          + "fresh"));
        }
        final Optional<DocRef> lucene = indexingRuns.luceneIndex(pipeline.getUuid());
        if (lucene.isPresent()) {
            return new BeforeEnabling("delete the shards of index '" + lucene.get().getName() + "' first (its Shards "
                                      + "tab), so it starts fresh with what this pipeline indexes, the Events "
                                      + "indexed in the build among them",
                    UserActionEntries.openDocument(links, ProcessingApprovals.withIndexType(lucene.get()),
                            "delete its shards on the Shards tab, so it starts fresh"));
        }
        return new BeforeEnabling(null, null);
    }

    // A test feed: one of the build's, or one the tools named.
    private static boolean testFeed(final String feed, final Set<String> tests) {
        return NullSafe.isNonEmptyString(feed)
               && (tests.contains(feed.toLowerCase(Locale.ROOT)) || feed.endsWith(OutputFeeds.TEST_FEED));
    }

    private static Map<String, Object> entryFor(final Integer filterId, final Entry entry, final boolean enabled) {
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("filter_id", filterId);
        result.put("pipeline", entry.pipeline().getName());
        result.put("feed", entry.feed());
        result.put("stream_type", entry.streamType());
        result.put("enabled", enabled);
        return result;
    }


    // --------------------------------------------------------------------------------


    /// A filter to make for a promoted pipeline.
    ///
    /// @param pipeline   The pipeline.
    /// @param feed       The feed it processes, or null for Events named by their source pipeline.
    /// @param streamType The stream type it processes.
    /// @param extraTerms Its other conditions (Pipeline IS_DOC_REF the source events pipeline).
    record Entry(DocRef pipeline, String feed, String streamType, List<ExpressionTerm> extraTerms) {

        /// @param pipeline   The pipeline.
        /// @param feed       The feed it processes, or null.
        /// @param streamType The stream type it processes.
        /// @param extraTerms Its other conditions.
        Entry {
            Objects.requireNonNull(pipeline, "pipeline");
            extraTerms = List.copyOf(NullSafe.list(extraTerms));
        }
    }

    // A feed and a stream type a pipeline's sample came from.
    private record FeedAndType(String feed, String streamType) {

    }

    // What comes before enabling a promoted filter, and its button.
    private record BeforeEnabling(String what, Map<String, Object> action) {

    }
}
