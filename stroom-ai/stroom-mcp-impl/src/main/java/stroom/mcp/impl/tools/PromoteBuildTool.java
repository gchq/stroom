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
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.generation.ModuleChecks;
import stroom.ai.impl.tools.generation.SourceTypeModule;
import stroom.ai.impl.tools.sample.SourceTypeRegistry;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.ai.shared.AiToolCategory;
import stroom.dashboard.shared.DashboardDoc;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.shared.ExplorerConstants;
import stroom.feed.shared.FeedDoc;
import stroom.index.shared.LuceneIndexDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.XsltDoc;
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
import java.util.stream.Stream;

/// `promote_build` - promotes a build out of the workspace once the user has seen where everything goes and approved
/// it. New documents are moved to their destination folders, working copies written back over their originals after a
/// backup, and feed-wide processor filters made, disabled, for the promoted pipelines: all through Stroom's explorer
/// and stores as the user, once they approve the layout in a short form bound to it. Then it tidies up after itself
/// ([PromotionTidyUp], unless the user turns it off).
class PromoteBuildTool extends AbstractBuiltInTool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PromoteBuildTool.class);

    static final String NAME = "promote_build";
    static final String BUILD_ARG = "build";
    static final String DESTINATIONS_ARG = "destinations";
    static final String REVIEWED_ARG = "reviewed";
    /// What a working copy's write-back takes from it.
    static final List<String> WRITTEN_BACK = List.of("data", "converterType", "documentation", "description");
    // A promoted pipeline and the code it runs: never written back from a working copy.
    private static final Set<String> CODE_TYPES = Set.of(PipelineDoc.TYPE, XsltDoc.TYPE, TextConverterDoc.TYPE);

    private static final String KEEP = "keep";
    // The status of the result once promoted: content for the user to see as it is.
    private static final String NEEDS_SHOWING = "needs_showing";
    // What the chat remembers of the latest layout shown for a build: its review id.
    private static final String LAYOUT_SHOWN = "layout shown";
    private static final String REVIEW_ID = "review_id";
    // And the digest of everything that review showed but its hint.
    private static final String SHOWN = "shown";
    // What becomes of a build's test feed.
    private static final String TEST_FEED_KEPT = "left in the workspace: the build's test feed, never promoted";
    private static final String TEST_INDEX_KEPT = "left in the workspace: the build's test index, never promoted";
    private static final String TEST_DASHBOARD_KEPT = "left in the workspace: it searches the build's test index";
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
    private final PromotionTidyUp tidyUp;
    private final ChangedOriginals changedOriginals;
    private final PromotionRoleRoots roleLocations;
    private final PromotionReview layoutReview;
    private final PromotionWrites stepWrites;
    // An aggregated feed's modules and the reference data its pipelines load; none in tests that make the tool
    // themselves.
    private Provider<SourceTypeGroups> groupsProvider;
    private Provider<GroupReferences> referencesProvider;
    // An aggregated feed's indexes, one per source type group unless shared; none in tests that make the tool
    // themselves.
    private Provider<FeedIndexDecisions> feedIndexesProvider;
    private Provider<BuildTestIndexes> testIndexesProvider;
    // How old filters selecting the build's feeds hand over to its new ones, and how much the feeds hold; none in
    // tests that make the tool themselves.
    private Provider<PromotionSwitchOver> switchOverProvider;
    private Provider<FeedVolumes> feedVolumesProvider;

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
                     final PendingDocumentation pendingDocs,
                     final PromotionTidyUp tidyUp) {
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
        this.tidyUp = tidyUp;
        this.changedOriginals = new ChangedOriginals(buildRecords, writes);
        this.roleLocations = new PromotionRoleRoots(facts, layout, places, guard, buildRecords, consent,
                documentAccess);
        this.layoutReview = new PromotionReview(consent);
        this.stepWrites = new PromotionWrites(places, guard, writes, documentAccess, history);
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

    /// How the build's new filters take over feeds old filters select, and how much each feed holds (set by
    /// injection; none in tests that make the tool themselves).
    ///
    /// @param switchOver  Finds the old filters selecting the build's feeds, and says the switch-over.
    /// @param feedVolumes Counts each feed's streams and their size.
    @Inject
    void setSwitchOver(final Provider<PromotionSwitchOver> switchOver, final Provider<FeedVolumes> feedVolumes) {
        this.switchOverProvider = switchOver;
        this.feedVolumesProvider = feedVolumes;
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
                             + "build's test feeds and test indexes are never promoted (don't offer them); once it is "
                             + "promoted they are tidied up as the review lists (on unless the user unticks it, or "
                             + "tidy_up=false): their streams marked deleted, shards deleted, then the test docs and "
                             + "their -VERIFY dashboards deleted. New documents are moved to their destination "
                             + "folders (UUIDs are kept, so filters and references keep working), role first, then "
                             + "version: "
                             + "<root>/<feed>/<Role>/<version> (e.g. Feeds/App-Logs/Events/V1.0, the version from the "
                             + "pipeline's name), or <root>/<feed>/<version> for a role whose location is elsewhere "
                             + "(e.g. Elastic Indices/App-Logs/V1.0); a pipeline's XSLTs, converter, index, "
                             + "documentation and dashboard with it; what nothing the build's pipelines use (a "
                             + "splitter it replaced, say) stays in the build, listed as not_promoted. Each role's "
                             + "location is proposed (where sibling "
                             + "feeds' pipelines of that role are, or a new version's predecessor's) and chosen by "
                             + "the user. A new version (copy_pipeline) goes in a sibling version folder of its "
                             + "predecessor's (Events/V1.1); a promoted pipeline reading the predecessor's Events "
                             + "gains a new filter, disabled, reading the new version's (its own filters are left "
                             + "for the admin). Destination folders that don't exist yet are created "
                             + "first. A working copy is written back over its original after the original is "
                             + "backed up to <workspace>/backups, then the copy is deleted (also one whose "
                             + "original was changed in production since it was copied: the review's "
                             + "changed_in_production and the approval say so). The build's workspace "
                             + "folder is removed afterwards if nothing is left in it. An aggregated feed's helpers "
                             + "and common module go in <feed's folder>/Helpers/<version> and Common/<version>, its "
                             + "registry and source notes beside the feed; a source type group's documents in "
                             + "<feed's folder>/<group>/<Role>/<version> (its output feed in <group>/); nothing "
                             + "promoted is rewritten (the feed's other groups keep importing the versions they "
                             + "import); a working copy of a pipeline's code or of a module is refused. Each pipeline "
                             + "running a group's XSLT gains the reference data the group looks up (a pipeline "
                             + "change, in the approval). What build_status says is missing goes in the approval, "
                             + "except documentation the build's own change left behind: write_documentation again "
                             + "first (once promoted, documentation only changes through a working copy).")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(BUILD_ARG, "Build name, e.g. 'acme-door-v1.3'.")
                        .addProperty(DESTINATIONS_ARG, TranslationArgs.object(
                                "Destination folder per document type, per role or per document UUID, e.g. "
                                + "{'Feed': '/Feeds/Acme', 'Indexing': '/Elastic Indices'}: an explorer path, with "
                                + "or without the root System ('System / Feeds' works too). By type: where the "
                                + "feed goes (and documents of no role). By role (Events, Records, Indexing, CEF, "
                                + "Reference): the folder holding the feed's folder for that role, as the user "
                                + "chose (the review's role_locations): the feed's own location (or the feed's "
                                + "folder itself) keeps <feed>/<Role>/<version>, another gives "
                                + "<it>/<feed>/<version>. By UUID: exactly that folder for that document (one "
                                + "nothing the build promotes uses is promoted only so). Working copies ignore "
                                + "this: they are written back over their originals. 'keep' for a document UUID "
                                + "leaves it in the build's workspace folder, not promoted (a test feed made while "
                                + "fixing, say). Documents with no destination are named all at once."))
                        .addStringProperty(REVIEWED_ARG, "The review_id of the needs_review reply, once the user has "
                                                         + "been shown its layout in the chat and confirmed it: they "
                                                         + "then approve it in a short form. Not true: a layout "
                                                         + "that changes (other destinations) gets a new id, to "
                                                         + "show again. Left out, the layout last shown in this "
                                                         + "chat, if unchanged.")
                        .addBooleanProperty(PromotionTidyUp.TIDY_UP, "False keeps the build's test feeds, test "
                                                                     + "indexes and their dashboards in the "
                                                                     + "workspace after promotion (default true: "
                                                                     + "tidied up, as the review lists).")
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
        // The feeds the build is for: its filters select them, and the earlier version keeps the rest (decided by the
        // user, 2026-11-25).
        final List<String> buildFeeds = facts.buildSourceFeeds(build, docs);
        // What nothing the build promotes uses (an older splitter it replaced, say) stays in the build (decided by the
        // user, 2026-11-18).
        final Set<String> unused = unused(docs, markers);
        // The build's test feeds (and test indexes, among them those the agent made itself that nothing promoted
        // writes to, and their dashboards) stay in the workspace, whatever the destinations say.
        final Set<String> testFeeds = tidyUp.testDocs(build, docs, testIndexes(build), unused);
        // The folder of the source's own its documents go in: the feed's, or one named for it where the feed's
        // folder is a parent of sources (the siblings' convention).
        final SourceFolders sourceFolders = new SourceFolders(guard, documentAccess);
        final Source source = source(build, docs, destinations, buildFeeds, testFeeds, sourceFolders);
        // A feed's source type registry goes beside the feed; its survey doc stays in the build.
        final Map<String, String> besideFeeds = besideFeeds(docs, destinations, sourceFolders);
        // An aggregated feed's modules and a group's output feed of its own: beside the feed (the group's own in its
        // group's folder there).
        modulesBeside(build, docs, destinations, sourceFolders, markers).forEach(besideFeeds::putIfAbsent);
        final Placed placed = new Placed(testFeeds, source, sourceFolders, group, sources, unused);
        final List<PromotionLayout.Step> plan = plan(docs, destinations, sources, besideFeeds, placed, markers);
        // Role first, then version (decided by the user, 2026-11-17): each role's root chosen, then every document
        // of a role in its version's folder there, and what documents or searches another beside it.
        final List<PromotionRoleRoots.RoleRoot> chosenRoots = roleLocations.roleRoots(call, context, build, docs,
                plan, destinations, placed, markers, reviewed == null);
        final Map<String, String> rootByRole = new LinkedHashMap<>();
        chosenRoots.forEach(root -> rootByRole.put(root.role(), root.root()));
        final List<String> layoutWarnings = roleLocations.layOut(plan, rootByRole, placed, markers);
        final List<PromotionRoleRoots.RoleRoot> roots = PromotionRoleRoots.withFolders(chosenRoots, plan);
        besideWhatItDocuments(plan, destinations);
        withWhatItSearches(plan, destinations);
        refuseUnplaced(plan, placed);
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
        // A working copy of a promoted pipeline is deleted at promotion, its original going on: an index reading the
        // copy's Events in the build reads the original's once promoted.
        final Map<String, String> originals = new LinkedHashMap<>();
        docs.stream()
                .filter(d -> PipelineDoc.TYPE.equals(d.get("type")) && d.get("working_copy_of") != null)
                .forEach(d -> originals.put(String.valueOf(d.get("uuid")), String.valueOf(d.get("working_copy_of"))));
        final List<PromotionProcessing.Entry> processing = promotionProcessing.promotionProcessing(moving,
                buildFeeds, keptFeeds, originals);
        // The promoted pipelines outside the build reading a predecessor's Events (CEF flatteners, indexing): each
        // gains a filter, disabled, reading the new version's Events instead (decided by the user, 2026-11-17). One
        // with a new version of its own in this build is passed over: its new version reads the new Events. So is
        // one writing to an index a pipeline the build promotes indexes the new Events into already: never two
        // filters indexing the same Events into the same index.
        final PromotionProcessing.Followed followed = promotionProcessing.withoutDoubleIndexing(
                promotionProcessing.followOns(eventsVersions(plan, predecessors), pipeline ->
                        guard.buildOf(pipeline).isEmpty() && !BuildDocumentation.inBuild(docs, pipeline.getUuid())
                        && !predecessors.containsValue(pipeline.getUuid())), processing, testIndexes(build));
        final List<PromotionProcessing.FollowOn> followOns = followed.followOns();
        // Old filters (any pipeline's) already selecting the new filters' feeds: each new filter is made disabled and
        // the switch-over is the user's (decided by the user, 2026-11-25); and what each earlier version keeps.
        final Set<String> inBuild = new LinkedHashSet<>();
        docs.forEach(doc -> inBuild.add(String.valueOf(doc.get("uuid"))));
        final List<List<PromotionSwitchOver.OldSelection>> overlaps = switchOverProvider == null
                ? processing.stream().map(entry -> List.<PromotionSwitchOver.OldSelection>of()).toList()
                : switchOverProvider.get().overlaps(processing, inBuild::contains);
        final List<String> switchOverSaid = PromotionSwitchOver.said(processing, overlaps);
        final Map<String, List<String>> keeps = keeps(predecessors, processing);
        final List<String> keptSaid = new ArrayList<>();
        keeps.forEach((name, kept) -> {
            if (!kept.isEmpty()) {
                keptSaid.add("'" + name + "' keeps " + String.join(", ", kept) + ": it goes on processing them, as "
                             + "this build doesn't move them");
            }
        });
        // How much each new filter's feeds hold: enabled, a new filter processes every stream it selects.
        final List<String> volumes = volumesSaid(processing);
        final List<String> followOnSaid = followOns.stream().map(PromotionProcessing.FollowOn::said).toList();
        final List<String> warnings = new ArrayList<>(facts.buildChecks(docs));
        final List<String> leftOut = moving.isEmpty()
                ? List.of()
                : promotionProcessing.leftOut(buildFeeds);
        if (!leftOut.isEmpty()) {
            warnings.add("No filter selects " + String.join(", ", leftOut) + ": no feed of exactly that name "
                         + "exists, so the promoted pipelines' filters leave it out (a filter names each feed by "
                         + "reference)");
        }
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
        // What is tidied up once promoted (decided by the user, 2026-11-17): in the layout, so the approval bound to
        // it is bound to the tidy-up too.
        final PromotionTidyUp.Plan tidy = PromotionTidyUp.wanted(argsMap(call).get(PromotionTidyUp.TIDY_UP))
                ? tidyUp.plan(build, docs, plan, testFeeds, sources)
                : PromotionTidyUp.Plan.OFF;
        // A production original changed since its working copy was taken is overwritten all the same, after its
        // backup (decided by the user, 2026-11-18): the review and the approval say so, never a question of its own.
        final List<String> changedSaid = changedOriginals.said(build, plan, ExplorerConstants.SYSTEM + "/"
                                                                            + guard.workspaceFolder() + "/"
                                                                            + PromotionWrites.BACKUPS);
        final String laidOut = layout.promotionTree(plan, creating) + PromotionRoleRoots.rootsNote(roots)
                               + PromotionLayout.warningsNote(layoutWarnings);
        // Shown with the tidy-up's counts, bound to its items alone (found in review, 2026-11-18).
        final String tree = laidOut + PromotionTidyUp.layoutNote(plan, testFeeds, tidy, true);
        final String bound = laidOut + PromotionTidyUp.layoutNote(plan, testFeeds, tidy, false);
        // Everything the review shows, whether or not it is shown now: a layout shown in this chat and confirmed
        // there is taken without its review_id only where nothing it showed has changed since.
        final String feedFolder = placed.source() == null
                ? null
                : placed.source().own();
        final String rolesHint = roots.isEmpty() || feedFolder == null
                ? null
                : PromotionRoleRoots.roleKeysHint(feedFolder,
                        roots.stream().map(PromotionRoleRoots.RoleRoot::role).toList(),
                        PromotionRoleRoots.feedKeyCoversRoles(docs, placed.source()));
        final Map<String, Object> review = PromotionReview.review(build, tree, bound, warnings, processed, roots,
                rolesHint, reviewed);
        if (!volumes.isEmpty()) {
            review.put(PromotionReview.FEED_STREAMS, volumes);
            review.put("hint", review.get("hint") + " Say the feed_streams too: enabled, each new filter processes "
                               + "every stream of its feeds it hasn't processed, existing ones included (a dormant "
                               + "feed's history too), unless the new version's filter is limited to streams from "
                               + "a time on.");
        }
        if (!switchOverSaid.isEmpty()) {
            review.put("switch_over", switchOverSaid);
            review.put("hint", review.get("hint") + " Say the switch_over too: old filters already select these "
                               + "feeds, so the new filter is made disabled and the user switches over in Stroom "
                               + "(the tools change no old filter or dictionary): which feeds move and which stay.");
        }
        if (!keptSaid.isEmpty()) {
            review.put("earlier_versions_keep", keptSaid);
            review.put("hint", review.get("hint") + " Say the earlier_versions_keep too: those feeds stay with the "
                               + "earlier version, whose filters go on.");
        }
        if (!followOnSaid.isEmpty()) {
            review.put("follow_on_filters", followOnSaid);
            review.put("hint", review.get("hint") + " Say the follow_on_filters too: each promoted pipeline "
                               + "reading the earlier version's Events gains a new filter, disabled, reading the "
                               + "new version's; its own filters are left as they are.");
        }
        if (!followed.passedOver().isEmpty()) {
            review.put("follow_on_filters_passed_over", followed.passedOver());
            review.put("hint", review.get("hint") + " Say the follow_on_filters_passed_over too: no filter is "
                               + "made where it would index the new Events into an index a promoted pipeline "
                               + "indexes them into already.");
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
        if (!changedSaid.isEmpty()) {
            review.put("changed_in_production", changedSaid);
            review.put("hint", review.get("hint") + " Say the changed_in_production too: each is overwritten "
                               + "by its working copy, its production version kept as a backup.");
        }
        final List<String> notPromoted = notUsed(plan);
        if (!notPromoted.isEmpty()) {
            review.put("not_promoted", notPromoted);
            review.put("hint", review.get("hint") + " Say the not_promoted too: nothing the build promotes "
                               + "uses them, so they stay in the build as they are (a folder given by a "
                               + "document's uuid promotes it all the same).");
        }
        if (!tidy.items().isEmpty()) {
            review.put("tidy_up", tidy.said());
            review.put("hint", review.get("hint") + " Say the tidy_up too: once promoted, the build's test data "
                               + "listed is tidied up, unless the user unticks it in the approval.");
        }
        // Left out after the layout was shown in this chat and confirmed there: the latest shown's id, where
        // nothing it showed has changed since (seen in chat 192: the user said "confirmed", and the agent searched
        // the chat's history for the review_id).
        final String shown = PromotionReview.shownDigest(review);
        final Map<String, String> shownBefore = reviewed == null
                ? UnsavedChoices.recalled(context, PromotionReview.shownTarget(build), LAYOUT_SHOWN)
                : Map.of();
        final boolean shownUnchanged = shownBefore.get(REVIEW_ID) != null
                                       && Consent.wasReviewed(shownBefore.get(REVIEW_ID), bound)
                                       && shown.equals(shownBefore.get(SHOWN));
        if (!shownUnchanged && !Consent.wasReviewed(reviewed, bound)) {
            UnsavedChoices.remember(context, PromotionReview.shownTarget(build), LAYOUT_SHOWN, Map.of(REVIEW_ID,
                    Consent.reviewId(bound), SHOWN, shown));
            if (shownBefore.get(REVIEW_ID) != null) {
                review.put("hint", "What was shown before (review_id '" + shownBefore.get(REVIEW_ID) + "') has "
                                   + "changed since: show this. " + review.get("hint"));
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
        if (!changedSaid.isEmpty()) {
            // In full: what the user agrees to lose from production but for its backup.
            shortForm.put("changed in production since copied", new Consent.Listed(changedSaid));
        }
        if (!roots.isEmpty()) {
            final List<String> where = new ArrayList<>();
            roots.forEach(root -> where.add(root.role() + ": " + root.folder()));
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
        if (!volumes.isEmpty()) {
            shortForm.put("streams the new filters select", volumes);
        }
        if (!switchOverSaid.isEmpty()) {
            shortForm.put("switch-over (yours, after promotion)", switchOverSaid);
        }
        if (!keptSaid.isEmpty()) {
            shortForm.put("earlier versions keep", keptSaid);
        }
        if (!followOnSaid.isEmpty()) {
            shortForm.put("follow-on filters", followOnSaid);
        }
        if (!followed.passedOver().isEmpty()) {
            shortForm.put("follow-on filters not made", followed.passedOver());
        }
        if (!notPromoted.isEmpty()) {
            shortForm.put("not promoted (nothing uses them)", notPromoted);
        }
        // A new version replacing a promoted one: how much its filter reads is asked each time (decided by the
        // user, 2026-11-24).
        final List<String> replacing = processing.stream()
                .map(entry -> entry.pipeline().getUuid())
                .filter(predecessors::containsKey)
                .distinct()
                .toList();
        final PromotionReview.Backfill backfill = replacing.isEmpty()
                ? null
                : layoutReview.backfill(call, context, build);
        if (backfill != null) {
            shortForm.put("new version's filter reads", backfill.said());
        }
        // The tidy-up, a choice in the form, ticked to start: the user unticks it to keep the test data.
        try {
            consent.require(call, context, tidy.offeredIn(Consent.Request.approval(NAME,
                    "Promote build '" + build + "' as shown in the chat", tidy.inForm(shortForm)).boundTo(bound)));
        } catch (final Consent.SentInstead e) {
            // Seen in chat 194: the user said where a role's documents should go instead of approving, and the
            // agent's correction ({'Feed': <the feed's folder>}) changed nothing: it is told what does.
            if (rolesHint == null) {
                throw e;
            }
            throw new ToolRefusal(e.getMessage() + " " + rolesHint);
        }
        final boolean tidying = !tidy.items().isEmpty() && consent.option(context, PromotionTidyUp.TIDY_UP, true);
        final List<String> done = new ArrayList<>();
        if (groupsProvider != null) {
            // An aggregated feed's modules import the originals their working copies are written back over.
            try {
                done.addAll(groupsProvider.get().toOriginals(context, build, docs));
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
        // What was kept is marked so, with its version, so it doesn't hold the build open on its feed until it
        // changes.
        plan.stream()
                .filter(p -> PromotionLayout.KEEP.equals(p.action()))
                .forEach(p -> {
                    final DocRef kept = ref(p.doc());
                    final Optional<String> version = BuildRecords.version(documentAccess, kept);
                    buildRecords.update(build, kept, entry -> {
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
        // Streams, then shards, then documents: never a reason for the promotion, done now, to fail.
        final List<Map<String, Object>> tidied = tidying
                ? tidyUp.apply(build, tidy)
                : List.of();
        if (guard.removeBuildFolderIfEmpty(build)) {
            done.add("removed the build's workspace folder, now empty");
        }
        // What was moved: a production index is never put up for dropping.
        final Set<String> moved = plan.stream()
                .filter(p -> PromotionLayout.MOVE.equals(p.action()))
                .map(p -> String.valueOf(p.doc().get("uuid")))
                .collect(Collectors.toSet());
        final Map<String, Long> since = new LinkedHashMap<>();
        if (backfill != null && !backfill.everything()) {
            final long from = backfill.fromMs() == null
                    ? System.currentTimeMillis()
                    : backfill.fromMs();
            replacing.forEach(uuid -> since.put(uuid, from));
        }
        final List<Map<String, Object>> filters = promotionProcessing.createPromotionFilters(context.links(),
                processing, moved, since);
        if (switchOverProvider != null) {
            switchOverProvider.get().handOver(context.links(), overlaps, filters);
        }
        final List<Map<String, Object>> followOnFilters = followOns.isEmpty()
                ? List.of()
                : promotionProcessing.createFollowOnFilters(context.links(), followOns, filters);
        final Map<String, Object> result = promoted(context, build, plan, folders, tree, done, warnings, filters,
                followOnFilters, keeps, !predecessors.isEmpty());
        final List<String> selection = PromotedFeeds.remember(context, build, processing, filters,
                switchOverProvider == null
                        ? null
                        : switchOverProvider.get());
        if (!selection.isEmpty()) {
            result.put(PromotedFeeds.FEED_SELECTION, selection);
        }
        tidy.report(result, tidying, tidied);
        if (backfill != null) {
            result.put("new_version_reads", backfill.said() + ". Its filter is made disabled, as every filter "
                                            + "promotion makes: say so to the user.");
        }
        if (shownUnchanged) {
            result.put("reviewed", "Taken as the layout shown in this chat (review_id '"
                                   + shownBefore.get(REVIEW_ID) + "'), unchanged since: reviewed was left out.");
        }
        if (!followed.passedOver().isEmpty()) {
            result.put("follow_on_filters_passed_over", followed.passedOver());
        }
        return result;
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

    // The build's documents nothing it promotes uses (PromotionUses): they stay in the build. An aggregated feed's
    // modules are always promoted.
    private Set<String> unused(final List<Map<String, Object>> docs, final Map<String, ModuleChecks.Marker> markers) {
        return new PromotionUses(documentAccess).unused(docs, markers.keySet());
    }

    // The documents left in the build because nothing promoted uses them, as the approval lists them.
    private static List<String> notUsed(final List<PromotionLayout.Step> plan) {
        return plan.stream()
                .filter(step -> PromotionLayout.KEEP.equals(step.action())
                                && PromotionLayout.NOT_USED.equals(step.target()))
                .map(step -> step.doc().get("name") + " (" + step.doc().get("type") + ")")
                .toList();
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
            if (!PipelineDoc.TYPE.equals(type) && stepWrites.unchangedCopy(doc, String.valueOf(copyOf))) {
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

    // The new events (or records) versions promoted, each with its predecessor: their follow-on pipelines read the
    // predecessor's Events.
    private Map<DocRef, DocRef> eventsVersions(final List<PromotionLayout.Step> plan,
                                               final Map<String, String> predecessors) {
        final Map<DocRef, DocRef> out = new LinkedHashMap<>();
        for (final PromotionLayout.Step step : plan) {
            final String uuid = String.valueOf(step.doc().get("uuid"));
            if (PromotionLayout.MOVE.equals(step.action()) && PipelineDoc.TYPE.equals(step.doc().get("type"))
                && PromotionRoleRoots.isRole(WriteGuard.SOURCE_ROLES, step.role()) && predecessors.containsKey(uuid)) {
                places.find(predecessors.get(uuid)).ifPresent(before -> out.put(ref(step.doc()),
                        new DocRef(PipelineDoc.TYPE, before.getUuid(), before.getName())));
            }
        }
        return out;
    }

    // What each earlier version keeps once its new version takes the build's feeds, by its name: its filters selecting
    // feeds outside the build (or feeds that can't be listed), never advised to be disabled or removed; none listed
    // for one that keeps nothing. No earlier version given without the switch-over (tests that make the tool
    // themselves), or where its filters can't be read.
    private Map<String, List<String>> keeps(final Map<String, String> predecessors,
                                            final List<PromotionProcessing.Entry> processing) {
        final Map<String, List<String>> out = new LinkedHashMap<>();
        if (switchOverProvider == null || predecessors.isEmpty()) {
            return out;
        }
        final Set<String> moving = new LinkedHashSet<>();
        processing.forEach(entry -> moving.addAll(entry.feeds()));
        for (final String predecessor : new LinkedHashSet<>(predecessors.values())) {
            try {
                places.find(predecessor).ifPresent(found -> {
                    final List<String> kept = switchOverProvider.get().keeps(new DocRef(PipelineDoc.TYPE,
                            found.getUuid(), found.getName()), moving);
                    out.put(found.getName(), kept);
                });
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> LogUtil.message("No filters of the earlier version {}: {}", predecessor,
                        e.getMessage()));
            }
        }
        return out;
    }

    // How much each new filter's feeds hold, as the review and the approval say it; none without the counts (tests
    // that make the tool themselves).
    private List<String> volumesSaid(final List<PromotionProcessing.Entry> processing) {
        if (feedVolumesProvider == null) {
            return List.of();
        }
        final List<String> out = new ArrayList<>();
        final Set<String> said = new LinkedHashSet<>();
        for (final PromotionProcessing.Entry entry : processing) {
            for (final String feed : entry.feeds()) {
                if (said.add(feed.toLowerCase(Locale.ROOT) + "|" + entry.streamType())) {
                    out.add(feedVolumesProvider.get().volume(feed, entry.streamType()).said());
                }
            }
        }
        return out;
    }

    /// What the user does once the build is promoted (decided by the user, 2026-11-17, and for feeds old filters
    /// select, 2026-11-25): for each new filter whose feeds old filters select, the switch-over in order, each step
    /// linked (the tools change no old filter or dictionary); enable the other new filters; and of the earlier
    /// versions, what stays (a filter serving feeds outside the build, a follow-on reading the Events of an earlier
    /// version that still translates some): never advised to be disabled or removed. A follow-on reading only the
    /// Events of an earlier version that translates nothing once switched over may be disabled by the admin, once
    /// the new version's output is right.
    ///
    /// @param filters         What promotion made for the build's pipelines.
    /// @param followOnFilters What promotion made for the pipelines reading an earlier version's Events.
    /// @param keeps           What each earlier version keeps, by its name (none listed: it keeps nothing; not given:
    ///                        not known, and nothing is said of what reads its Events).
    /// @param newVersion      True if the build promotes a new version of a promoted pipeline.
    /// @return The steps, numbered.
    static List<String> nextSteps(final List<Map<String, Object>> filters,
                                  final List<Map<String, Object>> followOnFilters,
                                  final Map<String, List<String>> keeps,
                                  final boolean newVersion) {
        final List<String> steps = new ArrayList<>();
        for (final Map<String, Object> filter : filters) {
            if (filter.get("switch_over") instanceof final List<?> switchOver && !switchOver.isEmpty()) {
                final List<String> ordered = new ArrayList<>();
                for (int i = 0; i < switchOver.size(); i++) {
                    ordered.add("(" + (char) ('a' + i) + ") " + switchOver.get(i));
                }
                steps.add((steps.size() + 1) + ". The new filter " + filter.get("filter_id") + " ('"
                          + filter.get("pipeline") + "') has been drafted and is disabled: old filters already select "
                          + "its feeds, so the switch-over is the user's (the tools change no old filter or "
                          + "dictionary; in-flight tasks make it unsafe), in this order: " + String.join(" ", ordered)
                          + ".");
            }
        }
        final List<String> enable = new ArrayList<>();
        filters.stream()
                .filter(filter -> !Boolean.TRUE.equals(filter.get("enabled")) && filter.get("switch_over") == null)
                .forEach(filter -> enable.add("filter " + filter.get("filter_id") + " ('" + filter.get("pipeline")
                                              + "')"));
        followOnFilters.stream()
                .filter(filter -> !Boolean.TRUE.equals(filter.get("enabled")))
                .forEach(filter -> enable.add("filter " + filter.get("filter_id") + " ('" + filter.get("pipeline")
                                              + "', reading the Events of '" + filter.get("reads") + "')"));
        final boolean switching = !steps.isEmpty();
        if (!enable.isEmpty() && (switching || newVersion)) {
            steps.add((steps.size() + 1) + ". Enable " + (switching
                    ? "the other new filters"
                    : "the new version's filters") + " when ready: " + String.join("; ", enable) + ".");
        }
        keeps.forEach((name, kept) -> {
            if (!kept.isEmpty()) {
                steps.add((steps.size() + 1) + ". '" + name + "' stays for the feeds this build doesn't move: "
                          + String.join("; ", kept) + ". Never disable or remove it while it serves them.");
            }
        });
        final List<String> following = new ArrayList<>();
        final List<String> retiring = new ArrayList<>();
        for (final Map<String, Object> filter : followOnFilters) {
            final String said = "'" + filter.get("pipeline") + "': filter " + filter.get("copied_from")
                                + " (reading the Events of '" + filter.get("in_place_of") + "')";
            final List<String> kept = keeps.get(String.valueOf(filter.get("in_place_of")));
            if (kept != null && kept.isEmpty()) {
                retiring.add(said);
            } else if (kept != null) {
                following.add(said);
            }
        }
        if (!following.isEmpty()) {
            steps.add((steps.size() + 1) + ". These stay too, as the earlier version they read still translates "
                      + "feeds: " + String.join("; ", following) + ".");
        }
        if (!retiring.isEmpty()) {
            steps.add((steps.size() + 1) + ". Once the new version's output is right, the admin may disable these "
                      + "(the tools never do): they read the Events of an earlier version that translates no feed "
                      + "once switched over: " + String.join("; ", retiring) + ".");
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
            } else if (copyOf != null && stepWrites.unchangedCopy(doc, String.valueOf(copyOf))) {
                // Seen in an eval: an unchanged working copy XSLT was listed as "updated from its working copy" in
                // the layout and updated_in_place, while the promotion discarded it. Decided here, so both agree.
                plan.add(new PromotionLayout.Step(doc, PromotionLayout.DISCARD, PromotionWrites.UNCHANGED_COPY, null));
            } else if (copyOf != null) {
                plan.add(new PromotionLayout.Step(doc, PromotionLayout.WRITE_BACK, String.valueOf(copyOf), null));
            } else if (given.testFeeds().contains(uuid)) {
                // Seen in chat 177: the build's test feed was in the layout, and the agent asked the user whether to
                // promote it. It holds only the sample's Events: never promoted, whatever the destinations say. A
                // test index, likewise, holds only the sample's documents.
                plan.add(new PromotionLayout.Step(doc, PromotionLayout.KEEP, FeedDoc.TYPE.equals(type)
                        ? TEST_FEED_KEPT
                        : DashboardDoc.TYPE.equals(type)
                                ? TEST_DASHBOARD_KEPT
                                : TEST_INDEX_KEPT, null));
            } else if (!destinations.containsKey(uuid) && given.unused().contains(uuid)) {
                // Seen in a user test (2026-11-18): an older Data Splitter the build had replaced was promoted with
                // the rest. A folder given for it by its uuid promotes it all the same.
                plan.add(new PromotionLayout.Step(doc, PromotionLayout.KEEP, PromotionLayout.NOT_USED, null));
            } else if (!destinations.containsKey(uuid) && KEEP.equals(besideFeeds.get(uuid))) {
                plan.add(new PromotionLayout.Step(doc, PromotionLayout.KEEP, "left in the workspace: a survey doc "
                                                                             + "stays in its build", null));
            } else if (!destinations.containsKey(uuid) && besideFeeds.containsKey(uuid)) {
                String target = String.join("/", WriteGuard.folderParts(besideFeeds.get(uuid)));
                // The group's own output feed: in the group's folder beside the feed. A module XSLT is laid out under
                // its role: a shared module's (Common, Helpers) in the feed's folder, a group's with its pipeline in
                // the group's (asked for by the user: seen placed at the feed's folder, beside the common module and
                // helpers, apart from its pipeline).
                final String role = PromotionRoleRoots.moduleRole(doc, markers.get(uuid));
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
                        : firstGiven(roleLocations.roleOf(doc), PromotionRoleRoots.moduleRole(doc, markers.get(uuid)));
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

    // The build's test indexes, each with the production index doc's UUID it stands in for; none without the tool.
    private Map<String, String> testIndexes(final String build) {
        return testIndexesProvider == null
                ? Map.of()
                : testIndexesProvider.get().testIndexes(build);
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
                    .filter(d -> INDEXING_STAGES.contains(NullSafe.string(roleLocations.stageOrNone(
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
            if (!e.feeds().isEmpty()) {
                said.add(e.pipeline().getName() + ": " + (e.feeds().size() == 1
                        ? "feed "
                        : "feeds ") + String.join(", ", e.feeds()) + " (" + e.streamType() + ")");
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
            // What stays in the build as nothing uses it is left as it is.
            final Set<String> notUsed = new LinkedHashSet<>();
            plan.stream()
                    .filter(step -> PromotionLayout.NOT_USED.equals(step.target()))
                    .forEach(step -> notUsed.add(String.valueOf(step.doc().get("uuid"))));
            final List<String> versioned = stepWrites.consolidateVersions(docs.stream()
                    .filter(doc -> !notUsed.contains(String.valueOf(doc.get("uuid"))))
                    .toList());
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
                done.add(stepWrites.carryOut(step, folders));
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
                                         final Map<String, List<String>> keeps,
                                         final boolean newVersion) {
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
        // Content the user is to see as it is (the layout as promoted, what the tidy-up did), with no id to send
        // back: Stroom AI's chat gives its model such a result whole up to a higher limit than other results (seen
        // in chat 188: a result of 9,856 characters stubbed without its layout or tidied_up, read from the saved
        // result twice).
        result.put("status", NEEDS_SHOWING);
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
        for (final Map<String, Object> filter : Stream.concat(filters.stream(), followOnFilters.stream()).toList()) {
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
        final List<String> nextSteps = nextSteps(filters, followOnFilters, keeps, newVersion);
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
                               + "where old filters already select a new filter's feeds (its switch_over), the "
                               + "switch-over is the user's, in its order, each step linked; changing, disabling or "
                               + "removing any old filter or dictionary is the user's or the admin's, never the "
                               + "tools'; what the earlier version keeps stays."
                               + (filters.stream().anyMatch(f -> f.get("before_enabling") != null)
                    ? " Give the user each filter's before_enabling as it is: an index the build made is emptied or "
                      + "dropped first, as it says (left as it is, it holds the build's events twice); a production "
                      + "index never is, and it says what the user decides instead."
                    : ""));
        }
        return result;
    }

    private static Map<String, String> destinations(final ToolCall call) {
        return objectArg(call, DESTINATIONS_ARG, "{type or document uuid: folder}");
    }

    // An argument that is an object of strings (or its JSON text), as given; empty when it isn't given.
    private static Map<String, String> objectArg(final ToolCall call, final String name, final String shape) {
        Object value = argsMap(call).get(name);
        if (value instanceof final String text && !text.isBlank()) {
            try {
                value = ToolJson.loads(text.strip());
            } catch (final RuntimeException e) {
                throw new ToolRefusal(name + " is an object, " + shape);
            }
        }
        final Map<String, String> out = new LinkedHashMap<>();
        if (value instanceof final Map<?, ?> map) {
            map.forEach((key, given) -> {
                if (given != null) {
                    out.put(String.valueOf(key), String.valueOf(given));
                }
            });
        } else if (value != null && !(value instanceof String)) {
            throw new ToolRefusal(name + " is an object, " + shape);
        }
        return out;
    }

    private static List<String> moveTargets(final List<PromotionLayout.Step> plan) {
        return plan.stream()
                .filter(p -> PromotionLayout.MOVE.equals(p.action()) && p.target() != null)
                .map(PromotionLayout.Step::target)
                .toList();
    }

    /// The build's documents of a type.
    ///
    /// @param docs The build's documents.
    /// @param type The type.
    /// @return Those of the type.
    static List<Map<String, Object>> ofType(final List<Map<String, Object>> docs, final String type) {
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

    /// A document as the build lists it, as a doc ref.
    ///
    /// @param doc The document.
    /// @return Its doc ref.
    static DocRef ref(final Map<String, Object> doc) {
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


    /// The source a build's documents are of.
    ///
    /// @param name The source feed's name.
    /// @param own  The folder of the source's own its documents go in, normalised.
    record Source(String name, String own) {

    }

    /// What decides where a build's documents go, beside the destinations.
    ///
    /// @param testFeeds The build's test feeds, by UUID: never promoted.
    /// @param source    The source, or null.
    /// @param folders   Tells a folder of the source's own from a parent of sources.
    /// @param group     An aggregated feed's build's source type group, or null.
    /// @param sources   The feeds the build works from.
    /// @param unused    The build's documents nothing it promotes uses, by UUID: left in the build.
    record Placed(Set<String> testFeeds, Source source, SourceFolders folders, GroupPlace group,
                          List<String> sources, Set<String> unused) {

    }

    /// An aggregated feed's build's source type group, whose documents go in a folder of its own in the feed's.
    ///
    /// @param name The group's name: its folder's.
    /// @param docs The documents of its own placed beside the feed (its XSLT, its output feed), by UUID.
    record GroupPlace(String name, Set<String> docs) {

    }

}
