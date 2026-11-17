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

import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.ToolArgs;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.QueryComponentSettings;
import stroom.docref.DocRef;
import stroom.docstore.api.DocDependencyService;
import stroom.feed.shared.FeedDoc;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexResource;
import stroom.index.shared.IndexShard;
import stroom.index.shared.LuceneIndexDoc;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.MetaExpressionUtil;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.Status;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.shared.ProcessorFilter;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Selection;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// The tidy-up at promotion (decided by the user, 2026-11-17: on by default, the user can untick it in the approval
/// or pass `tidy_up=false`): what the build made only to test with goes once the build is promoted. Each of the
/// build's test feeds has its streams marked deleted, as Stroom's Data screen deletes them (logically: data retention
/// purges them later; nothing is deleted physically), each of its Lucene test indexes has its shards deleted, as the
/// Index Shards screen deletes them, and then the test documents themselves are deleted: the test feeds, the test
/// index docs (an Elastic Index doc's index on the cluster is never touched) and the verification dashboards made on
/// them. Strictly in that order, each step only after the one before it succeeded for that item; a failure is said
/// and that item's later steps skipped, the others carry on.
///
/// Only what the build's record or workspace proves the build made is tidied: a feed or index in the build's own
/// workspace folder, made by the tools, that the record (or the tools' own name for a test feed, `-MCP-TEST`) says is
/// a test feed or test index, and that nothing outside the build reads or uses (a pipeline's processor filter, a
/// pipeline or dashboard naming it). Never a promoted document, a production feed or its streams, a feed the build
/// works from, the survey doc or the build's record.
class PromotionTidyUp {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PromotionTidyUp.class);

    /// The tool argument, and the approval form's choice, that turns the tidy-up off (true unless given false).
    static final String TIDY_UP = "tidy_up";
    /// The approval form's choice, as the form titles it.
    static final String CHOICE_TITLE = "Tidy up the build's test data after promotion (as listed in the chat)";
    /// What a verification dashboard's name ends with ([VerifyIndexTool#dashboardName]).
    static final String VERIFY = VerifyIndexTool.dashboardName("");

    // The streams of a test feed read for their size, a page at a time, and at most so many.
    private static final int STREAM_PAGE = 1_000;
    private static final int MAX_STREAMS_SIZED = 10_000;
    // The most documents read as using one test document.
    private static final int MAX_DEPENDANTS = 500;

    private final WriteGuard guard;
    private final BuildRecords records;
    private final DocumentAccess documentAccess;
    private final Provider<ProcessorFilters> processorFiltersProvider;
    private final Provider<MetaService> metaServiceProvider;
    private final Provider<IndexResource> indexResourceProvider;
    private final Provider<DocDependencyService> dependenciesProvider;

    /// @param guard                    Says which build a document is in, and deletes the test documents as the
    ///                                 user.
    /// @param records                  Says which of the build's documents are test feeds and test indexes.
    /// @param documentAccess           Reads the verification dashboards, as the user.
    /// @param processorFiltersProvider Says which pipelines' processor filters read a test feed.
    /// @param metaServiceProvider      Counts a test feed's streams and marks them deleted, as the user.
    /// @param indexResourceProvider    Counts a test index's shards and deletes them, as the user.
    /// @param dependenciesProvider     Says which documents use a test feed or test index.
    @Inject
    PromotionTidyUp(final WriteGuard guard,
                    final BuildRecords records,
                    final DocumentAccess documentAccess,
                    final Provider<ProcessorFilters> processorFiltersProvider,
                    final Provider<MetaService> metaServiceProvider,
                    final Provider<IndexResource> indexResourceProvider,
                    final Provider<DocDependencyService> dependenciesProvider) {
        this.guard = guard;
        this.records = records;
        this.documentAccess = documentAccess;
        this.processorFiltersProvider = processorFiltersProvider;
        this.metaServiceProvider = metaServiceProvider;
        this.indexResourceProvider = indexResourceProvider;
        this.dependenciesProvider = dependenciesProvider;
    }

    /// The build's test documents, never promoted: its test feeds (named as the tools name one,
    /// [OutputFeeds#TEST_FEED], or recorded as the Events output of the feeds the build works from, not a source
    /// type group's own output feed), its test indexes ([BuildTestIndexes]), and its verification dashboards that
    /// search only test indexes (a dashboard promoted searching a test index would search nothing once it is tidied
    /// up).
    ///
    /// @param build       The build.
    /// @param docs        Its documents, as the plan lists them.
    /// @param testIndexes Its test indexes, each with the production index doc's UUID it stands in for.
    /// @return Their UUIDs.
    Set<String> testDocs(final String build,
                         final List<Map<String, Object>> docs,
                         final Map<String, String> testIndexes) {
        final Set<String> out = new LinkedHashSet<>();
        for (final Map<String, Object> doc : docs) {
            final String uuid = String.valueOf(doc.get("uuid"));
            if (!FeedDoc.TYPE.equals(doc.get("type")) || doc.get("working_copy_of") != null) {
                continue;
            }
            final Map<String, Object> entry = records.entry(build, uuid);
            final boolean named = String.valueOf(doc.get("name")).toUpperCase(Locale.ROOT)
                    .endsWith(OutputFeeds.TEST_FEED);
            final boolean recorded = entry.get(BuildFeeds.OUTPUT_FOR) != null
                                     && entry.get(BuildFeeds.GROUP_OUTPUT) == null;
            if (named || recorded) {
                out.add(uuid);
            }
        }
        final Set<String> indexes = new LinkedHashSet<>();
        docs.stream()
                .map(doc -> String.valueOf(doc.get("uuid")))
                .filter(testIndexes::containsKey)
                .forEach(indexes::add);
        out.addAll(indexes);
        if (!indexes.isEmpty()) {
            docs.stream()
                    .filter(doc -> DashboardDoc.TYPE.equals(doc.get("type"))
                                   && String.valueOf(doc.get("name")).endsWith(VERIFY))
                    .filter(doc -> {
                        final Set<String> searched = searched(ref(doc));
                        return !searched.isEmpty() && indexes.containsAll(searched);
                    })
                    .forEach(doc -> out.add(String.valueOf(doc.get("uuid"))));
        }
        return out;
    }

    /// @param given The tool's `tidy_up` argument, or null.
    /// @return True unless it is false: the tidy-up is on by default.
    static boolean wanted(final Object given) {
        return given == null || ToolArgs.flag(given);
    }

    /// What the tidy-up will do, read before the promotion (while the build's pipelines are still in the build, so
    /// what reads a test feed from outside it is told apart from the build's own).
    ///
    /// @param build    The build.
    /// @param docs     Its documents, as the plan lists them.
    /// @param steps    The promotion's plan: only what it leaves in the workspace is tidied up.
    /// @param testDocs The build's test documents ([#testDocs]).
    /// @param sources  The feeds the build works from.
    /// @return The tidy-up: each item, and each test document passed over with why.
    Plan plan(final String build,
              final List<Map<String, Object>> docs,
              final List<PromotionLayout.Step> steps,
              final Set<String> testDocs,
              final List<String> sources) {
        final Set<String> kept = new HashSet<>();
        steps.stream()
                .filter(step -> PromotionLayout.KEEP.equals(step.action()))
                .forEach(step -> kept.add(String.valueOf(step.doc().get("uuid"))));
        final Set<String> sourceNames = new HashSet<>();
        sources.forEach(source -> sourceNames.add(source.toLowerCase(Locale.ROOT)));
        final List<Item> items = new ArrayList<>();
        final List<String> passedOver = new ArrayList<>();
        final List<Map<String, Object>> dashboards = new ArrayList<>();
        for (final Map<String, Object> doc : docs) {
            final String uuid = String.valueOf(doc.get("uuid"));
            if (!testDocs.contains(uuid) || !kept.contains(uuid)) {
                continue;
            }
            final DocRef ref = ref(doc);
            if (DashboardDoc.TYPE.equals(ref.getType())) {
                dashboards.add(doc);
                continue;
            }
            final Kind kind = kindOf(ref.getType());
            if (kind == null) {
                continue;
            }
            final String unproven = unproven(build, ref, kind, sourceNames);
            if (unproven != null) {
                passedOver.add(kind.said() + " '" + ref.getName() + "': " + unproven);
                continue;
            }
            try {
                items.add(switch (kind) {
                    case FEED -> feedItem(ref);
                    case LUCENE_INDEX -> new Item(kind, ref, 0, null, shards(ref.getUuid()), Set.of());
                    default -> new Item(kind, ref, 0, null, 0, Set.of());
                });
            } catch (final RuntimeException e) {
                passedOver.add(kind.said() + " '" + ref.getName() + "': it couldn't be read (" + reason(e) + ")");
            }
        }
        final Set<String> tidiedIndexes = new HashSet<>();
        items.stream()
                .filter(item -> item.kind() != Kind.FEED)
                .forEach(item -> tidiedIndexes.add(item.doc().getUuid()));
        for (final Map<String, Object> doc : dashboards) {
            final DocRef ref = ref(doc);
            final Set<String> searched = searched(ref);
            String unproven = unproven(build, ref, Kind.DASHBOARD, sourceNames);
            if (unproven == null && (searched.isEmpty() || !tidiedIndexes.containsAll(searched))) {
                unproven = "it searches an index that isn't tidied up";
            }
            if (unproven != null) {
                passedOver.add(Kind.DASHBOARD.said() + " '" + ref.getName() + "': " + unproven);
            } else {
                items.add(new Item(Kind.DASHBOARD, ref, 0, null, 0, searched));
            }
        }
        return new Plan(true, List.copyOf(items), List.copyOf(passedOver));
    }

    /// What the layout says of the tidy-up and of the test feeds left in the workspace: the approval is bound to the
    /// layout, so to what the tidy-up will do.
    ///
    /// @param plan      The promotion's plan.
    /// @param testDocs  The build's test documents ([#testDocs]).
    /// @param tidy      The tidy-up.
    /// @return The note, or empty when there is nothing to say.
    static String layoutNote(final List<PromotionLayout.Step> plan, final Set<String> testDocs, final Plan tidy) {
        final StringBuilder sb = new StringBuilder();
        if (!tidy.items().isEmpty()) {
            sb.append("\n\nTidy-up after promotion (the user can untick it in the approval):\n")
                    .append(String.join("\n", tidy.said().stream().map(line -> "- " + line).toList()));
        }
        if (tidy.on() && !tidy.passedOver().isEmpty()) {
            sb.append("\n\nNot tidied up, left in the workspace:\n")
                    .append(String.join("\n", tidy.passedOver().stream().map(line -> "- " + line).toList()));
        }
        final Set<String> tidied = new HashSet<>();
        tidy.items().forEach(item -> tidied.add(item.doc().getUuid()));
        final List<String> kept = plan.stream()
                .filter(step -> PromotionLayout.KEEP.equals(step.action())
                                && FeedDoc.TYPE.equals(step.doc().get("type")))
                .map(step -> String.valueOf(step.doc().get("uuid")))
                .filter(uuid -> testDocs.contains(uuid) && !tidied.contains(uuid))
                .map(uuid -> plan.stream()
                        .filter(step -> uuid.equals(String.valueOf(step.doc().get("uuid"))))
                        .map(step -> String.valueOf(step.doc().get("name")))
                        .findFirst()
                        .orElse(uuid))
                .toList();
        if (!kept.isEmpty()) {
            sb.append("\n\nThe build's test feed ").append(String.join(", ", kept)).append(" stays in the build's "
                                                                                         + "workspace: it holds only "
                                                                                         + "the sample's Events and "
                                                                                         + "is never promoted.");
        }
        return sb.toString();
    }

    /// Carries the tidy-up out, once the build is promoted: streams first, then shards, then documents. Never a
    /// reason for the promotion to fail: each failure is said in its item.
    ///
    /// @param build The build.
    /// @param plan  The tidy-up, as approved.
    /// @return What was done to each item: the streams marked deleted, the shards deleted, whether its document was
    /// deleted, and why not where it failed or was skipped.
    List<Map<String, Object>> apply(final String build, final Plan plan) {
        final Map<String, Map<String, Object>> results = new LinkedHashMap<>();
        final Set<String> failed = new HashSet<>();
        for (final Item item : plan.items()) {
            final Map<String, Object> result = new LinkedHashMap<>();
            result.put("document", item.doc().getType() + " '" + item.doc().getName() + "'");
            results.put(item.doc().getUuid(), result);
        }
        // 1. Each test feed's streams, marked deleted as the Data screen marks them.
        for (final Item item : plan.items()) {
            if (item.kind() == Kind.FEED) {
                step(item, results, failed, "marking its streams deleted", "streams_marked_deleted",
                        () -> (long) metaServiceProvider.get().updateStatus(new FindMetaCriteria(
                                MetaExpressionUtil.createFeedExpression(item.doc())), null, Status.DELETED));
            }
        }
        // 2. Each Lucene test index's shards, deleted as the Index Shards screen deletes them, on every node.
        for (final Item item : plan.items()) {
            if (item.kind() == Kind.LUCENE_INDEX) {
                step(item, results, failed, "deleting its shards", "shards_deleted",
                        () -> Objects.requireNonNullElse(indexResourceProvider.get().reset(item.doc().getUuid()),
                                0L));
            }
        }
        // 3. The documents: a dashboard before the index it searches, the indexes, then the feeds.
        final List<String> deleted = new ArrayList<>();
        for (final Kind kind : List.of(Kind.DASHBOARD, Kind.LUCENE_INDEX, Kind.ELASTIC_INDEX, Kind.FEED)) {
            for (final Item item : plan.items()) {
                final String uuid = item.doc().getUuid();
                if (item.kind() != kind || failed.contains(uuid)) {
                    continue;
                }
                final Map<String, Object> result = results.get(uuid);
                if (item.searched().stream().anyMatch(failed::contains)) {
                    result.put("deleted", false);
                    result.put("skipped", "left in the workspace: the test index it searches wasn't tidied up");
                    continue;
                }
                try {
                    guard.delete(item.doc());
                    if (guard.buildOf(item.doc()).isPresent()) {
                        throw new IllegalStateException("Stroom didn't delete it");
                    }
                    result.put("deleted", true);
                    deleted.add(uuid);
                } catch (final RuntimeException e) {
                    failed(result, failed, uuid, "deleting it", e);
                }
            }
        }
        if (!deleted.isEmpty()) {
            try {
                // What the build's record kept of them meant something only while they were there.
                records.forget(build, deleted);
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> LogUtil.message("The record of build {} kept the tidied documents: {}", build,
                        e.getMessage()));
            }
        }
        return List.copyOf(results.values());
    }

    // One step of an item, carried out unless an earlier one failed: its count put in the item's result.
    private static void step(final Item item,
                             final Map<String, Map<String, Object>> results,
                             final Set<String> failed,
                             final String doing,
                             final String key,
                             final Step step) {
        final String uuid = item.doc().getUuid();
        if (failed.contains(uuid)) {
            return;
        }
        try {
            results.get(uuid).put(key, step.run());
        } catch (final RuntimeException e) {
            failed(results.get(uuid), failed, uuid, doing, e);
        }
    }

    private static void failed(final Map<String, Object> result,
                               final Set<String> failed,
                               final String uuid,
                               final String doing,
                               final RuntimeException e) {
        LOGGER.warn(() -> LogUtil.message("Tidy-up of {} failed {}: {}", result.get("document"), doing,
                reason(e)), e);
        failed.add(uuid);
        result.put("deleted", false);
        result.put("failed", doing + ": " + reason(e) + " (its later steps were skipped; it is left in the "
                             + "workspace)");
    }

    // Why a test document isn't proven the build's own to tidy up, or null when it is: in the build's workspace,
    // made by the tools, not a feed the build works from, and used by nothing outside the build.
    private String unproven(final String build, final DocRef ref, final Kind kind, final Set<String> sources) {
        try {
            if (!build.equals(guard.buildOf(ref).orElse(null))) {
                return "it isn't in the build's workspace";
            }
            if (!guard.tags(ref).contains(WriteGuard.MANAGED)) {
                return "the tools didn't make it";
            }
            if (kind == Kind.FEED && sources.contains(NullSafe.string(ref.getName()).toLowerCase(Locale.ROOT))) {
                return "it is a feed the build works from";
            }
            final List<String> outside = usedOutside(build, ref, kind == Kind.FEED);
            return outside.isEmpty()
                    ? null
                    : "used outside the build, by " + String.join(", ", outside);
        } catch (final RuntimeException e) {
            return "what uses it couldn't be checked (" + reason(e) + ")";
        }
    }

    // What uses the document from outside the build: a document naming it (a pipeline, a dashboard), and for a
    // feed, a pipeline whose processor filter reads it.
    private List<String> usedOutside(final String build, final DocRef ref, final boolean feed) {
        final List<String> out = new ArrayList<>();
        final DependencyCriteria criteria = new DependencyCriteria();
        criteria.setPageRequest(new PageRequest(0, MAX_DEPENDANTS));
        criteria.setPartialName(DependencyCriteria.FIELD_DEF_TO_UUID.getFilterQualifier() + ":=" + ref.getUuid());
        final ResultPage<Dependency> page = dependenciesProvider.get().fetchDependencies(criteria, Set.of());
        for (final Dependency dependency : NullSafe.list(NullSafe.get(page, ResultPage::getValues))) {
            final DocRef from = dependency.getFrom();
            if (from == null || dependency.getTo() == null || !ref.getUuid().equals(dependency.getTo().getUuid())
                || ProcessorFilter.ENTITY_TYPE.equals(from.getType())) {
                // A processor filter is told by its pipeline, below.
                continue;
            }
            if (!build.equals(guard.buildOf(from).orElse(null))) {
                out.add(from.getType() + " '" + NullSafe.nonBlankStringElse(from.getName(), from.getUuid()) + "'");
            }
        }
        if (feed) {
            final ProcessorFilters filters = processorFiltersProvider.get();
            if (!filters.canManageProcessors()) {
                throw new IllegalStateException("the processor filters reading it need Manage Processors");
            }
            for (final Map<String, Object> pipeline : filters.pipelinesReading(ref.getName())) {
                final DocRef pipelineRef = new DocRef(PipelineDoc.TYPE, String.valueOf(pipeline.get("uuid")),
                        Objects.toString(pipeline.get("name"), null));
                if (!build.equals(guard.buildOf(pipelineRef).orElse(null))) {
                    out.add("pipeline '" + pipelineRef.getName() + "' (its processor filter reads it)");
                }
            }
        }
        return out;
    }

    // A test feed with its unlocked streams' count and size, as its Data tab finds them.
    private Item feedItem(final DocRef feed) {
        final MetaService metaService = metaServiceProvider.get();
        long count = 0;
        long bytes = 0;
        boolean sized = true;
        for (int offset = 0; offset < MAX_STREAMS_SIZED; offset += STREAM_PAGE) {
            final FindMetaCriteria criteria = new FindMetaCriteria(MetaExpressionUtil.createFeedExpression(feed));
            criteria.setPageRequest(new PageRequest(offset, STREAM_PAGE));
            final List<MetaRow> rows = NullSafe.list(NullSafe.get(metaService.findRows(criteria),
                    ResultPage::getValues));
            for (final MetaRow row : rows) {
                count++;
                final String size = row.getAttributeValue(MetaFields.FILE_SIZE.getFldName());
                if (NullSafe.isNonBlankString(size) && size.strip().chars().allMatch(Character::isDigit)) {
                    bytes += Long.parseLong(size.strip());
                } else {
                    sized = false;
                }
            }
            if (rows.size() < STREAM_PAGE) {
                return new Item(Kind.FEED, feed, count, sized
                        ? bytes
                        : null, 0, Set.of());
            }
        }
        // More than are read for their size: counted as Stroom counts them, the size not known.
        final long all = metaService.getSelectionSummary(new FindMetaCriteria(
                MetaExpressionUtil.createFeedExpression(feed))).getItemCount();
        return new Item(Kind.FEED, feed, all, null, 0, Set.of());
    }

    // The shards of a Lucene index not marked deleted, on any node.
    private int shards(final String indexUuid) {
        final ResultPage<IndexShard> shards = indexResourceProvider.get().find(FindIndexShardCriteria.builder()
                .indexUuidSet(new Selection<>(false, Set.of(indexUuid)))
                .build());
        return (int) NullSafe.list(NullSafe.get(shards, ResultPage::getValues)).stream()
                .filter(shard -> shard.getStatus() != IndexShard.IndexShardStatus.DELETED)
                .count();
    }

    // The indexes a dashboard's queries search.
    private Set<String> searched(final DocRef dashboard) {
        final Set<String> found = new LinkedHashSet<>();
        try {
            documentAccess.read(dashboard, DashboardDoc.class)
                    .map(DashboardDoc::getDashboardConfig)
                    .ifPresent(config -> {
                        for (final ComponentConfig component : NullSafe.list(config.getComponents())) {
                            if (component.getSettings() instanceof final QueryComponentSettings query
                                && query.getDataSource() != null) {
                                found.add(query.getDataSource().getUuid());
                            }
                        }
                    });
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to read dashboard {}: {}", dashboard, e.getMessage()));
        }
        return found;
    }

    private static Kind kindOf(final String type) {
        if (FeedDoc.TYPE.equals(type)) {
            return Kind.FEED;
        } else if (LuceneIndexDoc.TYPE.equals(type)) {
            return Kind.LUCENE_INDEX;
        } else if (ElasticIndexDoc.TYPE.equals(type)) {
            return Kind.ELASTIC_INDEX;
        }
        return null;
    }

    private static String reason(final RuntimeException e) {
        return NullSafe.nonBlankStringElse(e.getMessage(), e.getClass().getSimpleName());
    }

    private static DocRef ref(final Map<String, Object> doc) {
        return new DocRef(String.valueOf(doc.get("type")), String.valueOf(doc.get("uuid")),
                Objects.toString(doc.get("name"), null));
    }


    // --------------------------------------------------------------------------------


    /// The kinds of thing tidied up.
    enum Kind {
        /// A test feed: its streams marked deleted, then the feed deleted.
        FEED("test feed"),
        /// A Lucene test index: its shards deleted, then the index doc deleted.
        LUCENE_INDEX("test index"),
        /// An Elastic Index doc made for testing: the doc deleted, its index on the cluster left as it is.
        ELASTIC_INDEX("test index doc"),
        /// A verification dashboard on test indexes: deleted.
        DASHBOARD("verification dashboard");

        private final String said;

        Kind(final String said) {
            this.said = said;
        }

        /// @return The kind, as the review says it, e.g. `test feed`.
        String said() {
            return said;
        }
    }

    /// One thing tidied up.
    ///
    /// @param kind     What it is.
    /// @param doc      Its document.
    /// @param streams  A test feed's unlocked streams.
    /// @param bytes    Their total size, or null when it isn't known.
    /// @param shards   A Lucene test index's shards.
    /// @param searched The indexes a dashboard searches.
    record Item(Kind kind, DocRef doc, long streams, Long bytes, int shards, Set<String> searched) {

        /// @return What the review says will be done to it.
        String said() {
            final String named = kind.said() + " '" + doc.getName() + "'";
            return switch (kind) {
                case FEED -> named + ": its " + streams + " stream(s) (" + (bytes == null
                        ? "size not known"
                        : ModelStringUtil.formatIECByteSizeString(bytes, true)) + ") marked deleted, as Stroom's Data "
                             + "screen deletes them (data retention purges them later), then the feed deleted";
                case LUCENE_INDEX -> named + ": its " + shards + " shard(s) deleted, then the index doc deleted";
                case ELASTIC_INDEX -> named + ": deleted (its index on the cluster is left as it is)";
                case DASHBOARD -> named + ": deleted";
            };
        }
    }

    /// The tidy-up a promotion does.
    ///
    /// @param on         False when the user turned it off (`tidy_up=false`).
    /// @param items      What is tidied up, in order.
    /// @param passedOver Each test document left in the workspace, with why.
    record Plan(boolean on, List<Item> items, List<String> passedOver) {

        /// The tidy-up turned off: nothing tidied.
        static final Plan OFF = new Plan(false, List.of(), List.of());

        /// @return What the review says of each item.
        List<String> said() {
            return items.stream().map(Item::said).toList();
        }

        /// @param details The approval form's details.
        /// @return They, with the tidy-up referred to (its list is in the chat: forms are short plain text).
        Map<String, Object> inForm(final Map<String, Object> details) {
            if (!items.isEmpty()) {
                details.put("tidy-up after promotion", items.size() + " test document(s), as listed in the chat");
            }
            return details;
        }

        /// @param approval The approval.
        /// @return It, offering the tidy-up as a choice, ticked to start, when there is something to tidy up.
        Consent.Request offeredIn(final Consent.Request approval) {
            return items.isEmpty()
                    ? approval
                    : approval.withOption(TIDY_UP, CHOICE_TITLE, true);
        }

        /// What the promotion's result says of the tidy-up.
        ///
        /// @param result  The result.
        /// @param done    True if the tidy-up was carried out.
        /// @param tidied  What it did to each item ([PromotionTidyUp#apply]).
        void report(final Map<String, Object> result, final boolean done, final List<Map<String, Object>> tidied) {
            if (done) {
                result.put("tidied_up", tidied);
                result.put("hint", result.get("hint") + " Say what the tidy-up did (tidied_up), with any failure: "
                                   + "what failed is left in the workspace.");
            } else if (!items.isEmpty()) {
                result.put("tidy_up", "not done, as the user chose: the build's test data stays in the workspace");
            }
        }
    }

    // A step of an item, giving its count.
    @FunctionalInterface
    private interface Step {

        long run();
    }
}
