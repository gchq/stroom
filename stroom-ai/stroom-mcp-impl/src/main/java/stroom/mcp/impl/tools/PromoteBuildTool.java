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

import stroom.ai.impl.tools.AbstractBuiltInTool;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.generation.CodeDiff;
import stroom.ai.impl.tools.generation.ModuleChecks;
import stroom.ai.impl.tools.generation.SourceTypeModule;
import stroom.ai.impl.tools.sample.SourceTypeRegistry;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.ai.impl.tools.validation.XsltVersionHistory;
import stroom.ai.shared.AiToolCategory;
import stroom.dashboard.shared.DashboardDoc;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.shared.ExplorerConstants;
import stroom.feed.shared.FeedDoc;
import stroom.index.shared.LuceneIndexDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.processor.shared.ProcessorFilter;
import stroom.query.api.ExpressionTerm;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/// `promote_build` - promotes a build out of the workspace once the user has seen where everything goes and approved
/// it. New documents are moved to their destination folders, working copies written back over their originals after a
/// backup, and feed-wide processor filters made, disabled, for the promoted pipelines: all through Stroom's explorer
/// and stores as the user, once they approve the layout in a short form bound to it.
class PromoteBuildTool extends AbstractBuiltInTool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PromoteBuildTool.class);

    static final String NAME = "promote_build";
    static final String BUILD_ARG = "build";
    static final String DESTINATIONS_ARG = "destinations";
    static final String REVIEWED_ARG = "reviewed";
    /// What a working copy's write-back takes from it.
    static final List<String> WRITTEN_BACK = List.of("data", "converterType", "documentation", "description");
    // A discarded step's target: a working copy the same as its original, discarded rather than written back.
    // A promoted pipeline and the code it runs: never written back from a working copy.
    private static final Set<String> CODE_TYPES = Set.of(PipelineDoc.TYPE, XsltDoc.TYPE, TextConverterDoc.TYPE);
    private static final String UNCHANGED_COPY = "unchanged working copy, discarded";

    private static final String KEEP = "keep";
    // What becomes of a build's test feed.
    private static final String TEST_FEED_KEPT = "left in the workspace: the build's test feed, never promoted";
    private static final String TEST_INDEX_KEPT = "left in the workspace: the build's test index, never promoted";
    static final String OVERWRITE_ORIGINAL = "Overwrite production with the working copy";
    static final String KEEP_ORIGINAL = "Keep production as it is";
    // In a working copy's record entry: what the user chose for an original changed since, and at which version.
    private static final String ORIGINAL_CHOICE = "original_choice";
    // The most of a diff a question quotes: the form's message must stay short enough to show.
    private static final int MAX_DIFF_CHARS = 1_000;
    // The most lines of each side of the difference a question quotes.
    private static final int MAX_DIFF_LINES = 6;
    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneOffset.UTC);
    private static final List<String> INDEX_TYPES = List.of(ElasticIndexDoc.TYPE, LuceneIndexDoc.TYPE);
    private static final List<String> INDEXING_STAGES = List.of("indexing", "discovery");

    private final BuildFacts facts;
    private final PromotionLayout layout;
    private final DocumentPlaces places;
    private final WriteGuard guard;
    private final BuildRecords buildRecords;
    private final Consent consent;
    private final PromotionProcessing promotionProcessing;
    private final ProcessorFilterWrites filterWrites;
    private final OutputFeeds outputFeeds;
    private final DocumentWrites writes;
    private final DocumentAccess documentAccess;
    private final XsltHistory history;
    private final PendingDocumentation pendingDocs;
    // An aggregated feed's modules and the reference data its pipelines load; none in tests that make the tool
    // themselves.
    private Provider<SourceTypeGroups> groupsProvider;
    private Provider<GroupReferences> referencesProvider;
    // An aggregated feed's indexes, one per source type group unless shared; none in tests that make the tool
    // themselves.
    private Provider<FeedIndexDecisions> feedIndexesProvider;
    private Provider<BuildTestIndexes> testIndexesProvider;

    @Inject
    PromoteBuildTool(final BuildFacts facts,
                     final PromotionLayout layout,
                     final DocumentPlaces places,
                     final WriteGuard guard,
                     final BuildRecords buildRecords,
                     final Consent consent,
                     final PromotionProcessing promotionProcessing,
                     final ProcessorFilterWrites filterWrites,
                     final OutputFeeds outputFeeds,
                     final DocumentWrites writes,
                     final DocumentAccess documentAccess,
                     final XsltHistory history,
                     final PendingDocumentation pendingDocs) {
        this.facts = facts;
        this.layout = layout;
        this.places = places;
        this.guard = guard;
        this.buildRecords = buildRecords;
        this.consent = consent;
        this.promotionProcessing = promotionProcessing;
        this.filterWrites = filterWrites;
        this.outputFeeds = outputFeeds;
        this.writes = writes;
        this.documentAccess = documentAccess;
        this.history = history;
        this.pendingDocs = pendingDocs;
    }

    /// An aggregated feed's modules, put beside the feed (pointed at the originals of an older build's unchanged
    /// working copies, which are discarded), and
    /// the reference data its group pipelines load (set by injection; none in tests that make the tool themselves).
    ///
    /// @param groups     The feed's source type groups.
    /// @param references The reference data its group pipelines load.
    @Inject
    void setAggregated(final Provider<SourceTypeGroups> groups, final Provider<GroupReferences> references) {
        this.groupsProvider = groups;
        this.referencesProvider = references;
    }

    /// An aggregated feed's indexes: each group's gains the build's new fields, recorded in the feed's
    /// registry, once clashes with the fields it has are resolved (set by injection; none in tests that make the
    /// tool themselves).
    ///
    /// @param feedIndexes The feed's indexes.
    @Inject
    void setFeedIndexes(final Provider<FeedIndexDecisions> feedIndexes) {
        this.feedIndexesProvider = feedIndexes;
    }

    /// @param testIndexes The build's test indexes: kept in the workspace at promotion, and a promoted pipeline
    ///                    pointed back at the production index each stood in for.
    @Inject
    void setTestIndexes(final Provider<BuildTestIndexes> testIndexes) {
        this.testIndexesProvider = testIndexes;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CONTENT;
    }

    @Override
    public boolean isReadOnly() {
        // It moves the build into production and makes processor filters.
        return false;
    }

    @Override
    public boolean confirmsItself() {
        // The user reviews the layout in the chat, then approves it in a short form bound to it, before anything
        // is changed.
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .unlockedBy(AppPermission.MANAGE_PROCESSORS_PERMISSION)
                .checksDocuments(DocumentPermission.VIEW, DocumentPermission.EDIT, DocumentPermission.DELETE)
                .note("Documents are moved, copied, written back and deleted through Stroom's explorer and stores "
                      + "as the user, who needs permission to do each, and to create folders in the destinations; "
                      + "the promoted pipelines' processor filters are made as the user (Manage Processors), only "
                      + "once they approve.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "Promote a build out of the workspace, after the user has seen where everything goes "
                             + "and approved it. The first call returns the layout as a tree (needs_review) to show "
                             + "in the chat; once the user confirms it (or after the destinations they asked for), "
                             + "call again with reviewed=<its review_id> for a short approval bound to that layout. "
                             + "Documents with no destination are named all at once, to ask the user about "
                             + "together. A build's documents go in a folder of the source's own: a folder given by "
                             + "type that holds other sources' folders becomes <it>/<feed>, as the siblings have. The "
                             + "build's test feed is never promoted: don't offer it. New documents are moved to their "
                             + "destination folders (UUIDs are kept, so "
                             + "filters and references keep working), role first, then version: "
                             + "<root>/<feed>/<Role>/<version> (e.g. Feeds/App-Logs/Events/V1.0, the version from the "
                             + "pipeline's name), or <root>/<feed>/<version> for a role whose location is elsewhere "
                             + "(e.g. Elastic Indices/App-Logs/V1.0); a pipeline's XSLTs, converter, index, "
                             + "documentation and dashboard with it. Each role's location is proposed (where sibling "
                             + "feeds' pipelines of that role are, or a new version's predecessor's) and chosen by "
                             + "the user. A new version (copy_pipeline) goes in a sibling version folder of its "
                             + "predecessor's (Events/V1.1); a promoted pipeline reading the predecessor's Events "
                             + "gains a new filter, disabled, reading the new version's (its own filters are left "
                             + "for the admin). Destination folders that don't exist yet are created "
                             + "first. A working copy is written back over its original after the original is "
                             + "backed up to <workspace>/backups, then the copy is deleted. The build's workspace "
                             + "folder is removed afterwards if nothing is left in it. An aggregated feed's helpers "
                             + "and common module go in <feed's folder>/Helpers/<version> and Common/<version>, its "
                             + "registry and source notes beside the feed; a source type group's documents in "
                             + "<feed's folder>/<group>/<Role>/<version> (its output feed in <group>/); nothing "
                             + "promoted is rewritten (the feed's other groups keep importing the versions they "
                             + "import); a working copy of a "
                             + "pipeline's code or of a module is refused. Each pipeline running a group's XSLT "
                             + "gains the reference data the group looks up (a pipeline change, in the approval). "
                             + "What "
                             + "build_status says "
                             + "is "
                             + "missing goes in the approval, except documentation the build's own change left "
                             + "behind: write_documentation again first (once promoted, documentation only changes "
                             + "through a working copy).")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(BUILD_ARG, "Build name, e.g. 'acme-door-v1.3'.")
                        .addProperty(DESTINATIONS_ARG, TranslationArgs.object(
                                "Destination folder per document type, per role or per document UUID, e.g. "
                                + "{'Feed': '/Feeds/Acme', 'Indexing': '/Elastic Indices'}: an explorer path, with "
                                + "or without the root System ('System / Feeds' works too). By type: where the "
                                + "feed goes (and documents of no role). By role (Events, Records, Indexing, CEF, "
                                + "Reference): the folder holding the feed's folders of that role, as the user "
                                + "chose (the review's role_locations). By UUID: exactly that folder for that "
                                + "document. Working copies ignore this: they are written back over their originals. "
                                + "'keep' for a document UUID leaves it in the build's "
                                + "workspace folder, not promoted (a test feed made while fixing, say). Documents "
                                + "with no destination are named all at once."))
                        .addStringProperty(REVIEWED_ARG, "The review_id of the needs_review reply, once the user has "
                                                         + "been shown its layout in the chat and confirmed it: they "
                                                         + "then approve it in a short form. Not true: a layout "
                                                         + "that changes (other destinations) gets a new id, to "
                                                         + "show again.")
                        .required(BUILD_ARG)
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        final String build = stringArg(call, BUILD_ARG);
        if (build == null) {
            return JsonResults.refused("Give build: the build's name, as start_onboarding or start_build named it.");
        }
        // Not promoted with a doc unwritten (seen: the agent said the documentation was done, nothing written).
        final Optional<String> undocumented = pendingDocs.warning(context.session());
        if (undocumented.isPresent()) {
            return JsonResults.refused(undocumented.get());
        }
        try {
            final Map<String, Object> result = promote(call, context, build);
            return UserActionEntries.withActions(JsonResults.of(result), result);
        } catch (final ToolRefusal | PipelineUnreadableException e) {
            return JsonResults.refused(e.getMessage());
        }
    }

    private Map<String, Object> promote(final ToolCall call, final ToolCallContext context, final String build) {
        final Map<String, String> destinations = destinations(call);
        final Object reviewed = argsMap(call).get(REVIEWED_ARG);
        final List<Map<String, Object>> docs = facts.buildDocs(build);
        if (docs.isEmpty()) {
            throw new ToolRefusal("Build '" + build + "' has no documents");
        }
        // An aggregated feed's build's source type group: its documents in a folder of its own beside the feed.
        final Map<String, ModuleChecks.Marker> markers = markers(docs);
        refuseCodeWorkingCopies(docs, sharedModules(markers));
        final GroupPlace group = group(build, docs, markers);
        // A build on data already in Stroom: its own feeds are test feeds, which stay in the workspace, and its
        // pipelines' filters name the feeds it works from (seen in VS Code: the promoted filter selected the test
        // feed).
        final List<String> sources = facts.sourceFeeds(build, docs);
        // The build's test feeds stay in the workspace, whatever the destinations say.
        final Set<String> testFeeds = testFeeds(build, docs);
        // The folder of the source's own its documents go in: the feed's, or one named for it where the feed's
        // folder is a parent of sources (the siblings' convention).
        final SourceFolders sourceFolders = new SourceFolders(guard, documentAccess);
        final Source source = source(build, docs, destinations, sources, testFeeds, sourceFolders);
        // A feed's source type registry goes beside the feed; its survey doc stays in the build.
        final Map<String, String> besideFeeds = besideFeeds(docs, destinations, sourceFolders);
        // An aggregated feed's modules and a group's output feed of its own: beside the feed (the group's own in its
        // group's folder there).
        modulesBeside(build, docs, destinations, sourceFolders, markers).forEach(besideFeeds::putIfAbsent);
        final Placed placed = new Placed(testFeeds, source, sourceFolders, group);
        final List<PromotionLayout.Step> plan = plan(docs, destinations, sources, besideFeeds, placed, markers);
        // Before the layout is shown, so it shows what the user chose (found reviewing the change: asked after
        // the review, a copy kept still showed as written back).
        askAboutChangedOriginals(call, context, build, plan);
        // Role first, then version (decided by the user, 2026-11-17): each role's root chosen, then every document
        // of a role in its version's folder there, and what documents or searches another beside it.
        final List<RoleRoot> roots = roleRoots(call, context, build, plan, destinations, placed, reviewed == null);
        final List<String> layoutWarnings = layOut(plan, roots, placed, markers);
        besideWhatItDocuments(plan, destinations);
        withWhatItSearches(plan, destinations);
        refuseUnplaced(plan, placed);
        // A working copy the user kept stays in the build: the modules promoted import its original, which must
        // have what they need of it.
        final Set<String> keptCopies = keptCopies(plan);
        if (groupsProvider != null && !keptCopies.isEmpty()) {
            final List<String> lacking = groupsProvider.get().keptProblems(build, docs, keptCopies);
            if (!lacking.isEmpty()) {
                throw new ToolRefusal("Not promoted: " + String.join("; ", lacking) + ". Overwrite production with "
                                      + "the working copy, or change the modules importing it, then promote_build "
                                      + "again. Nothing was changed.");
            }
        }
        // Every destination is resolved before approval, so a missing folder can't stop a promotion halfway.
        final Map<String, WriteGuard.Folder> folders = new LinkedHashMap<>();
        final List<String> creating = new ArrayList<>();
        for (final String target : new LinkedHashSet<>(moveTargets(plan))) {
            final WriteGuard.ResolvedFolder resolved = guard.resolveFolder(target);
            folders.put(resolved.folder().path(), resolved.folder());
            for (int n = 1; n <= resolved.missing().size(); n++) {
                final String path = resolved.folder().path() + "/" + String.join("/",
                        resolved.missing().subList(0, n));
                if (!creating.contains(path)) {
                    creating.add(path);
                }
            }
        }
        final List<DocRef> moving = plan.stream()
                .filter(p -> PromotionLayout.MOVE.equals(p.action()) && PipelineDoc.TYPE.equals(p.doc().get("type")))
                .map(p -> ref(p.doc()))
                .toList();
        final List<String> keptFeeds = plan.stream()
                .filter(p -> PromotionLayout.KEEP.equals(p.action()) && FeedDoc.TYPE.equals(p.doc().get("type")))
                .map(p -> String.valueOf(p.doc().get("name")))
                .toList();
        // New versions' predecessors, read before promotion clears the build's record: the next steps name their
        // filters, for the admin to disable or remove once the new version's output is right (decided by the user,
        // 2026-11-17: never done by the tools).
        final Map<String, String> predecessors = predecessors(build, moving);
        // The promoted pipelines outside the build reading a predecessor's Events (CEF flatteners, indexing): each
        // gains a filter, disabled, reading the new version's Events instead (decided by the user, 2026-11-17). One
        // with a new version of its own in this build is passed over: its new version reads the new Events.
        final List<PromotionProcessing.FollowOn> followOns = promotionProcessing.followOns(eventsVersions(plan,
                predecessors), pipeline -> guard.buildOf(pipeline).isEmpty() && !BuildDocumentation.inBuild(docs,
                pipeline.getUuid()) && !predecessors.containsValue(pipeline.getUuid()));
        final List<String> followOnSaid = followOns.stream().map(PromotionProcessing.FollowOn::said).toList();
        // A working copy of a promoted pipeline is deleted at promotion, its original going on: an index reading the
        // copy's Events in the build reads the original's once promoted.
        final Map<String, String> originals = new LinkedHashMap<>();
        docs.stream()
                .filter(d -> PipelineDoc.TYPE.equals(d.get("type")) && d.get("working_copy_of") != null)
                .forEach(d -> originals.put(String.valueOf(d.get("uuid")), String.valueOf(d.get("working_copy_of"))));
        final List<PromotionProcessing.Entry> processing = promotionProcessing.promotionProcessing(moving, sources,
                keptFeeds, originals);
        final List<String> warnings = facts.buildChecks(docs);
        final List<String> blocking = warnings.stream().filter(w -> w.endsWith(BuildChecks.BLOCKS)).toList();
        if (!blocking.isEmpty()) {
            throw new ToolRefusal("Not promoted: " + String.join(" ", blocking) + ". Do it in the build first, then "
                                  + "promote_build again: once promoted, the pipeline's documentation can only "
                                  + "change through a working copy.");
        }
        final List<String> processed = processingSaid(processing);
        // The reference data an aggregated feed's group pipelines gain for their groups' lookups: a pipeline change.
        final List<GroupReferences.Change> referenceChanges = referencesProvider == null
                ? List.of()
                : referencesProvider.get().changes(build, docs);
        final List<String> pipelineChanges = referenceChanges.stream().map(GroupReferences.Change::said).toList();
        // An aggregated feed's groups' indexes: held while a field clashes with one the index has.
        final List<FeedIndexDecisions.IndexChange> indexChanges = feedIndexesProvider == null
                ? List.of()
                : feedIndexesProvider.get().changes(build, docs);
        // Any build's indexing pipelines: the production Lucene indexes they write to once promoted gain the fields
        // their plans add (an aggregated feed's groups' indexes are the changes above).
        final Set<String> handled = new LinkedHashSet<>();
        indexChanges.stream()
                .filter(change -> change.indexDoc() != null)
                .forEach(change -> handled.add(change.indexDoc().getUuid()));
        final List<BuildTestIndexes.FieldAddition> fieldAdditions = testIndexesProvider == null
                ? List.of()
                : testIndexesProvider.get().promotionAdditions(build, docs, handled);
        final List<String> indexSaid = new ArrayList<>(indexChanges.stream()
                .map(FeedIndexDecisions.IndexChange::said)
                .toList());
        fieldAdditions.forEach(addition -> indexSaid.add(addition.said()));
        final String tree = layout.promotionTree(plan, creating) + rootsNote(roots)
                            + PromotionLayout.warningsNote(layoutWarnings) + testFeedNote(plan, testFeeds);
        if (!Consent.wasReviewed(reviewed, tree)) {
            final Map<String, Object> review = review(build, tree, warnings, processed, roots, reviewed);
            if (!followOnSaid.isEmpty()) {
                review.put("follow_on_filters", followOnSaid);
                review.put("hint", review.get("hint") + " Say the follow_on_filters too: each promoted pipeline "
                                   + "reading the earlier version's Events gains a new filter, disabled, reading the "
                                   + "new version's; its own filters are left as they are.");
            }
            if (!pipelineChanges.isEmpty()) {
                review.put("pipeline_changes", pipelineChanges);
                review.put("hint", review.get("hint") + " Say the pipeline_changes too: the reference data each "
                                   + "pipeline gains for its source type group's lookups.");
            }
            if (!indexSaid.isEmpty()) {
                review.put("index_changes", indexSaid);
                review.put("hint", review.get("hint") + " Say the index_changes too: each production index the "
                                   + "build's indexing changes (for an aggregated feed, its source type group's: its "
                                   + "own, unless the user chose to share one) and the fields this build adds to it "
                                   + "(forward only).");
            }
            return review;
        }
        // The form stays short; the approval is bound to the exact layout shown, by its review id, which the form
        // doesn't show (it meant nothing to the user).
        final long promoting = plan.stream()
                .filter(p -> PromotionLayout.MOVE.equals(p.action()) || PromotionLayout.WRITE_BACK.equals(p.action()))
                .count();
        final Map<String, Object> shortForm = new LinkedHashMap<>();
        shortForm.put("build", build);
        shortForm.put("layout", promoting + " document(s), as shown in the chat");
        shortForm.put("folders", new ArrayList<>(new TreeSet<>(moveTargets(plan))));
        if (!roots.isEmpty()) {
            final List<String> where = new ArrayList<>();
            roots.forEach(root -> where.add(root.role() + ": " + root.root()));
            shortForm.put("role locations", where);
        }
        if (!warnings.isEmpty()) {
            shortForm.put("warnings", warnings);
        }
        if (!processing.isEmpty()) {
            shortForm.put("processing after promotion", processed);
        }
        if (!pipelineChanges.isEmpty()) {
            shortForm.put("pipeline changes", pipelineChanges);
        }
        if (!indexSaid.isEmpty()) {
            shortForm.put("index changes", indexSaid);
        }
        if (!followOnSaid.isEmpty()) {
            shortForm.put("follow-on filters", followOnSaid);
        }
        consent.require(call, context, Consent.Request.approval(NAME,
                "Promote build '" + build + "' as shown in the chat", shortForm).boundTo(tree));
        final List<String> done = new ArrayList<>();
        if (groupsProvider != null) {
            // An aggregated feed's modules import the originals their working copies are written back over.
            try {
                done.addAll(groupsProvider.get().toOriginals(context, build, docs, keptCopies));
            } catch (final RuntimeException e) {
                throw new ToolRefusal("Not promoted: the build's modules couldn't be pointed at the originals of "
                                      + "their working copies: " + e.getMessage() + ". Done before it: "
                                      + (done.isEmpty()
                        ? "nothing"
                        : String.join("; ", done)));
            }
        }
        if (!indexChanges.isEmpty()) {
            // Recorded in the registry before it is moved beside the feed.
            try {
                done.addAll(feedIndexesProvider.get().apply(context, build, indexChanges));
            } catch (final RuntimeException e) {
                throw new ToolRefusal("Not promoted: the groups' indexes couldn't be recorded in the feed's "
                                      + "registry: " + e.getMessage() + ". Done before it: " + (done.isEmpty()
                        ? "nothing"
                        : String.join("; ", done)));
            }
        }
        if (!fieldAdditions.isEmpty()) {
            // Before the pipelines writing to them are promoted, so production indexing never meets a field the
            // index lacks.
            try {
                done.addAll(testIndexesProvider.get().apply(fieldAdditions));
            } catch (final RuntimeException e) {
                throw new ToolRefusal("Not promoted: the production indexes couldn't be given the build's new "
                                      + "fields: " + e.getMessage() + ". Done before it: " + (done.isEmpty()
                        ? "nothing"
                        : String.join("; ", done)));
            }
        }
        carryOut(build, docs, plan, folders, creating, done);
        if (!referenceChanges.isEmpty()) {
            done.addAll(referencesProvider.get().apply(referenceChanges));
        }
        // A choice about a changed original holds for this promotion only: promoted again, it is asked again. What
        // was kept is marked so, with its version, so it doesn't hold the build open on its feed until it changes.
        plan.stream()
                .filter(p -> PromotionLayout.KEEP.equals(p.action()))
                .forEach(p -> {
                    final DocRef kept = ref(p.doc());
                    final Optional<String> version = BuildRecords.version(documentAccess, kept);
                    buildRecords.update(build, kept, entry -> {
                        entry.remove(ORIGINAL_CHOICE);
                        entry.put(BuildRecords.KEPT_AT_PROMOTION, true);
                        version.ifPresentOrElse(v -> entry.put(BuildRecords.KEPT_VERSION, v),
                                () -> entry.remove(BuildRecords.KEPT_VERSION));
                    });
                });
        // What the record kept about the promoted docs only meant something inside the build; it goes once nothing
        // is left in the build.
        buildRecords.forget(build, plan.stream()
                .filter(p -> !PromotionLayout.KEEP.equals(p.action()))
                .map(p -> String.valueOf(p.doc().get("uuid")))
                .toList());
        if (guard.removeBuildFolderIfEmpty(build)) {
            done.add("removed the build's workspace folder, now empty");
        }
        // What was moved: a production index is never put up for dropping.
        final Set<String> moved = plan.stream()
                .filter(p -> PromotionLayout.MOVE.equals(p.action()))
                .map(p -> String.valueOf(p.doc().get("uuid")))
                .collect(Collectors.toSet());
        final List<Map<String, Object>> filters = promotionProcessing.createPromotionFilters(context.links(),
                processing, moved);
        final List<Map<String, Object>> followOnFilters = followOns.isEmpty()
                ? List.of()
                : promotionProcessing.createFollowOnFilters(context.links(), followOns, filters);
        return promoted(context, build, plan, folders, tree, done, warnings, filters, followOnFilters, predecessors);
    }

    // An aggregated feed's shared modules among the build's XSLTs (its common module and helpers, by their marks),
    // by UUID: a new version of one goes in a version folder of its own.
    private static Set<String> sharedModules(final Map<String, ModuleChecks.Marker> markers) {
        final Set<String> out = new LinkedHashSet<>();
        markers.forEach((uuid, marker) -> {
            if (ModuleChecks.HELPERS.equals(marker.kind()) || SourceTypeModule.COMMON.equals(marker.kind())) {
                out.add(uuid);
            }
        });
        return out;
    }

    // A working copy of a promoted pipeline, or of the code it runs (its XSLTs, text converters, an aggregated
    // feed's shared modules), from a build opened before new versions became the only way (decided by the user,
    // 2026-11-17: no change in place of a promoted pipeline or the code it runs, nor of a feed's common module or
    // helpers): refused, not written back. Documentation is updated in place as before; an unchanged XSLT or
    // converter copy is only discarded.
    private void refuseCodeWorkingCopies(final List<Map<String, Object>> docs, final Set<String> shared) {
        final List<String> refused = new ArrayList<>();
        boolean modules = false;
        for (final Map<String, Object> doc : docs) {
            final Object copyOf = doc.get("working_copy_of");
            final String type = String.valueOf(doc.get("type"));
            if (copyOf == null || !CODE_TYPES.contains(type)) {
                continue;
            }
            if (!PipelineDoc.TYPE.equals(type) && unchangedCopy(doc, String.valueOf(copyOf))) {
                continue;
            }
            refused.add(type + " '" + doc.get("name") + "'");
            modules |= shared.contains(String.valueOf(doc.get("uuid")));
        }
        if (!refused.isEmpty()) {
            throw new ToolRefusal("Not promoted: " + String.join(", ", refused) + (refused.size() == 1
                    ? " is a working copy"
                    : " are working copies") + " of a promoted pipeline's code, which changes only by a new "
                                  + "version, never written back in place. In this build, make a new version "
                                  + "instead: copy_pipeline of the promoted pipeline (it copies its code under the "
                                  + "new version's names), carry the change over to the new version's XSLT, and "
                                  + "delete the working copies (in Stroom); then promote_build again." + (modules
                ? " An aggregated feed's common module or helpers changes the same way: delete its working copy, "
                  + "and build_translation_xslt of the build's group makes the new version of it that the group "
                  + "imports."
                : "") + " Nothing was changed.");
        }
    }

    // The working copies the plan keeps in the build, by UUID: their originals stay as they are in production.
    private static Set<String> keptCopies(final List<PromotionLayout.Step> plan) {
        final Set<String> out = new LinkedHashSet<>();
        plan.stream()
                .filter(step -> PromotionLayout.KEEP.equals(step.action())
                                && step.doc().get("working_copy_of") != null)
                .forEach(step -> out.add(String.valueOf(step.doc().get("uuid"))));
        return out;
    }

    // The role a document is laid out under: its build role subfolder's, or for a pipeline at the build's top, its
    // stage's. None for a feed (beside its source, never in a version's folder) or a working copy.
    private String roleOf(final Map<String, Object> doc) {
        if (FeedDoc.TYPE.equals(doc.get("type")) || doc.get("working_copy_of") != null) {
            return null;
        }
        if (doc.get("role") != null) {
            return String.valueOf(doc.get("role"));
        }
        if (PipelineDoc.TYPE.equals(doc.get("type"))) {
            final String stage = stageOrNone(String.valueOf(doc.get("uuid")));
            return stage == null
                    ? null
                    : PipelineShapes.STAGE_ROLES.get(stage);
        }
        return null;
    }

    // An aggregated feed's module's role: a shared module's own (Common, Helpers: decided by the user, 2026-11-17),
    // a group's XSLT its pipeline's; null for anything else.
    private static String moduleRole(final Map<String, Object> doc, final ModuleChecks.Marker marker) {
        if (marker == null) {
            return null;
        }
        if (SourceTypeModule.COMMON.equals(marker.kind())) {
            return PromotionLayout.MODULE_ROLES.getFirst();
        }
        if (ModuleChecks.HELPERS.equals(marker.kind())) {
            return PromotionLayout.MODULE_ROLES.get(1);
        }
        if (SourceTypeModule.GROUP.equals(marker.kind())) {
            return NullSafe.nonBlankStringElse((String) doc.get("role"), WriteGuard.STAGE_ROLES.get("translation"));
        }
        return null;
    }

    // Each role's root, for a build whose source's folder is known (decided by the user, 2026-11-17): a destination
    // keyed by the role, else what the user chose in a form before (kept in the build's record, so the approval's
    // layout is the one reviewed), else asked now in one form on the review's call, else the proposal: a new
    // version's predecessor's root, else where the sibling feeds' pipelines of the role are, else the feed's own.
    private List<RoleRoot> roleRoots(final ToolCall call,
                                     final ToolCallContext context,
                                     final String build,
                                     final List<PromotionLayout.Step> plan,
                                     final Map<String, String> destinations,
                                     final Placed placed,
                                     final boolean asking) {
        if (placed.source() == null || placed.source().own() == null) {
            return List.of();
        }
        final String feedFolder = placed.source().own();
        final String feedRoot = PromotionLayout.parentOf(feedFolder);
        final Set<String> roles = new LinkedHashSet<>();
        plan.stream()
                .filter(step -> PromotionLayout.MOVE.equals(step.action()) && isRole(WriteGuard.ROLES, step.role()))
                .forEach(step -> roles.add(step.role()));
        if (roles.isEmpty()) {
            return List.of();
        }
        final Map<String, String> byPredecessor = predecessorRoots(plan, feedFolder, placed.group());
        Map<String, String> bySiblings = null;
        final Map<String, Object> recorded = buildRecords.promotionRoots(build);
        final List<RoleRoot> out = new ArrayList<>();
        final Map<String, Consent.Question> questions = new LinkedHashMap<>();
        for (final String role : roles) {
            String proposed = byPredecessor.get(role);
            String why = "proposed: where its earlier version is";
            if (proposed == null) {
                if (bySiblings == null) {
                    bySiblings = siblingRoots(feedFolder, placed.source().name());
                }
                proposed = bySiblings.get(role);
                why = "proposed: where the sibling feeds' " + role + " pipelines are";
            }
            if (proposed == null) {
                proposed = feedRoot;
                why = "proposed: the feed's own location";
            }
            final List<String> options = new ArrayList<>(List.of(proposed));
            if (!PromotionLayout.sameFolder(proposed, feedRoot)) {
                options.add(feedRoot);
            }
            final String given = roleKeyed(destinations, role);
            final Object chosen = recorded.get(role);
            if (given != null) {
                out.add(new RoleRoot(role, PromotionLayout.normalised(given), "as given", options, false));
            } else if (chosen instanceof final String root && !root.isBlank()) {
                out.add(new RoleRoot(role, root, "as the user chose", options, false));
            } else {
                out.add(new RoleRoot(role, proposed, why, options, true));
                if (asking) {
                    questions.put(role, new Consent.Question("Where " + role + " goes: the folder holding the "
                                                             + "feed's " + role + " folders (or type another)",
                            options));
                }
            }
        }
        if (!questions.isEmpty()) {
            final Optional<Map<String, String>> answered = consent.answerEach(call, context, NAME, "Where the "
                    + "build's documents of each role go: the first is proposed (" + String.join("; ", out.stream()
                    .filter(RoleRoot::toAsk)
                    .map(root -> root.role() + ": " + root.root() + ", " + root.why().replace("proposed: ", ""))
                    .toList()) + "); the feed's own location keeps them in " + feedFolder + "/<role>/<version>.",
                    questions);
            answered.ifPresent(answers -> answers.forEach((role, answer) -> {
                final String root = PromotionLayout.normalised(answer);
                buildRecords.rememberPromotionRoot(build, role, root);
                out.replaceAll(each -> each.role().equals(role)
                        ? new RoleRoot(role, root, "as the user chose", each.options(), false)
                        : each);
            }));
        }
        return out;
    }

    // Whether a role (or null) is one of these.
    private static boolean isRole(final List<String> roles, final String role) {
        return role != null && roles.contains(role);
    }

    // A destination keyed by a role (any case), or null.
    private static String roleKeyed(final Map<String, String> destinations, final String role) {
        return destinations.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(role) && NullSafe.isNonBlankString(entry.getValue()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    // The roots of the roles of the build's new versions, from their predecessors' folders.
    private Map<String, String> predecessorRoots(final List<PromotionLayout.Step> plan,
                                                 final String feedFolder,
                                                 final GroupPlace group) {
        final Map<String, String> out = new LinkedHashMap<>();
        for (final PromotionLayout.Step step : plan) {
            if (PromotionLayout.MOVE.equals(step.action()) && isRole(WriteGuard.ROLES, step.role())
                && PipelineDoc.TYPE.equals(step.doc().get("type"))) {
                layout.predecessorFolder(step.doc()).ifPresent(folder -> out.putIfAbsent(step.role(),
                        PromotionLayout.rootOf(PromotionLayout.predecessor(folder), feedFolder, group == null
                                ? null
                                : group.name())));
            }
        }
        return out;
    }

    // Where the sibling feeds' production pipelines of each role are (none in a build or a template another inherits
    // from). Never a reason for promotion to fail: without them, the feed's own location is proposed.
    private Map<String, String> siblingRoots(final String feedFolder, final String source) {
        try {
            final Set<String> siblings = new SourceFolders(guard, documentAccess).siblings(
                    PromotionLayout.parentOf(feedFolder), feedFolder, source);
            if (siblings.isEmpty()) {
                return Map.of();
            }
            final Map<String, PipelineIndex.Entry> pipelines = places.pipelines();
            final Set<String> parents = new LinkedHashSet<>();
            pipelines.values().forEach(entry -> {
                if (entry.parentUuid() != null) {
                    parents.add(entry.parentUuid());
                }
            });
            final String workspace = PipelineIndex.SYSTEM + "/" + guard.workspaceFolder();
            return PromotionLayout.siblingRoots(pipelines.values(), siblings, uuid -> {
                final String stage = stageOrNone(uuid);
                return stage == null
                        ? null
                        : PipelineShapes.STAGE_ROLES.get(stage);
            }, entry -> parents.contains(entry.uuid()) || entry.path().equals(workspace)
                        || entry.path().startsWith(workspace + "/"));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No sibling feeds' pipelines read for {}: {}", feedFolder,
                    e.getMessage()));
            return Map.of();
        }
    }

    // Each document of a role put in its role's and version's folder (PromotionLayout.layOut), each role's root as
    // chosen. What the layout warns of, for the user.
    private List<String> layOut(final List<PromotionLayout.Step> plan,
                                final List<RoleRoot> roots,
                                final Placed placed,
                                final Map<String, ModuleChecks.Marker> markers) {
        final Map<String, String> rootByRole = new LinkedHashMap<>();
        roots.forEach(root -> rootByRole.put(root.role(), root.root()));
        return layout.layOut(plan, rootByRole, placed.source() == null
                ? null
                : placed.source().own(), placed.group() == null
                ? null
                : placed.group().name(), step -> groupOf(step, placed, markers));
    }

    // The source type group a document of a role is laid out under: a group XSLT's own (by its mark: another
    // group's, the build may hold), else the build's group's; null in a build of none.
    private static String groupOf(final PromotionLayout.Step step,
                                  final Placed placed,
                                  final Map<String, ModuleChecks.Marker> markers) {
        final ModuleChecks.Marker marker = markers.get(String.valueOf(step.doc().get("uuid")));
        if (marker != null && SourceTypeModule.GROUP.equals(marker.kind())
            && NullSafe.isNonBlankString(marker.name())) {
            return marker.name().strip().replace('/', '-');
        }
        return placed.group() != null && isRole(WriteGuard.ROLES, step.role())
                ? placed.group().name()
                : null;
    }

    // What the layout says of each role's location, so the review (and the approval bound to it) covers them.
    private static String rootsNote(final List<RoleRoot> roots) {
        if (roots.isEmpty()) {
            return "";
        }
        return "\n\nWhere each role goes (destinations keyed by the role change it):\n" + String.join("\n",
                roots.stream().map(root -> "- " + root.role() + ": " + root.root() + " (" + root.why() + ")")
                        .toList());
    }

    // The new events (or records) versions promoted, each with its predecessor: their follow-on pipelines read the
    // predecessor's Events.
    private Map<DocRef, DocRef> eventsVersions(final List<PromotionLayout.Step> plan,
                                               final Map<String, String> predecessors) {
        final Map<DocRef, DocRef> out = new LinkedHashMap<>();
        for (final PromotionLayout.Step step : plan) {
            final String uuid = String.valueOf(step.doc().get("uuid"));
            if (PromotionLayout.MOVE.equals(step.action()) && PipelineDoc.TYPE.equals(step.doc().get("type"))
                && isRole(WriteGuard.SOURCE_ROLES, step.role()) && predecessors.containsKey(uuid)) {
                places.find(predecessors.get(uuid)).ifPresent(before -> out.put(ref(step.doc()),
                        new DocRef(PipelineDoc.TYPE, before.getUuid(), before.getName())));
            }
        }
        return out;
    }

    // What the user does once a new version is promoted (decided by the user, 2026-11-17): enable the new version's
    // filters, then, once its output is right, the admin disables or removes the earlier version's and those still
    // reading its Events. Never a reason for promotion to fail.
    private List<String> nextSteps(final List<Map<String, Object>> filters,
                                   final List<Map<String, Object>> followOnFilters,
                                   final Map<String, String> predecessors) {
        if (predecessors.isEmpty()) {
            return List.of();
        }
        final List<String> enable = new ArrayList<>();
        filters.stream()
                .filter(filter -> !Boolean.TRUE.equals(filter.get("enabled")))
                .forEach(filter -> enable.add("filter " + filter.get("filter_id") + " ('" + filter.get("pipeline")
                                              + "')"));
        followOnFilters.stream()
                .filter(filter -> !Boolean.TRUE.equals(filter.get("enabled")))
                .forEach(filter -> enable.add("filter " + filter.get("filter_id") + " ('" + filter.get("pipeline")
                                              + "', reading the Events of '" + filter.get("reads") + "')"));
        final List<String> earlier = new ArrayList<>();
        for (final String predecessor : new LinkedHashSet<>(predecessors.values())) {
            try {
                places.find(predecessor).ifPresent(found -> {
                    final DocRef before = new DocRef(PipelineDoc.TYPE, found.getUuid(), found.getName());
                    final List<String> ids = filterWrites.filters(before).stream()
                            .map(filter -> String.valueOf(filter.getId()))
                            .toList();
                    if (!ids.isEmpty()) {
                        earlier.add("'" + found.getName() + "': filter " + String.join(", ", ids));
                    }
                });
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> LogUtil.message("No filters of the earlier version {}: {}", predecessor,
                        e.getMessage()));
            }
        }
        final Map<String, List<String>> following = new LinkedHashMap<>();
        followOnFilters.forEach(filter -> following.computeIfAbsent("'" + filter.get("pipeline") + "'",
                ignored -> new ArrayList<>()).add(filter.get("copied_from") + " (reading the Events of '"
                                                  + filter.get("in_place_of") + "')"));
        following.forEach((pipeline, ids) -> earlier.add(pipeline + ": filter " + String.join(", ", ids)));
        final List<String> steps = new ArrayList<>();
        if (!enable.isEmpty()) {
            steps.add("1. Enable the new version's filters when ready: " + String.join("; ", enable) + ".");
        }
        if (!earlier.isEmpty()) {
            steps.add((steps.isEmpty()
                    ? "1"
                    : "2") + ". Once the new version's output is right, the admin disables or removes the earlier "
                      + "version's filters (the tools never do): " + String.join("; ", earlier) + ".");
        }
        return steps;
    }

    // Where each document goes: its folder, and the role it is laid out under there (layOut puts it in its role's
    // and version's folders below).
    private List<PromotionLayout.Step> plan(final List<Map<String, Object>> docs,
                                            final Map<String, String> destinations,
                                            final List<String> sources,
                                            final Map<String, String> besideFeeds,
                                            final Placed given,
                                            final Map<String, ModuleChecks.Marker> markers) {
        final List<PromotionLayout.Step> plan = new ArrayList<>();
        for (final Map<String, Object> doc : docs) {
            final String uuid = String.valueOf(doc.get("uuid"));
            final String type = String.valueOf(doc.get("type"));
            final Object copyOf = doc.get("working_copy_of");
            if (copyOf != null && PipelineDoc.TYPE.equals(type)) {
                // The copied pipeline only existed to step the working copies; the original keeps running.
                plan.add(new PromotionLayout.Step(doc, PromotionLayout.DISCARD, "deleted after write-back", null));
            } else if (copyOf != null && unchangedCopy(doc, String.valueOf(copyOf))) {
                // Seen in an eval: an unchanged working copy XSLT was listed as "updated from its working copy" in
                // the layout and updated_in_place, while the promotion discarded it. Decided here, so both agree.
                plan.add(new PromotionLayout.Step(doc, PromotionLayout.DISCARD, UNCHANGED_COPY, null));
            } else if (copyOf != null) {
                plan.add(new PromotionLayout.Step(doc, PromotionLayout.WRITE_BACK, String.valueOf(copyOf), null));
            } else if (given.testFeeds().contains(uuid)) {
                // Seen in chat 177: the build's test feed was in the layout, and the agent asked the user whether to
                // promote it. It holds only the sample's Events: never promoted, whatever the destinations say. A
                // test index, likewise, holds only the sample's documents.
                plan.add(new PromotionLayout.Step(doc, PromotionLayout.KEEP, FeedDoc.TYPE.equals(type)
                        ? TEST_FEED_KEPT
                        : TEST_INDEX_KEPT, null));
            } else if (!destinations.containsKey(uuid) && KEEP.equals(besideFeeds.get(uuid))) {
                plan.add(new PromotionLayout.Step(doc, PromotionLayout.KEEP, "left in the workspace: a survey doc "
                                                                             + "stays in its build", null));
            } else if (!destinations.containsKey(uuid) && besideFeeds.containsKey(uuid)) {
                String target = String.join("/", WriteGuard.folderParts(besideFeeds.get(uuid)));
                // The group's own output feed: in the group's folder beside the feed. A module XSLT is laid out under
                // its role: a shared module's (Common, Helpers) in the feed's folder, a group's with its pipeline in
                // the group's (asked for by the user: seen placed at the feed's folder, beside the common module and
                // helpers, apart from its pipeline).
                final String role = moduleRole(doc, markers.get(uuid));
                if (role == null && given.group() != null && given.group().docs().contains(uuid)) {
                    target = target + "/" + given.group().name();
                }
                plan.add(new PromotionLayout.Step(doc, PromotionLayout.MOVE, target, role));
            } else if (KEEP.equals(NullSafe.string(destinations.get(uuid)).strip().toLowerCase(Locale.ROOT))) {
                // Seen (fix_errors, Haiku): a test feed the agent made had nowhere to go but production, and with no
                // way to leave it out the agent never promoted the fix.
                plan.add(new PromotionLayout.Step(doc, PromotionLayout.KEEP, "left in the workspace", null));
            } else if (FeedDoc.TYPE.equals(type) && !destinations.containsKey(uuid)
                       && (!sources.isEmpty() || String.valueOf(doc.get("name")).endsWith(OutputFeeds.TEST_FEED))) {
                // Promoted only when the user gives it a folder of its own (a new feed version, say).
                plan.add(new PromotionLayout.Step(doc, PromotionLayout.KEEP, "left in the workspace: a test feed",
                        null));
            } else {
                final Placing placing = target(doc, docs, destinations, given.source());
                // A document of a role is laid out under its source's folder, whatever folder its type was given:
                // the role's location is chosen by role (destinations keyed by role), one document's by its uuid. A
                // new version with nowhere else to go is laid out beside its predecessor.
                final String role = destinations.containsKey(uuid) || placing.besideProduction()
                        ? null
                        : firstGiven(roleOf(doc), moduleRole(doc, markers.get(uuid)));
                final String placeTarget = role != null && given.source() != null && given.source().own() != null
                        ? given.source().own()
                        : firstGiven(placing.target(), role == null
                                ? null
                                : layout.predecessorFolder(doc)
                                        .map(folder -> PromotionLayout.predecessor(folder).base())
                                        .orElse(null));
                if (placeTarget == null) {
                    // A document of a role may yet go with its pipeline (laid out below); else it is named with the
                    // others that have no destination.
                    plan.add(new PromotionLayout.Step(doc, PromotionLayout.MOVE, null, role));
                    continue;
                }
                String target = String.join("/", WriteGuard.folderParts(placeTarget));
                if (!destinations.containsKey(uuid) && !placing.besideProduction() && given.source() != null) {
                    // In a folder of the source's own, never straight into a parent of other sources (whose role
                    // subfolders every source would share).
                    target = given.folders().ownFolder(target, given.source().name());
                }
                plan.add(new PromotionLayout.Step(doc, PromotionLayout.MOVE, target, role));
            }
        }
        return plan;
    }

    // Every document with no destination, said at once (seen, Delinea SecretServer: raised one document at a time,
    // the Dashboard's, then the TextConverter's, a failed call each).
    private static void refuseUnplaced(final List<PromotionLayout.Step> plan, final Placed given) {
        final List<Map<String, Object>> unplaced = plan.stream()
                .filter(step -> PromotionLayout.MOVE.equals(step.action()) && step.target() == null)
                .map(PromotionLayout.Step::doc)
                .toList();
        if (!unplaced.isEmpty()) {
            final Set<String> placed = new TreeSet<>(moveTargets(plan));
            final Set<String> types = new TreeSet<>();
            unplaced.forEach(doc -> types.add(String.valueOf(doc.get("type"))));
            throw new ToolRefusal("No destination for " + String.join("; ", unplaced.stream()
                    .map(doc -> doc.get("type") + " '" + doc.get("name") + "' (" + doc.get("uuid") + ")")
                    .toList()) + ". Add each to destinations, by type (" + String.join(", ", types) + ": a folder) "
                                  + "or by document uuid (a folder, or 'keep' to leave it in the workspace), where "
                                  + "the user wants it; a folder is an explorer path, e.g. /Feeds/Events/Acme"
                                  + (placed.isEmpty()
                    ? ""
                    : ". The others have one: " + String.join(", ", placed))
                                  + (given.source() == null
                    ? ""
                    : ". The source's own folder, as its siblings have one: " + given.source().own()
                      + " (a parent of other sources given by type is read as it)") + ". Nothing was changed.");
        }
    }

    // A pipeline's or index's Documentation (or evaluation report), not given a destination of its own, goes
    // wherever the document it documents goes, its role subfolder too: a folder given by uuid, a version folder,
    // or the source's own folder alike (never placed by type apart from it).
    private static void besideWhatItDocuments(final List<PromotionLayout.Step> plan,
                                              final Map<String, String> destinations) {
        final Map<String, PromotionLayout.Step> documented = new LinkedHashMap<>();
        for (final PromotionLayout.Step step : plan) {
            final Object type = step.doc().get("type");
            if (PromotionLayout.MOVE.equals(step.action())
                && (PipelineDoc.TYPE.equals(type) || INDEX_TYPES.contains(String.valueOf(type)))) {
                documented.putIfAbsent(String.valueOf(step.doc().get("name")), step);
            }
        }
        plan.replaceAll(step -> {
            final String name = String.valueOf(step.doc().get("name"));
            final String named = name.endsWith(WriteDocumentationTool.EVALUATION)
                    ? name.substring(0, name.length() - WriteDocumentationTool.EVALUATION.length())
                    : name;
            final PromotionLayout.Step of = documented.get(named);
            if (of == null || !PromotionLayout.MOVE.equals(step.action())
                || !DocumentationDoc.TYPE.equals(step.doc().get("type"))
                || destinations.containsKey(String.valueOf(step.doc().get("uuid")))) {
                return step;
            }
            return new PromotionLayout.Step(step.doc(), PromotionLayout.MOVE, of.target(), of.role());
        });
    }

    // A verification dashboard, not given a destination of its own, goes wherever the index it searches goes (decided
    // by the user, 2026-11-17: dashboards with the index they search).
    private void withWhatItSearches(final List<PromotionLayout.Step> plan, final Map<String, String> destinations) {
        final Map<String, String> indexes = new LinkedHashMap<>();
        plan.stream()
                .filter(step -> PromotionLayout.MOVE.equals(step.action())
                                && INDEX_TYPES.contains(String.valueOf(step.doc().get("type"))))
                .forEach(step -> indexes.put(String.valueOf(step.doc().get("uuid")), step.target()));
        if (indexes.isEmpty()) {
            return;
        }
        plan.replaceAll(step -> {
            if (!PromotionLayout.MOVE.equals(step.action()) || !DashboardDoc.TYPE.equals(step.doc().get("type"))
                || destinations.containsKey(String.valueOf(step.doc().get("uuid")))) {
                return step;
            }
            return searched(ref(step.doc())).stream()
                    .filter(indexes::containsKey)
                    .findFirst()
                    .map(index -> step.withTarget(indexes.get(index)))
                    .orElse(step);
        });
    }

    // Where each new survey doc and source type registry goes, by uuid: `keep` for a survey doc, which stays in the
    // build (decided 2026-11-13: no longer asked), or the folder of its feed for a registry. A registry always goes
    // beside the feed (decided 2026-11-13): the registry is a doc beside the feed, where later builds find it; kept
    // in the build, it held the promoted build open on the feed for ever. One whose feed has no folder yet is placed
    // as before.
    private Map<String, String> besideFeeds(final List<Map<String, Object>> docs,
                                            final Map<String, String> destinations,
                                            final SourceFolders folders) {
        final Map<String, String> out = new LinkedHashMap<>();
        for (final Map<String, Object> doc : docs) {
            final String name = String.valueOf(doc.get("name"));
            final String uuid = String.valueOf(doc.get("uuid"));
            final boolean survey = name.endsWith(" - Survey");
            final boolean registry = name.endsWith(SourceTypeRegistry.SUFFIX);
            if (!DocumentationDoc.TYPE.equals(doc.get("type")) || doc.get("working_copy_of") != null
                || !(survey || registry)) {
                continue;
            }
            if (registry && KEEP.equals(NullSafe.string(destinations.get(uuid)).strip().toLowerCase(Locale.ROOT))) {
                throw new ToolRefusal("The source type registry '" + name + "' goes beside its feed, where later "
                                      + "builds find it: it can't be kept in the workspace. Leave its uuid out of "
                                      + "destinations. Nothing was changed.");
            }
            if (destinations.containsKey(uuid)) {
                continue;
            }
            if (survey) {
                out.put(uuid, KEEP);
                continue;
            }
            final String feed = name.substring(0, name.length() - SourceTypeRegistry.SUFFIX.length());
            feedFolder(feed, docs, destinations).ifPresent(folder -> out.put(uuid, folders.ownFolder(folder, feed)));
        }
        return out;
    }

    // An aggregated feed's modules (its helpers, common module and groups, by the marker the generator
    // writes in each) and its groups' output feeds of their own: under the feed, where a later build finds them
    // through the registry (each module laid out in its role's version folder there). Not working copies (written
    // back where their originals are), nor any given a destination.
    private Map<String, String> modulesBeside(final String build,
                                              final List<Map<String, Object>> docs,
                                              final Map<String, String> destinations,
                                              final SourceFolders folders,
                                              final Map<String, ModuleChecks.Marker> markers) {
        final Optional<String> feed = docs.stream()
                .filter(doc -> DocumentationDoc.TYPE.equals(doc.get("type")))
                .map(doc -> String.valueOf(doc.get("name")))
                .filter(name -> name.endsWith(SourceTypeRegistry.SUFFIX))
                .map(name -> name.substring(0, name.length() - SourceTypeRegistry.SUFFIX.length()))
                .findFirst();
        final Optional<String> folder = feed.flatMap(name -> feedFolder(name, docs, destinations)
                .map(found -> folders.ownFolder(found, name)));
        if (folder.isEmpty()) {
            return Map.of();
        }
        final Map<String, String> out = new LinkedHashMap<>();
        for (final Map<String, Object> doc : docs) {
            final String uuid = String.valueOf(doc.get("uuid"));
            if (destinations.containsKey(uuid) || doc.get("working_copy_of") != null) {
                continue;
            }
            if (markers.containsKey(uuid) || (FeedDoc.TYPE.equals(doc.get("type")) && groupOutput(build, doc))) {
                out.put(uuid, folder.get());
            }
        }
        return out;
    }

    // The group named by the mark of one of the build's group XSLTs: new ones, or working copies.
    private static Optional<String> markedGroup(final List<Map<String, Object>> docs,
                                                final Map<String, ModuleChecks.Marker> markers,
                                                final boolean copies) {
        return docs.stream()
                .filter(doc -> (doc.get("working_copy_of") != null) == copies)
                .map(doc -> markers.get(String.valueOf(doc.get("uuid"))))
                .filter(marker -> marker != null && SourceTypeModule.GROUP.equals(marker.kind()))
                .map(ModuleChecks.Marker::name)
                .filter(NullSafe::isNonBlankString)
                .findFirst();
    }

    // The module mark of each of the build's XSLTs that has one (its helpers, common module or group), by UUID.
    private Map<String, ModuleChecks.Marker> markers(final List<Map<String, Object>> docs) {
        final Map<String, ModuleChecks.Marker> out = new LinkedHashMap<>();
        for (final Map<String, Object> doc : ofType(docs, XsltDoc.TYPE)) {
            documentAccess.read(ref(doc), XsltDoc.class)
                    .flatMap(xslt -> ModuleChecks.marker(NullSafe.string(xslt.getData())))
                    .ifPresent(marker -> out.put(String.valueOf(doc.get("uuid")), marker));
        }
        return out;
    }

    // An aggregated feed's build's source type group (a build makes one): its name, by the build's scope, else by
    // its new group XSLT's mark, else a working copy's (a build joining a group); and the documents of the group's
    // own that go beside the feed (its new XSLT and its output feed of its own), in its folder there. Null for a
    // build of no group: its documents are placed as before. Seen in e2e: by the first mark found, a second group's
    // build took the first group's name from the copy of its XSLT the build held.
    private GroupPlace group(final String build,
                             final List<Map<String, Object>> docs,
                             final Map<String, ModuleChecks.Marker> markers) {
        Optional<String> name = groupsProvider == null
                ? Optional.empty()
                : groupsProvider.get().scopedGroup(build, List.of());
        if (name.isEmpty()) {
            name = markedGroup(docs, markers, false).or(() -> markedGroup(docs, markers, true));
        }
        if (name.isEmpty()) {
            return null;
        }
        final Set<String> own = new LinkedHashSet<>();
        for (final Map<String, Object> doc : docs) {
            final String uuid = String.valueOf(doc.get("uuid"));
            if (doc.get("working_copy_of") != null) {
                continue;
            }
            final ModuleChecks.Marker marker = markers.get(uuid);
            // Another group's XSLT the build holds is placed as a module is, beside the feed.
            if ((marker != null && SourceTypeModule.GROUP.equals(marker.kind())
                 && name.get().equals(NullSafe.string(marker.name()).strip()))
                || (FeedDoc.TYPE.equals(doc.get("type")) && groupOutput(build, doc))) {
                own.add(uuid);
            }
        }
        // A folder's name: a slash in the group's name would make it two.
        return new GroupPlace(name.get().strip().replace('/', '-'), own);
    }

    // A feed made in the build as a source type group's output feed of its own.
    private boolean groupOutput(final String build, final Map<String, Object> feed) {
        return buildRecords.entry(build, String.valueOf(feed.get("uuid"))).get(BuildFeeds.GROUP_OUTPUT) != null;
    }

    // The folder a feed's documents go beside: an existing feed's own, or where the build's feed of that name goes.
    private Optional<String> feedFolder(final String feed,
                                        final List<Map<String, Object>> docs,
                                        final Map<String, String> destinations) {
        final Optional<Map<String, Object>> own = ofType(docs, FeedDoc.TYPE).stream()
                .filter(doc -> feed.equalsIgnoreCase(String.valueOf(doc.get("name"))))
                .findFirst();
        if (own.isPresent()) {
            return Optional.ofNullable(firstGiven(destinations.get(String.valueOf(own.get().get("uuid"))),
                    destinations.get(FeedDoc.TYPE)));
        }
        return places.named(FeedDoc.TYPE, feed).stream()
                .filter(ref -> guard.buildOf(ref).isEmpty())
                .findFirst()
                .flatMap(places::folderOf);
    }

    // The destination a new document goes to, and whether it is beside a production document.
    private Placing target(final Map<String, Object> doc,
                           final List<Map<String, Object>> docs,
                           final Map<String, String> destinations,
                           final Source source) {
        final String uuid = String.valueOf(doc.get("uuid"));
        final String type = String.valueOf(doc.get("type"));
        final String name = String.valueOf(doc.get("name"));
        String target = firstGiven(destinations.get(uuid), destinations.get(type));
        if (target == null && DocumentationDoc.TYPE.equals(type)
            && (name.endsWith(" source notes") || name.contains(" reference - "))) {
            // The source's notes and reference documents: beside the feed, where later work on it looks (in the
            // source's own folder, as its registry is: seen in chat 177, the notes went to the parent of the
            // sources while the registry went to the source's folder).
            final List<Map<String, Object>> feeds = ofType(docs, FeedDoc.TYPE);
            target = source != null && source.own() != null
                    ? source.own()
                    : feeds.size() == 1
                            ? firstGiven(destinations.get(String.valueOf(feeds.getFirst().get("uuid"))),
                            destinations.get(FeedDoc.TYPE))
                            : null;
        }
        if (target == null && DocumentationDoc.TYPE.equals(type)) {
            // The documentation of a pipeline promoted with it: wherever that pipeline goes.
            final Map<String, Object> same = docs.stream()
                    .filter(d -> PipelineDoc.TYPE.equals(d.get("type")) && name.equals(d.get("name"))
                                 && d.get("working_copy_of") == null)
                    .findFirst()
                    .orElse(null);
            target = same == null
                    ? null
                    : firstGiven(destinations.get(String.valueOf(same.get("uuid"))),
                            destinations.get(PipelineDoc.TYPE));
        }
        if (target == null && DashboardDoc.TYPE.equals(type)) {
            target = dashboardTarget(doc, docs, destinations);
        }
        if (target == null && DocumentationDoc.TYPE.equals(type)) {
            target = besideProductionDoc(docs, name);
            if (target != null) {
                return new Placing(target, true);
            }
        }
        return new Placing(target, false);
    }

    // The documentation of a production pipeline (an in-place change's) or an existing index: beside it. An
    // evaluation report goes beside the pipeline it evaluates. Null when there is none (or several).
    private String besideProductionDoc(final List<Map<String, Object>> docs, final String name) {
        final String named = name.endsWith(WriteDocumentationTool.EVALUATION)
                ? name.substring(0, name.length() - WriteDocumentationTool.EVALUATION.length())
                : name;
        final List<DocRef> outside = new ArrayList<>();
        for (final String kind : List.of(PipelineDoc.TYPE, ElasticIndexDoc.TYPE, LuceneIndexDoc.TYPE)) {
            places.named(kind, named).stream()
                    .filter(found -> !BuildDocumentation.inBuild(docs, found.getUuid()))
                    .forEach(outside::add);
        }
        return outside.size() == 1
                ? places.folderOf(outside.getFirst()).orElse(null)
                : null;
    }

    // The build's test feeds: named as the tools name one (OutputFeeds.TEST_FEED), or recorded as the Events
    // output of the feeds the build works from (not a source type group's own output feed).
    private Set<String> testFeeds(final String build, final List<Map<String, Object>> docs) {
        final Set<String> out = new LinkedHashSet<>();
        for (final Map<String, Object> feed : ofType(docs, FeedDoc.TYPE)) {
            final String uuid = String.valueOf(feed.get("uuid"));
            if (feed.get("working_copy_of") != null) {
                continue;
            }
            final Map<String, Object> entry = buildRecords.entry(build, uuid);
            final boolean named = String.valueOf(feed.get("name")).toUpperCase(Locale.ROOT)
                    .endsWith(OutputFeeds.TEST_FEED);
            final boolean recorded = entry.get(BuildFeeds.OUTPUT_FOR) != null
                                     && entry.get(BuildFeeds.GROUP_OUTPUT) == null;
            if (named || recorded) {
                out.add(uuid);
            }
        }
        // The build's test indexes (BuildTestIndexes), standing in for production indexes while it was open.
        final Set<String> listed = new LinkedHashSet<>();
        docs.forEach(doc -> listed.add(String.valueOf(doc.get("uuid"))));
        testIndexes(build).keySet().stream().filter(listed::contains).forEach(out::add);
        return out;
    }

    // The build's test indexes, each with the production index doc's UUID it stands in for; none without the tool.
    private Map<String, String> testIndexes(final String build) {
        return testIndexesProvider == null
                ? Map.of()
                : testIndexesProvider.get().testIndexes(build);
    }

    // What the layout says of the test feeds kept, for the user and the agent: never promoted, nothing to offer.
    private static String testFeedNote(final List<PromotionLayout.Step> plan, final Set<String> testFeeds) {
        final List<String> kept = plan.stream()
                .filter(step -> PromotionLayout.KEEP.equals(step.action())
                                && FeedDoc.TYPE.equals(step.doc().get("type"))
                                && testFeeds.contains(String.valueOf(step.doc().get("uuid"))))
                .map(step -> String.valueOf(step.doc().get("name")))
                .toList();
        return kept.isEmpty()
                ? ""
                : "\n\nThe build's test feed " + String.join(", ", kept) + " stays in the build's workspace: it holds "
                  + "only the sample's Events and is never promoted.";
    }

    // The source the build's documents are of (the feed it works from, its registry's, or its only new feed) and
    // the folder of its own they go in; null when there is none, or its folder isn't known yet.
    private Source source(final String build,
                          final List<Map<String, Object>> docs,
                          final Map<String, String> destinations,
                          final List<String> sources,
                          final Set<String> testFeeds,
                          final SourceFolders folders) {
        String name = sources.isEmpty()
                ? null
                : sources.getFirst();
        if (name == null) {
            name = ofType(docs, DocumentationDoc.TYPE).stream()
                    .map(doc -> String.valueOf(doc.get("name")))
                    .filter(named -> named.endsWith(SourceTypeRegistry.SUFFIX))
                    .map(named -> named.substring(0, named.length() - SourceTypeRegistry.SUFFIX.length()))
                    .findFirst()
                    .orElse(null);
        }
        if (name == null) {
            final List<String> feeds = ofType(docs, FeedDoc.TYPE).stream()
                    .filter(feed -> feed.get("working_copy_of") == null
                                    && !testFeeds.contains(String.valueOf(feed.get("uuid")))
                                    && !groupOutput(build, feed))
                    .map(feed -> String.valueOf(feed.get("name")))
                    .toList();
            name = feeds.size() == 1
                    ? feeds.getFirst()
                    : null;
        }
        if (name == null) {
            return null;
        }
        final String feed = name;
        return feedFolder(feed, docs, destinations)
                .map(folder -> new Source(feed, folders.ownFolder(folder, feed)))
                .orElse(null);
    }

    // A verification dashboard: wherever the index it searches goes (seen in VS Code: promotion stopped for want
    // of its destination), else beside the pipelines.
    private String dashboardTarget(final Map<String, Object> doc,
                                   final List<Map<String, Object>> docs,
                                   final Map<String, String> destinations) {
        final Set<String> searched = searched(ref(doc));
        final Map<String, Object> index = docs.stream()
                .filter(d -> INDEX_TYPES.contains(String.valueOf(d.get("type")))
                             && searched.contains(String.valueOf(d.get("uuid"))))
                .findFirst()
                .orElse(null);
        String target = index == null
                ? null
                : firstGiven(destinations.get(String.valueOf(index.get("uuid"))),
                        destinations.get(String.valueOf(index.get("type"))));
        if (target == null && index == null) {
            // An index from outside the build (a new version writing to an existing index doc): the dashboard goes
            // with the build's indexing pipeline, into its version folder.
            final List<Map<String, Object>> feeding = docs.stream()
                    .filter(d -> PipelineDoc.TYPE.equals(d.get("type")) && d.get("working_copy_of") == null)
                    .filter(d -> INDEXING_STAGES.contains(NullSafe.string(stageOrNone(
                            String.valueOf(d.get("uuid"))))))
                    .toList();
            if (feeding.size() == 1) {
                final String feedingUuid = String.valueOf(feeding.getFirst().get("uuid"));
                target = destinations.get(feedingUuid);
            }
        }
        return target != null
                ? target
                : destinations.get(PipelineDoc.TYPE);
    }

    // The indexes a dashboard's queries search.
    private Set<String> searched(final DocRef dashboard) {
        final Set<String> found = new LinkedHashSet<>();
        try {
            final Map<String, Object> json = writes.fields(dashboard);
            if (json.get("dashboardConfig") instanceof final Map<?, ?> config
                && config.get("components") instanceof final List<?> components) {
                for (final Object item : components) {
                    if (item instanceof final Map<?, ?> component && "query".equals(component.get("type"))
                        && component.get("settings") instanceof final Map<?, ?> settings
                        && settings.get("dataSource") instanceof final Map<?, ?> dataSource) {
                        found.add(String.valueOf(dataSource.get("uuid")));
                    }
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to read dashboard {}: {}", dashboard, e.getMessage()));
        }
        return found;
    }

    // A pipeline's stage, or null for one Stroom can't load (a choice then made without it, not a failure).
    private String stageOrNone(final String pipelineUuid) {
        try {
            return facts.pipelineShape(pipelineUuid).stage();
        } catch (final PipelineUnreadableException e) {
            return null;
        }
    }

    private Map<String, String> predecessors(final String build, final List<DocRef> moving) {
        final Map<String, String> predecessors = new LinkedHashMap<>();
        for (final DocRef pipeline : moving) {
            try {
                final Object before = buildRecords.entry(build, pipeline.getUuid()).get("version_of");
                if (before != null) {
                    predecessors.put(pipeline.getUuid(), String.valueOf(before));
                }
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> LogUtil.message("No predecessor read for {}: {}", pipeline, e.getMessage()));
            }
        }
        return predecessors;
    }

    // What each promoted pipeline goes on processing, as the approval says it.
    private static List<String> processingSaid(final List<PromotionProcessing.Entry> processing) {
        final List<String> said = new ArrayList<>();
        for (final PromotionProcessing.Entry e : processing) {
            if (e.feed() != null) {
                said.add(e.pipeline().getName() + ": feed " + e.feed() + " (" + e.streamType() + ")");
            } else {
                said.add(e.pipeline().getName() + ": " + e.streamType() + " from pipeline " + String.join(", ",
                        e.extraTerms().stream()
                                .filter(t -> "Pipeline".equals(t.getField()))
                                .map(PromoteBuildTool::termDocName)
                                .toList()));
            }
        }
        return said;
    }

    private static String termDocName(final ExpressionTerm term) {
        return NullSafe.nonBlankStringElse(NullSafe.get(term.getDocRef(), DocRef::getName), "?");
    }

    // Shown in the chat first: a list of moves crammed into a form was hard to read, and where things would go
    // wasn't plain (asked for by the user). Passed on only with this layout's id (seen: reviewed=true on the first
    // call, and the user approved a form whose layout was never shown).
    private static Map<String, Object> review(final String build,
                                              final String tree,
                                              final List<String> warnings,
                                              final List<String> processed,
                                              final List<RoleRoot> roots,
                                              final Object reviewed) {
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "needs_review");
        result.put("build", build);
        result.put("layout", tree);
        result.put("review_id", Consent.reviewId(tree));
        if (!warnings.isEmpty()) {
            result.put("warnings", warnings);
        }
        if (!processed.isEmpty()) {
            result.put("processing_after_promotion", processed);
        }
        final List<Map<String, Object>> locations = new ArrayList<>();
        for (final RoleRoot root : roots) {
            final Map<String, Object> location = new LinkedHashMap<>();
            location.put("role", root.role());
            location.put("location", root.root());
            location.put("why", root.why());
            location.put("options", root.options());
            locations.add(location);
        }
        if (!locations.isEmpty()) {
            result.put("role_locations", locations);
        }
        final boolean asked = roots.stream().allMatch(root -> !root.toAsk());
        result.put("hint", "Show the user the layout in the chat exactly as it is (a code block), with any warnings "
                           + "and the processing after promotion, and ask them to confirm it or say what to change "
                           + "(a folder: destinations by type or document uuid; a role's location: destinations "
                           + "keyed by the role, e.g. {'Indexing': '/Elastic Indices'})"
                           + (asked
                ? ""
                : "; and where each role's documents go (role_locations: the first option is proposed, the "
                  + "others are the feed's own location or a folder the user names): their answer goes in "
                  + "destinations keyed by the role, then show the tree again")
                           + ". Once they confirm it, promote_build again with the same destinations and reviewed='"
                           + Consent.reviewId(tree) + "' (this layout's review_id): they approve it "
                           + "in a short form. If they change something, call again with their changes and no "
                           + "reviewed: the new layout is shown the same way." + Consent.reviewNote(reviewed));
        return result;
    }

    // Each step is a separate write to Stroom: one that fails leaves the ones before it done. Said, so the user
    // knows what is in production now and what is still in the build, not only that the promotion failed.
    private void carryOut(final String build,
                          final List<Map<String, Object>> docs,
                          final List<PromotionLayout.Step> plan,
                          final Map<String, WriteGuard.Folder> folders,
                          final List<String> creating,
                          final List<String> done) {
        // The build is done: its changes become one line of each XSLT's version history and one row of each doc's.
        String doing = "consolidating version histories";
        try {
            final List<String> versioned = consolidateVersions(docs);
            for (final String path : creating) {
                doing = "creating folder " + path;
                final int slash = path.lastIndexOf('/');
                // Production folders, so not managed: only generated, to show the server made them. One made since
                // the review (by someone else, or in Stroom's UI while the form was open) is used, not twinned.
                final WriteGuard.Folder made = guard.createFolder(folders.get(path.substring(0, slash)),
                        path.substring(slash + 1), Set.of(WriteGuard.GENERATED));
                folders.put(path, made);
                done.add(made.created()
                        ? "created folder " + path
                        : "used the existing folder " + path);
            }
            done.addAll(versioned);
            final Set<String> buildFeeds = new LinkedHashSet<>();
            // Not a group's output feed of its own: promoted with the build, its pipeline goes on writing there.
            ofType(docs, FeedDoc.TYPE).stream()
                    .filter(feed -> !groupOutput(build, feed))
                    .forEach(feed -> buildFeeds.add(String.valueOf(feed.get("uuid"))));
            final Map<String, String> testIndexes = testIndexes(build);
            for (final PromotionLayout.Step step : plan) {
                final String name = String.valueOf(step.doc().get("name"));
                doing = "restoring production settings on '" + name + "'";
                if (PromotionLayout.MOVE.equals(step.action()) && PipelineDoc.TYPE.equals(step.doc().get("type"))) {
                    final String uuid = String.valueOf(step.doc().get("uuid"));
                    if (outputFeeds.restoreOutputFeed(uuid, buildFeeds)) {
                        // Its Events went to the build's test feed (an existing feed's streams, processed in the
                        // build): in production they go into the input's feed.
                        done.add("'" + name + "' writes its Events into its input's feed again (the build's test "
                                 + "feed was for the build only)");
                    }
                    if (testIndexesProvider != null && testIndexesProvider.get().restoreIndex(uuid, testIndexes)) {
                        // It indexed into the build's test index: in production it indexes into the index that
                        // stood in for.
                        done.add("'" + name + "' indexes into its production index again (the build's test index "
                                 + "was for the build only)");
                    }
                }
            }
            final List<String> order = List.of(PromotionLayout.WRITE_BACK, PromotionLayout.MOVE,
                    PromotionLayout.DISCARD, PromotionLayout.KEEP);
            final List<PromotionLayout.Step> ordered = plan.stream()
                    .sorted(Comparator.comparingInt(step -> order.indexOf(step.action())))
                    .toList();
            for (final PromotionLayout.Step step : ordered) {
                doing = step.action() + " " + step.doc().get("type") + " '" + step.doc().get("name") + "'";
                done.add(carryOut(step, folders));
            }
        } catch (final RuntimeException e) {
            final String reason = e instanceof ToolRefusal
                    ? e.getMessage()
                    : e.getClass().getSimpleName() + ": " + e.getMessage();
            throw new ToolRefusal("The promotion stopped while " + doing + ": " + reason + ". Done before it: "
                                  + (done.isEmpty()
                    ? "nothing"
                    : String.join("; ", done)) + ". What wasn't promoted is still in the build '" + build + "': "
                                  + "show the user both, fix the cause, then call promote_build again for the "
                                  + "rest.");
        }
    }

    // One step, carried out: what was done.
    private String carryOut(final PromotionLayout.Step step, final Map<String, WriteGuard.Folder> folders) {
        final DocRef ref = ref(step.doc());
        return switch (step.action()) {
            case PromotionLayout.KEEP -> "left " + ref.getType() + " '" + ref.getName() + "' in the workspace";
            case PromotionLayout.DISCARD -> {
                guard.delete(ref);
                yield UNCHANGED_COPY.equals(step.target())
                        ? "discarded unchanged working copy " + ref.getType() + " '" + ref.getName() + "'"
                        : "deleted working-copy pipeline '" + ref.getName() + "'";
            }
            case PromotionLayout.MOVE -> {
                guard.moveTo(ref, folders.get(step.target()));
                // Now production content: the agent may no longer change it directly. mcp-generated stays, so it
                // is still known as the server's own.
                guard.untag(List.of(ref), Set.of(WriteGuard.MANAGED));
                yield "moved " + ref.getType() + " '" + ref.getName() + "' to " + step.target();
            }
            default -> writeBack(ref, step.target());
        };
    }

    // Each working copy whose original was changed since it was taken (e.g. edited by hand in production): the user
    // chooses whether the copy overwrites it, told how the two differ; kept, the copy (and the working-copy
    // pipeline that uses it) stays in the build (asked for by the user: a production edit made after the copy was
    // taken was lost, but for its backup). The choice is kept in the build's record against the original's version,
    // so the review and the approval ask once; changed again, it is asked again.
    private void askAboutChangedOriginals(final ToolCall call,
                                          final ToolCallContext context,
                                          final String build,
                                          final List<PromotionLayout.Step> plan) {
        boolean anyKept = false;
        for (int i = 0; i < plan.size(); i++) {
            final PromotionLayout.Step step = plan.get(i);
            if (!PromotionLayout.WRITE_BACK.equals(step.action())) {
                continue;
            }
            final DocRef copy = ref(step.doc());
            final Optional<ChangedOriginal> changed = changedSinceCopied(build, copy, step.target());
            if (changed.isEmpty()) {
                continue;
            }
            final Map<String, Object> entry = buildRecords.entry(build, copy.getUuid());
            Boolean keep = entry.get(ORIGINAL_CHOICE) instanceof final Map<?, ?> choice
                           && changed.get().version().equals(choice.get("version"))
                    ? Boolean.TRUE.equals(choice.get("keep"))
                    : null;
            if (keep == null) {
                final Optional<String> chosen = consent.choose(call, context, NAME, changed.get().said(),
                        List.of(OVERWRITE_ORIGINAL, KEEP_ORIGINAL), copy.getType() + " '" + copy.getName()
                                                                    + "' was changed in production since it was "
                                                                    + "copied");
                if (chosen.isEmpty()) {
                    throw new ToolRefusal("Not promoted: " + changed.get().said() + " Promotion needs a client that "
                                          + "can show forms to ask the user.");
                }
                keep = KEEP_ORIGINAL.equals(chosen.get());
                final boolean kept = keep;
                buildRecords.update(build, copy, record -> record.put(ORIGINAL_CHOICE, Map.of("version",
                        changed.get().version(), "keep", kept)));
            }
            if (keep) {
                plan.set(i, new PromotionLayout.Step(step.doc(), PromotionLayout.KEEP, step.target(), step.role()));
                anyKept = true;
            }
        }
        if (anyKept) {
            // The working-copy pipelines stay with the copies kept, rather than being discarded from under them.
            plan.replaceAll(step -> PromotionLayout.DISCARD.equals(step.action())
                                    && !UNCHANGED_COPY.equals(step.target())
                    ? new PromotionLayout.Step(step.doc(), PromotionLayout.KEEP, step.target(), step.role())
                    : step);
        }
    }

    /// @param build        The build.
    /// @param copy         A working copy.
    /// @param originalUuid Its original's UUID.
    /// @return What changed in the original since the copy was taken, said for the user with the choice, and its
    /// version now; empty if it is as it was (or it isn't known, for a copy taken before this was recorded).
    Optional<ChangedOriginal> changedSinceCopied(final String build, final DocRef copy, final String originalUuid) {
        final Object copiedVersion = buildRecords.entry(build, copy.getUuid()).get(BuildRecords.COPY_OF_VERSION);
        if (copiedVersion == null) {
            return Optional.empty();
        }
        final Map<String, Object> original = writes.fields(new DocRef(copy.getType(), originalUuid));
        if (copiedVersion.equals(original.get("version"))) {
            return Optional.empty();
        }
        final Map<String, Object> copied = writes.fields(copy);
        final String by = original.get("updateUser") == null
                ? ""
                : " by " + original.get("updateUser");
        final String when = original.get("updateTimeMs") instanceof final Number ms
                ? " at " + Instant.ofEpochMilli(ms.longValue())
                : "";
        final String difference = original.get("data") instanceof final String productionCode
                                  && copied.get("data") instanceof final String copyCode
                ? difference(productionCode, copyCode)
                : "";
        return Optional.of(new ChangedOriginal(original.get("version"), copy.getType() + " '" + copy.getName()
                           + "' was changed in production since its working "
                           + "copy was taken" + by + when + "." + difference + " " + OVERWRITE_ORIGINAL + " (the "
                           + "production change is then kept only in its backup), or "
                           + KEEP_ORIGINAL.toLowerCase(Locale.ROOT) + " (the working copy stays in the build, for "
                           + "its change to be made again on a fresh working copy)?"));
    }

    // How production and the copy differ, what overwriting would lose from production first (found testing it: in
    // the code's order, the copy's change filled the lines shown and production's own edit was cut off).
    private static String difference(final String productionCode, final String copyCode) {
        final List<String> lost = new ArrayList<>();
        final List<String> added = new ArrayList<>();
        for (final String line : CodeDiff.diff(productionCode, copyCode, Integer.MAX_VALUE)) {
            if (line.startsWith("- ")) {
                lost.add(line.substring(2));
            } else if (line.startsWith("+ ")) {
                added.add(line.substring(2));
            }
        }
        return PipelineTemplates.shorten((lost.isEmpty()
                ? ""
                : " Overwriting it loses from production: " + lines(lost) + ".")
                + (added.isEmpty()
                ? ""
                : " The working copy brings: " + lines(added) + "."), MAX_DIFF_CHARS);
    }

    private static String lines(final List<String> lines) {
        final List<String> shown = lines.subList(0, Math.min(MAX_DIFF_LINES, lines.size()));
        return String.join(" | ", shown) + (lines.size() > shown.size()
                ? " | ... and " + (lines.size() - shown.size()) + " more lines"
                : "");
    }

    // True when a working copy's written-back content is the same as its original's now: nothing to write back.
    private boolean unchangedCopy(final Map<String, Object> doc, final String originalUuid) {
        try {
            final DocRef copy = ref(doc);
            return places.find(originalUuid)
                    .map(found -> DocumentWrites.same(writes.fields(copy), writes.fields(
                            new DocRef(copy.getType(), found.getUuid(), found.getName())), WRITTEN_BACK))
                    .orElse(false);
        } catch (final RuntimeException e) {
            // Unreadable here: decided when it is written back, as before.
            return false;
        }
    }

    // A working copy written back over its original, after the original is backed up.
    private String writeBack(final DocRef copy, final String originalUuid) {
        final DocRef original = places.find(originalUuid)
                .map(found -> new DocRef(copy.getType(), found.getUuid(), found.getName()))
                .orElseThrow(() -> new ToolRefusal(copy.getType() + " " + originalUuid + " can't be read: it doesn't "
                                                   + "exist, or you don't have permission to view it"));
        if (DocumentWrites.same(writes.fields(copy), writes.fields(original), WRITTEN_BACK)) {
            guard.delete(copy);
            return "discarded unchanged working copy " + copy.getType() + " '" + copy.getName() + "'";
        }
        final WriteGuard.Folder backups = guard.buildFolder("backups");
        final List<DocRef> backupRefs = guard.copyTo(original, backups, original.getName() + " backup "
                                                                        + BACKUP_STAMP.format(history.now()));
        if (!backupRefs.isEmpty()) {
            guard.tag(backupRefs, Set.of(WriteGuard.GENERATED));
        }
        writes.copyFields(copy, original, WRITTEN_BACK);
        if (guard.tags(copy).contains(WriteGuard.KEPT_MAPPING)) {
            // Its description now holds the mapping: the safeguard that notices it deleted goes with it.
            guard.tag(List.of(original), Set.of(WriteGuard.KEPT_MAPPING));
        }
        guard.delete(copy);
        return "wrote " + copy.getType() + " '" + copy.getName() + "' back over '" + original.getName()
               + "' (backup kept)";
    }

    /// A build's pending changes, as one new line of each XSLT's version history and one new row of each doc's version
    /// control: called when the build is promoted.
    ///
    /// @param docs The build's documents.
    /// @return What was recorded.
    List<String> consolidateVersions(final List<Map<String, Object>> docs) {
        final List<String> done = new ArrayList<>();
        final Instant now = history.now();
        for (final Map<String, Object> listed : docs) {
            final DocRef ref = ref(listed);
            if (XsltDoc.TYPE.equals(ref.getType())) {
                final XsltDoc doc = documentAccess.read(ref, XsltDoc.class)
                        .orElseThrow(() -> new ToolRefusal("XSLT " + ref.getUuid() + " can't be read"));
                if (XsltVersionHistory.pendingOf(doc.getDescription()).filter(p -> !p.isEmpty()).isPresent()) {
                    final String code = NullSafe.string(doc.getData());
                    final String description = XsltVersionHistory.consolidate(XsltVersionHistory.adopt(
                            doc.getDescription(), code), code, now);
                    writeXslt(doc.copy().description(description).data(XsltVersionHistory.strip(code)).build());
                    done.add("recorded the build's changes as version " + XsltVersionHistory.history(description)
                            .size() + " of XSLT '" + ref.getName() + "' (its Documentation)");
                }
            } else if (DocumentationDoc.TYPE.equals(ref.getType())) {
                final DocumentationDoc doc = writes.documentation(ref);
                final Optional<String> text = DocumentationVersions.consolidate(BuildRecords.bodyText(doc), now);
                if (text.isPresent()) {
                    writes.write(BuildRecords.withBodyText(doc, text.get()));
                    done.add("recorded the build's changes as version " + DocumentationVersions.rowsOf(text.get())
                            .size() + " of documentation '" + ref.getName() + "'");
                }
            }
        }
        return done;
    }

    @SuppressWarnings("unchecked")
    private void writeXslt(final XsltDoc doc) {
        final DocumentActionHandler<XsltDoc> handler = documentAccess.handler(XsltDoc.TYPE)
                .orElseThrow(() -> new ToolRefusal("Stroom has no XSLT store"));
        handler.writeDocument(doc);
    }

    // The layout the user approved, as promoted, with a link to open each folder in Stroom (asked for by the
    // user).
    private Map<String, Object> promoted(final ToolCallContext context,
                                         final String build,
                                         final List<PromotionLayout.Step> plan,
                                         final Map<String, WriteGuard.Folder> folders,
                                         final String tree,
                                         final List<String> done,
                                         final List<String> warnings,
                                         final List<Map<String, Object>> filters,
                                         final List<Map<String, Object>> followOnFilters,
                                         final Map<String, String> predecessors) {
        final List<Map<String, Object>> links = new ArrayList<>();
        for (final String path : new TreeSet<>(moveTargets(plan))) {
            final WriteGuard.Folder folder = folders.get(path);
            if (folder != null && folder.uuid() != null) {
                final Map<String, Object> link = new LinkedHashMap<>();
                link.put("folder", path);
                link.put("link", context.links().documentTarget(new DocRef(ExplorerConstants.FOLDER_TYPE,
                        folder.uuid())));
                links.add(link);
            }
        }
        final List<Map<String, Object>> updated = new ArrayList<>();
        plan.stream().filter(p -> PromotionLayout.WRITE_BACK.equals(p.action())).forEach(p -> {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("document", p.doc().get("name") + " (" + p.doc().get("type") + ")");
            entry.put("link", context.links().documentTarget(new DocRef(String.valueOf(p.doc().get("type")),
                    p.target())));
            updated.add(entry);
        });
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("build", build);
        result.put("layout", tree);
        result.put("folders", links);
        if (!updated.isEmpty()) {
            result.put("updated_in_place", updated);
        }
        result.put("promoted", done);
        result.put("hint", "Show the user the layout as promoted, exactly as it is (a code block, the same tree they "
                           + "approved), with each folder's link to open it in Stroom" + (updated.isEmpty()
                ? ""
                : " and each document updated in place") + ".");
        if (!warnings.isEmpty()) {
            result.put("promoted_with_warnings", warnings);
        }
        if (context.session().isChat()) {
            // Once per build, at its end (asked for by the user): the choices made building it, and in earlier
            // chats, written down so the next build follows them. Only in the chat, whose sessions are the
            // evidence.
            result.put("standing_instructions", "Once the user has what they need about the promotion, call "
                                                + "instruction_evidence: if it finds repeated choices, offer once "
                                                + "to propose them as standing instructions for the feed's folder "
                                                + "(propose_instructions, which they review and apply). Don't "
                                                + "offer if it finds none, or if the folder's AGENTS doc says them "
                                                + "already.");
        }
        // No buttons disabling an earlier version's filters (decided by the user, 2026-11-17): disabling and
        // removing them is the admin's, once the new version's output is right; the next steps say which.
        final List<Object> buttons = new ArrayList<>();
        for (final Map<String, Object> filter : filters) {
            final Object actions = filter.remove(UserActionEntries.USER_ACTIONS);
            if (actions instanceof final List<?> list) {
                buttons.addAll(list);
            }
        }
        for (final Map<String, Object> filter : followOnFilters) {
            final Object actions = filter.remove(UserActionEntries.USER_ACTIONS);
            if (actions instanceof final List<?> list) {
                buttons.addAll(list);
            }
        }
        if (!buttons.isEmpty()) {
            result.put(UserActionEntries.USER_ACTIONS, buttons);
        }
        if (!followOnFilters.isEmpty()) {
            result.put("follow_on_filters", followOnFilters);
        }
        final List<String> nextSteps = nextSteps(filters, followOnFilters, predecessors);
        if (!nextSteps.isEmpty()) {
            result.put("next_steps", nextSteps);
        }
        if (!filters.isEmpty()) {
            result.put("processing_filters", filters);
            result.put("next", "Tell the user each promoted pipeline has a processing filter, created disabled (one "
                               + "marked existing is an enabled filter it already had, selecting what arrives: no "
                               + "second one was made): review the pipeline (pipeline_link) and enable it on its "
                               + "Processors tab when ready. Enabled, it processes everything on its feed, existing "
                               + "data included, and what the build processed again: if an earlier version still "
                               + "processes the same feed, it processes that history again, so decide with them "
                               + "first (limit the filter in Stroom, say). Give them the next_steps as they are: "
                               + "disabling or removing the earlier version's filters is the admin's, never the "
                               + "tools'."
                               + (filters.stream().anyMatch(f -> f.get("before_enabling") != null)
                    ? " A filter with before_enabling indexes into an index that must start fresh: ask the user to "
                      + "have it done first, as it says (an index left as it is holds the build's events twice)."
                    : ""));
        }
        return result;
    }

    private static Map<String, String> destinations(final ToolCall call) {
        Object value = argsMap(call).get(DESTINATIONS_ARG);
        if (value instanceof final String text && !text.isBlank()) {
            try {
                value = ToolJson.loads(text.strip());
            } catch (final RuntimeException e) {
                throw new ToolRefusal("destinations is an object, {type or document uuid: folder}");
            }
        }
        final Map<String, String> out = new LinkedHashMap<>();
        if (value instanceof final Map<?, ?> map) {
            map.forEach((key, folder) -> {
                if (folder != null) {
                    out.put(String.valueOf(key), String.valueOf(folder));
                }
            });
        } else if (value != null && !(value instanceof String)) {
            throw new ToolRefusal("destinations is an object, {type or document uuid: folder}");
        }
        return out;
    }

    private static List<String> moveTargets(final List<PromotionLayout.Step> plan) {
        return plan.stream()
                .filter(p -> PromotionLayout.MOVE.equals(p.action()) && p.target() != null)
                .map(PromotionLayout.Step::target)
                .toList();
    }

    private static List<Map<String, Object>> ofType(final List<Map<String, Object>> docs, final String type) {
        return docs.stream().filter(doc -> type.equals(doc.get("type"))).toList();
    }

    private static String firstGiven(final String... values) {
        for (final String value : values) {
            if (NullSafe.isNonEmptyString(value)) {
                return value;
            }
        }
        return null;
    }

    private static DocRef ref(final Map<String, Object> doc) {
        return new DocRef(String.valueOf(doc.get("type")), String.valueOf(doc.get("uuid")),
                Objects.toString(doc.get("name"), null));
    }


    // --------------------------------------------------------------------------------


    /// Where a document goes.
    ///
    /// @param target           Its folder, or null if none is known.
    /// @param besideProduction True if beside a production document, where no role subfolder is kept.
    private record Placing(String target, boolean besideProduction) {

    }

    /// A role's root: the folder holding the feed's folders of that role (the feed's own location keeps them in
    /// `<feed>/<Role>/<version>`; another, `<root>/<feed>/<version>`).
    ///
    /// @param role    The role, e.g. `Indexing`.
    /// @param root    The root chosen, from System.
    /// @param why     Where it came from, for the user.
    /// @param options The roots offered: the proposal first, then the feed's own location where it differs (the user
    ///                may name another).
    /// @param toAsk   True if it is the proposal, the user not asked yet.
    record RoleRoot(String role, String root, String why, List<String> options, boolean toAsk) {

    }


    /// The source a build's documents are of.
    ///
    /// @param name The source feed's name.
    /// @param own  The folder of the source's own its documents go in, normalised.
    private record Source(String name, String own) {

    }

    /// What decides where a build's documents go, beside the destinations.
    ///
    /// @param testFeeds The build's test feeds, by UUID: never promoted.
    /// @param source    The source, or null.
    /// @param folders   Tells a folder of the source's own from a parent of sources.
    /// @param group     An aggregated feed's build's source type group, or null.
    private record Placed(Set<String> testFeeds, Source source, SourceFolders folders, GroupPlace group) {

    }

    /// An aggregated feed's build's source type group, whose documents go in a folder of its own in the feed's.
    ///
    /// @param name The group's name: its folder's.
    /// @param docs The documents of its own placed beside the feed (its XSLT, its output feed), by UUID.
    private record GroupPlace(String name, Set<String> docs) {

    }

    /// An original changed since its working copy was taken.
    ///
    /// @param version Its version now.
    /// @param said    What changed, for the user, with the choice.
    record ChangedOriginal(Object version, String said) {

    }
}
