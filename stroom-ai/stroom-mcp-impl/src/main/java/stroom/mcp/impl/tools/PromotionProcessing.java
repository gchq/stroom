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
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

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
import java.util.function.Predicate;

/// What a build's pipelines go on processing once promoted, and the feed-wide filters made for them, disabled for the
/// user to enable, for `promote_build` to use.
class PromotionProcessing {

    private final ProcessorFilterWrites filterWrites;
    private final StreamProcessing streamProcessing;
    private final PipelineOutputs pipelineOutputs;
    private final IndexTemplateAgreements agreements;
    private final IndexingRuns indexingRuns;
    private final PipelineDocs pipelineDocs;
    private final ProductionIndexes productionIndexes;
    private final LongSupplier clockMs;
    // Every processor filter the user can see, for the pipelines reading a new version's predecessor's Events; none
    // in tests that make this themselves.
    private Provider<ProcessorFilters> processorFiltersProvider;

    /// @param filterWrites     Makes and reads processor filters.
    /// @param streamProcessing Reads streams' meta.
    /// @param pipelineOutputs  Says whether a pipeline indexes.
    /// @param agreements       Says where an Elasticsearch indexing pipeline writes.
    /// @param indexingRuns     Says which Lucene index a pipeline writes to, and how its Elasticsearch index is
    ///                         dropped.
    /// @param pipelineDocs      Reads pipelines.
    /// @param productionIndexes Says what else writes to, or names, a promoted pipeline's Elasticsearch index.
    @Inject
    PromotionProcessing(final ProcessorFilterWrites filterWrites,
                        final StreamProcessing streamProcessing,
                        final PipelineOutputs pipelineOutputs,
                        final IndexTemplateAgreements agreements,
                        final IndexingRuns indexingRuns,
                        final PipelineDocs pipelineDocs,
                        final ProductionIndexes productionIndexes) {
        this(filterWrites, streamProcessing, pipelineOutputs, agreements, indexingRuns, pipelineDocs,
                productionIndexes, System::currentTimeMillis);
    }

    /// As the injected constructor, with the clock given, for tests.
    PromotionProcessing(final ProcessorFilterWrites filterWrites,
                        final StreamProcessing streamProcessing,
                        final PipelineOutputs pipelineOutputs,
                        final IndexTemplateAgreements agreements,
                        final IndexingRuns indexingRuns,
                        final PipelineDocs pipelineDocs,
                        final ProductionIndexes productionIndexes,
                        final LongSupplier clockMs) {
        this.filterWrites = filterWrites;
        this.streamProcessing = streamProcessing;
        this.pipelineOutputs = pipelineOutputs;
        this.agreements = agreements;
        this.indexingRuns = indexingRuns;
        this.pipelineDocs = pipelineDocs;
        this.productionIndexes = productionIndexes;
        this.clockMs = clockMs;
    }

    /// The processor filters of every pipeline the user can see (set by injection; none in tests that make this
    /// themselves): a new events version's follow-on pipelines are found by them.
    ///
    /// @param processorFilters Reads every processor filter.
    @Inject
    void setProcessorFilters(final Provider<ProcessorFilters> processorFilters) {
        this.processorFiltersProvider = processorFilters;
    }

    /// The pipelines reading a predecessor's Events once a new version of it is promoted (decided by the user,
    /// 2026-11-17): every filter outside the build with a `Pipeline IS_DOC_REF <predecessor>` term (a CEF flattener's,
    /// an indexing pipeline's), each to gain a filter of the same selection reading the new version's Events. A
    /// filter selecting streams by id (a build's sample) is passed over: it selects the predecessor's own streams.
    ///
    /// @param versions The new events (or records) versions promoted, each with its predecessor.
    /// @param outside  True for a pipeline outside the build and every other build.
    /// @return Each filter to follow, in order.
    List<FollowOn> followOns(final Map<DocRef, DocRef> versions, final Predicate<DocRef> outside) {
        if (versions.isEmpty() || processorFiltersProvider == null) {
            return List.of();
        }
        final List<ProcessorFilter> all = processorFiltersProvider.get().all();
        final List<FollowOn> out = new ArrayList<>();
        versions.forEach((version, before) -> {
            for (final ProcessorFilter filter : all) {
                final String uuid = filter.getPipelineUuid();
                if (uuid == null || uuid.equals(before.getUuid()) || uuid.equals(version.getUuid())) {
                    continue;
                }
                final DocRef pipeline = new DocRef(PipelineDoc.TYPE, uuid, pipelineName(filter));
                final ExpressionOperator expression = ProcessorFilterWrites.expressionOf(filter);
                if (ProcessorFilterWrites.pipelineUuids(ProcessorFilterWrites.pipelineTerms(expression))
                            .contains(before.getUuid())
                    && ProcessorFilterWrites.selectedIds(expression).isEmpty()
                    && outside.test(pipeline)) {
                    out.add(new FollowOn(pipeline, filter, before, version));
                }
            }
        });
        return out;
    }

    /// Never two filters handed over that index the same Events into the same index. Seen in an eval (case 61): the
    /// build's indexing pipeline, made afresh rather than as a new version of the production one, was promoted with
    /// a filter reading the new events version's Events, and the production indexing pipeline gained a follow-on
    /// filter reading them too, both indexing into the same index: enabled, each event would be indexed twice. A
    /// follow-on is passed over where a pipeline the build promotes ([#promotionProcessing]) already reads the new
    /// version's Events and writes to the same index (a build's test index taken as the production index it stands
    /// in for), or where an earlier follow-on does.
    ///
    /// @param followOns   What [#followOns] found.
    /// @param processing  What [#promotionProcessing] planned for the build's pipelines.
    /// @param testIndexes The build's test indexes, each with the UUID of the production index it stands in for.
    /// @return The follow-ons to make, and what is said of each one passed over.
    Followed withoutDoubleIndexing(final List<FollowOn> followOns,
                                   final List<Entry> processing,
                                   final Map<String, String> testIndexes) {
        final Map<List<String>, String> indexedBy = new LinkedHashMap<>();
        for (final Entry entry : processing) {
            final Optional<String> index = indexKey(entry.pipeline().getUuid(), testIndexes);
            if (index.isEmpty()) {
                continue;
            }
            for (final String source : ProcessorFilterWrites.pipelineUuids(entry.extraTerms())) {
                indexedBy.putIfAbsent(List.of(index.get(), NullSafe.string(source)), entry.pipeline().getName());
            }
        }
        final List<FollowOn> kept = new ArrayList<>();
        final List<String> passedOver = new ArrayList<>();
        for (final FollowOn followOn : followOns) {
            final Optional<String> index = indexKey(followOn.pipeline().getUuid(), testIndexes);
            if (index.isEmpty()) {
                kept.add(followOn);
                continue;
            }
            final List<String> key = List.of(index.get(), followOn.version().getUuid());
            final String already = indexedBy.get(key);
            if (already != null) {
                passedOver.add("'" + followOn.pipeline().getName() + "': no new filter, as '" + already + "' "
                               + "already indexes the Events of '" + followOn.version().getName() + "' into the "
                               + "same index: a second filter would index each event twice (filter "
                               + followOn.reading().getId() + " is left as it is)");
            } else {
                indexedBy.put(key, followOn.pipeline().getName());
                kept.add(followOn);
            }
        }
        return new Followed(List.copyOf(kept), List.copyOf(passedOver));
    }

    // The index a pipeline writes to, as `lucene:<index doc UUID>` (a build's test index as the production index it
    // stands in for) or `elasticsearch:<index name>`; empty for a pipeline that indexes nothing, or can't be read.
    private Optional<String> indexKey(final String pipelineUuid, final Map<String, String> testIndexes) {
        try {
            final Optional<Map<String, Object>> elastic = agreements.elasticDestination(pipelineUuid);
            if (elastic.isPresent()) {
                return Optional.ofNullable(elastic.get().get("index name"))
                        .map(name -> "elasticsearch:" + String.valueOf(name).toLowerCase(Locale.ROOT));
            }
            return indexingRuns.luceneIndex(pipelineUuid)
                    .map(DocRef::getUuid)
                    .map(uuid -> "lucene:" + testIndexes.getOrDefault(uuid, uuid));
        } catch (final RuntimeException e) {
            // Unread: its follow-on is made, as before.
            return Optional.empty();
        }
    }

    // A filter's pipeline's name, as the filter has it or read; its UUID when neither says.
    private String pipelineName(final ProcessorFilter filter) {
        if (NullSafe.isNonBlankString(filter.getPipelineName())) {
            return filter.getPipelineName();
        }
        try {
            return NullSafe.nonBlankStringElse(NullSafe.get(pipelineDocs.read(filter.getPipelineUuid()),
                    PipelineDoc::getName), filter.getPipelineUuid());
        } catch (final RuntimeException e) {
            return filter.getPipelineUuid();
        }
    }

    /// Makes each follow-on pipeline's new filter: the same selection as the filter it follows, the Pipeline term
    /// naming the new version in place of its predecessor, created disabled for the user to enable. The filter it
    /// follows is never changed, disabled or removed: that is the admin's, once the new version's output is right
    /// (decided by the user, 2026-11-17). One made already (a promotion called again) isn't made twice.
    ///
    /// @param links    How the caller links to Stroom.
    /// @param followOns What [#followOns] found.
    /// @param made      What [#createPromotionFilters] made, whose new events filters are enabled first.
    /// @return What was made for each, in order.
    List<Map<String, Object>> createFollowOnFilters(final StroomLinks links,
                                                    final List<FollowOn> followOns,
                                                    final List<Map<String, Object>> made) {
        final List<Map<String, Object>> out = new ArrayList<>();
        for (final FollowOn followOn : followOns) {
            final ExpressionOperator expression = repointed(ProcessorFilterWrites.expressionOf(followOn.reading()),
                    followOn.predecessor().getUuid(), followOn.version());
            final Map<String, Object> result = new LinkedHashMap<>();
            final Optional<ProcessorFilter> already = filterWrites.filters(followOn.pipeline()).stream()
                    .filter(filter -> Objects.equals(ProcessorFilterWrites.expressionOf(filter), expression))
                    .findFirst();
            final ProcessorFilter filter = already.orElseGet(() -> filterWrites.createFilter(followOn.pipeline(),
                    expression, followOn.reading().getPriority(), followOn.reading().getMaxProcessingTasks(),
                    followOn.reading().getMinMetaCreateTimeMs(), false));
            result.put("filter_id", filter.getId());
            result.put("pipeline", followOn.pipeline().getName());
            result.put("reads", followOn.version().getName());
            result.put("in_place_of", followOn.predecessor().getName());
            result.put("copied_from", followOn.reading().getId());
            result.put("enabled", filter.isEnabled());
            if (already.isPresent()) {
                result.put("existing", true);
            }
            result.put("pipeline_link", links.documentTarget(followOn.pipeline()));
            if (!filter.isEnabled()) {
                // After the new version's own filter, so it reads Events the new version wrote.
                final List<String> after = made.stream()
                        .filter(entry -> followOn.version().getName().equals(entry.get("pipeline"))
                                         && !Boolean.TRUE.equals(entry.get("existing")))
                        .map(entry -> "enable-" + entry.get("filter_id"))
                        .toList();
                result.put(UserActionEntries.USER_ACTIONS, List.of(UserActionEntries.enableFilter(links,
                        filter.getId(), followOn.pipeline(), "reads the Events of '" + followOn.version().getName()
                                                             + "' in place of '" + followOn.predecessor().getName()
                                                             + "'", after)));
            }
            out.add(result);
        }
        return out;
    }

    /// @param expression A filter's expression.
    /// @param before     The UUID of the pipeline its Pipeline terms name.
    /// @param version    The pipeline they name instead.
    /// @return The expression with each such term naming the new pipeline, the rest as it was.
    static ExpressionOperator repointed(final ExpressionOperator expression,
                                        final String before,
                                        final DocRef version) {
        final List<ExpressionItem> children = new ArrayList<>();
        for (final ExpressionItem child : NullSafe.list(expression.getChildren())) {
            if (child instanceof final ExpressionOperator operator) {
                children.add(repointed(operator, before, version));
            } else if (child instanceof final ExpressionTerm term
                       && ProcessorFilters.PIPELINE.equals(term.getField())
                       && before.equals(NullSafe.get(term.getDocRef(), DocRef::getUuid))) {
                children.add(term.copy().docRef(new DocRef(PipelineDoc.TYPE, version.getUuid(), version.getName()))
                        .build());
            } else {
                children.add(child);
            }
        }
        return expression.copy().children(children).build();
    }

    /// What each promoted pipeline should go on processing: the feeds and stream types of its sample filters.
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
        return promotionProcessing(pipelines, sources, testFeeds, Map.of());
    }

    /// As [#promotionProcessing(List, List, List)], an indexing pipeline's Events source that is a working copy of a
    /// promoted pipeline named by its original: the copy only steps and processes in the build, and is deleted at
    /// promotion, while the original goes on writing the Events (an aggregated feed's group pipeline changed through
    /// working copies, its group's index reading only its Events).
    ///
    /// @param pipelines The pipelines promoted.
    /// @param sources   The build's source feeds.
    /// @param testFeeds The build's test feeds.
    /// @param originals The original of each of the build's working copies of a pipeline, by the copy's UUID.
    /// @return A filter to make for each, in order.
    List<Entry> promotionProcessing(final List<DocRef> pipelines,
                                    final List<String> sources,
                                    final List<String> testFeeds,
                                    final Map<String, String> originals) {
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
                            toOriginals(ProcessorFilterWrites.pipelineTerms(expression), originals));
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

    /// @param terms     A filter's Pipeline conditions.
    /// @param originals The original of each working copy of a pipeline, by the copy's UUID.
    /// @return The conditions, one naming a working copy naming its original instead (the name kept).
    static List<ExpressionTerm> toOriginals(final List<ExpressionTerm> terms, final Map<String, String> originals) {
        if (originals.isEmpty()) {
            return terms;
        }
        final List<ExpressionTerm> out = new ArrayList<>();
        for (final ExpressionTerm term : terms) {
            final String uuid = NullSafe.get(term.getDocRef(), DocRef::getUuid);
            final String original = uuid == null
                    ? null
                    : originals.get(uuid);
            out.add(original == null
                    ? term
                    : ProcessorFilterWrites.pipelineTerm(new DocRef(term.getDocRef().getType(), original,
                            term.getDocRef().getName())));
        }
        return out;
    }

    /// Feed-wide filters for promoted pipelines, created disabled for the user. No minimum stream creation time (asked
    /// for by the user): enabled, a filter processes the feed's data, existing data included, and what the pipeline
    /// processed in its build too: Stroom does those again for a new filter. An index starts fresh: dropped (or
    /// emptied) before its filter is enabled, it holds each event once (asked for by the user, in place of leaving out
    /// the streams processed in the build, which an index would otherwise hold twice). A pipeline with an enabled
    /// filter already selecting what arrives is given none (asked for by the user): a second would process everything
    /// twice.
    ///
    /// @param links How the caller links to Stroom.
    /// @param plan  What [#promotionProcessing] planned.
    /// @return What was made for each entry, in order.
    List<Map<String, Object>> createPromotionFilters(final StroomLinks links, final List<Entry> plan) {
        return createPromotionFilters(links, plan, Set.of());
    }

    /// As [#createPromotionFilters(StroomLinks, List)], with what the promotion moved, so a production index is never
    /// put up for dropping: a new index is dropped only while nothing but this promotion's documents writes to it or
    /// names it.
    ///
    /// @param links    How the caller links to Stroom.
    /// @param plan     What [#promotionProcessing] planned.
    /// @param promoted The UUIDs of the documents the promotion moved out of the build.
    /// @return What was made for each entry, in order.
    List<Map<String, Object>> createPromotionFilters(final StroomLinks links,
                                                     final List<Entry> plan,
                                                     final Set<String> promoted) {
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
            final BeforeEnabling before = beforePromotedIndexing(links, entry.pipeline(), promoted);
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
    // fresh, and the button for it; nothing for any other pipeline. A production index is never put up for dropping
    // (seen in a user's test: the production index of a promoted feed was dropped at the tools' prompt): a new
    // version's own index is dropped only while nothing but this promotion's documents writes to it or names it.
    private BeforeEnabling beforePromotedIndexing(final StroomLinks links,
                                                  final DocRef pipeline,
                                                  final Set<String> promoted) {
        final Optional<Map<String, Object>> destination = agreements.elasticDestination(pipeline.getUuid());
        if (destination.isPresent()) {
            final String index = String.valueOf(destination.get().get("index name"));
            final Set<String> ours = new HashSet<>(promoted);
            ours.add(pipeline.getUuid());
            final List<ProductionIndexes.Claim> others = productionIndexes.claimsBesides(ours, pipeline.getUuid(),
                    index);
            if (!others.isEmpty()) {
                return new BeforeEnabling("'" + index + "' is not dropped: it is a production index ("
                                          + String.join("; ", others.stream().map(ProductionIndexes.Claim::said)
                        .distinct().toList()) + "), never dropped for a promotion. Enabled, this filter indexes "
                                          + "into it beside what it holds: the user decides whether that is meant",
                        null);
            }
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

    /// A pipeline outside the build reading a predecessor's Events, and the filter it reads them by.
    ///
    /// @param pipeline    The follow-on pipeline.
    /// @param reading     Its filter naming the predecessor.
    /// @param predecessor The predecessor.
    /// @param version     The new version, whose Events its new filter reads.
    record FollowOn(DocRef pipeline, ProcessorFilter reading, DocRef predecessor, DocRef version) {

        /// @return What the approval says of it.
        String said() {
            return "'" + pipeline.getName() + "': a new filter, disabled, as filter " + reading.getId()
                   + " selects but reading the Events of '" + version.getName() + "' in place of '"
                   + predecessor.getName() + "' (filter " + reading.getId() + " is left as it is)";
        }
    }

    /// The follow-ons to make once those indexing Events twice are passed over ([#withoutDoubleIndexing]).
    ///
    /// @param followOns  The follow-ons to make.
    /// @param passedOver What is said of each one passed over.
    record Followed(List<FollowOn> followOns, List<String> passedOver) {

    }

    // A feed and a stream type a pipeline's sample came from.
    private record FeedAndType(String feed, String streamType) {

    }

    // What comes before enabling a promoted filter, and its button.
    private record BeforeEnabling(String what, Map<String, Object> action) {

    }
}
