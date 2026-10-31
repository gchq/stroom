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
import stroom.ai.impl.tools.sample.PyJson;
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

/// `promote_build` - promotes a build out of the workspace once the user has seen where everything goes and
/// approved it (the Stroom MCP server's `tools/builds.py` `promote_build`, with `consolidate_versions`). New
/// documents are moved to their destination folders, working copies written back over their originals after a
/// backup, and feed-wide processor filters made, disabled, for the promoted pipelines: all through Stroom's
/// explorer and stores as the user, once they approve the layout in a short form bound to it.
class PromoteBuildTool extends AbstractBuiltInTool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PromoteBuildTool.class);

    static final String NAME = "promote_build";
    static final String BUILD_ARG = "build";
    static final String DESTINATIONS_ARG = "destinations";
    static final String REVIEWED_ARG = "reviewed";
    static final String ROLE_SUBFOLDERS_ARG = "role_subfolders";
    /// What a working copy's write-back takes from it.
    static final List<String> WRITTEN_BACK = List.of("data", "converterType", "documentation", "description");

    private static final String KEEP = "keep";
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
    private final IndexingRuns indexingRuns;
    private final OutputFeeds outputFeeds;
    private final DocumentWrites writes;
    private final DocumentAccess documentAccess;
    private final XsltHistory history;

    @Inject
    PromoteBuildTool(final BuildFacts facts,
                     final PromotionLayout layout,
                     final DocumentPlaces places,
                     final WriteGuard guard,
                     final BuildRecords buildRecords,
                     final Consent consent,
                     final PromotionProcessing promotionProcessing,
                     final ProcessorFilterWrites filterWrites,
                     final IndexingRuns indexingRuns,
                     final OutputFeeds outputFeeds,
                     final DocumentWrites writes,
                     final DocumentAccess documentAccess,
                     final XsltHistory history) {
        this.facts = facts;
        this.layout = layout;
        this.places = places;
        this.guard = guard;
        this.buildRecords = buildRecords;
        this.consent = consent;
        this.promotionProcessing = promotionProcessing;
        this.filterWrites = filterWrites;
        this.indexingRuns = indexingRuns;
        this.outputFeeds = outputFeeds;
        this.writes = writes;
        this.documentAccess = documentAccess;
        this.history = history;
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
                             + "together. New documents are moved to their destination folders (UUIDs are kept, so "
                             + "filters and references keep working); a new version of a production pipeline "
                             + "(copy_pipeline) goes in a version subfolder beside its predecessor, with its index, "
                             + "documentation and dashboard; destination folders that don't exist yet are created "
                             + "first. A working copy is written back over its original after the original is "
                             + "backed up to <workspace>/backups, then the copy is deleted. The build's workspace "
                             + "folder is removed afterwards if nothing is left in it. What build_status says is "
                             + "missing goes in the approval, except documentation the build's own change left "
                             + "behind: write_documentation again first (once promoted, documentation only changes "
                             + "through a working copy).")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(BUILD_ARG, "Build name, e.g. 'acme-door-v1.3'.")
                        .addProperty(DESTINATIONS_ARG, TranslationArgs.object(
                                "Destination folder per document type or per document UUID, e.g. {'Feed': "
                                + "'/Feeds/Events/Acme', 'Pipeline': '/Feeds/Events/Acme', 'XSLT': ...}: an "
                                + "explorer path, with or without the root System ('System / Feeds / Events' works "
                                + "too). Working copies ignore this: they are written back over their originals. A "
                                + "new version of a production document (copy_pipeline) goes in a version subfolder "
                                + "beside it unless given here. 'keep' for a document UUID leaves it in the build's "
                                + "workspace folder, not promoted (a test feed made while fixing, say). Documents "
                                + "with no destination are named all at once."))
                        .addStringProperty(REVIEWED_ARG, "The review_id of the needs_review reply, once the user has "
                                                         + "been shown its layout in the chat and confirmed it: they "
                                                         + "then approve it in a short form. Not true: a layout "
                                                         + "that changes (other destinations) gets a new id, to "
                                                         + "show again.")
                        .addBooleanProperty(ROLE_SUBFOLDERS_ARG, "Keep the build's role subfolders (Events or "
                                                                 + "Records, Indexing, CEF, Reference) under each "
                                                                 + "destination, as the layout shows them by "
                                                                 + "default; false puts each document straight in "
                                                                 + "its destination folder. The user decides when "
                                                                 + "they review the layout.")
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
        final String roleArg = stringArg(call, ROLE_SUBFOLDERS_ARG);
        final boolean roleSubfolders = roleArg == null || Boolean.parseBoolean(roleArg);
        final List<Map<String, Object>> docs = facts.buildDocs(build);
        if (docs.isEmpty()) {
            throw new ToolRefusal("Build '" + build + "' has no documents");
        }
        final PromotionLayout.VersionFolders versions = layout.versionFolders(docs, destinations);
        final Map<String, String> joins = PromotionLayout.joining(versions.beside());
        // A build on data already in Stroom: its own feeds are test feeds, which stay in the workspace, and its
        // pipelines' filters name the feeds it works from (seen in VS Code: the promoted filter selected the test
        // feed).
        final List<String> sources = facts.sourceFeeds(build, docs);
        final List<PromotionLayout.Step> plan = plan(docs, destinations, versions.proposed(), joins, sources);
        // The build's role subfolders kept in production (asked for by the user), unless they say otherwise: where
        // they tell stages apart, so only when it promotes documents of more than one role (an index documented,
        // or a new indexing version, is all Indexing: a subfolder there only adds a level).
        final Set<Object> rolesMoved = new LinkedHashSet<>();
        plan.stream()
                .filter(p -> PromotionLayout.MOVE.equals(p.action()) && p.doc().get("role") != null)
                .forEach(p -> rolesMoved.add(p.doc().get("role")));
        if (roleSubfolders && rolesMoved.size() > 1) {
            plan.replaceAll(p -> p.role() != null
                    ? p.withTarget(p.target() + "/" + p.role())
                    : p);
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
        // New versions' predecessors, read before promotion clears the build's record: their filters may be
        // disabled once the new version takes over (the user's to decide).
        final Map<String, String> predecessors = predecessors(build, moving);
        final List<PromotionProcessing.Entry> processing = promotionProcessing.promotionProcessing(moving, sources,
                keptFeeds);
        final List<String> warnings = facts.buildChecks(docs);
        final List<String> blocking = warnings.stream().filter(w -> w.endsWith(BuildChecks.BLOCKS)).toList();
        if (!blocking.isEmpty()) {
            throw new ToolRefusal("Not promoted: " + String.join(" ", blocking) + ". Do it in the build first, then "
                                  + "promote_build again: once promoted, the pipeline's documentation can only "
                                  + "change through a working copy.");
        }
        final List<String> processed = processingSaid(processing);
        final String tree = layout.promotionTree(plan, creating);
        // The question applies: documents of several roles.
        final boolean roles = rolesMoved.size() > 1;
        if (!Consent.wasReviewed(reviewed, tree)) {
            return review(build, tree, warnings, processed, roles, reviewed);
        }
        // The form stays short; the approval is bound to the exact layout shown, through its digest.
        final long promoting = plan.stream()
                .filter(p -> PromotionLayout.MOVE.equals(p.action()) || PromotionLayout.WRITE_BACK.equals(p.action()))
                .count();
        final Map<String, Object> shortForm = new LinkedHashMap<>();
        shortForm.put("build", build);
        shortForm.put("layout", promoting + " document(s), as shown in the chat (" + Consent.reviewId(tree) + ")");
        shortForm.put("folders", new ArrayList<>(new TreeSet<>(moveTargets(plan))));
        if (roles) {
            shortForm.put("role subfolders", roleSubfolders
                    ? "kept under each destination"
                    : "not kept");
        }
        if (!warnings.isEmpty()) {
            shortForm.put("warnings", warnings);
        }
        if (!processing.isEmpty()) {
            shortForm.put("processing after promotion", processed);
        }
        consent.require(call, context, Consent.Request.approval(NAME,
                "Promote build '" + build + "' as shown in the chat", shortForm));
        final List<String> done = new ArrayList<>();
        carryOut(build, docs, plan, folders, creating, done);
        // What the record kept about the promoted docs only meant something inside the build; it goes once nothing
        // is left in the build.
        buildRecords.forget(build, plan.stream()
                .filter(p -> !PromotionLayout.KEEP.equals(p.action()))
                .map(p -> String.valueOf(p.doc().get("uuid")))
                .toList());
        if (guard.removeBuildFolderIfEmpty(build)) {
            done.add("removed the build's workspace folder, now empty");
        }
        final List<Map<String, Object>> filters = promotionProcessing.createPromotionFilters(context.links(),
                processing);
        return promoted(context, build, plan, folders, tree, done, warnings, filters, predecessors);
    }

    // Where each document goes.
    private List<PromotionLayout.Step> plan(final List<Map<String, Object>> docs,
                                            final Map<String, String> destinations,
                                            final Map<String, String> proposed,
                                            final Map<String, String> joins,
                                            final List<String> sources) {
        final List<PromotionLayout.Step> plan = new ArrayList<>();
        // Every document with no destination, said at once.
        final List<Map<String, Object>> unplaced = new ArrayList<>();
        for (final Map<String, Object> doc : docs) {
            final String uuid = String.valueOf(doc.get("uuid"));
            final String type = String.valueOf(doc.get("type"));
            final Object copyOf = doc.get("working_copy_of");
            if (copyOf != null && PipelineDoc.TYPE.equals(type)) {
                // The copied pipeline only existed to step the working copies; the original keeps running.
                plan.add(new PromotionLayout.Step(doc, PromotionLayout.DISCARD, "deleted after write-back", null));
            } else if (copyOf != null) {
                plan.add(new PromotionLayout.Step(doc, PromotionLayout.WRITE_BACK, String.valueOf(copyOf), null));
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
                final Placing placing = target(doc, docs, destinations, proposed);
                if (placing.target() == null) {
                    unplaced.add(doc);
                    continue;
                }
                String target = String.join("/", WriteGuard.folderParts(placing.target()));
                if (!destinations.containsKey(uuid) && !FeedDoc.TYPE.equals(type) && joins.containsKey(target)) {
                    // With the version it belongs to.
                    target = joins.get(target);
                }
                plan.add(new PromotionLayout.Step(doc, PromotionLayout.MOVE, target,
                        !destinations.containsKey(uuid) && !placing.besideProduction()
                                ? (String) doc.get("role")
                                : null));
            }
        }
        if (!unplaced.isEmpty()) {
            // Seen (Delinea SecretServer): raised one document at a time, the Dashboard's, then the TextConverter's,
            // a failed call each.
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
                    : ". The others have one: " + String.join(", ", placed)) + ". Nothing was changed.");
        }
        return plan;
    }

    // The destination a new document goes to, and whether it is beside a production document.
    private Placing target(final Map<String, Object> doc,
                           final List<Map<String, Object>> docs,
                           final Map<String, String> destinations,
                           final Map<String, String> proposed) {
        final String uuid = String.valueOf(doc.get("uuid"));
        final String type = String.valueOf(doc.get("type"));
        final String name = String.valueOf(doc.get("name"));
        String target = firstGiven(destinations.get(uuid), proposed.get(uuid), destinations.get(type));
        if (target == null && DocumentationDoc.TYPE.equals(type)
            && (name.endsWith(" source notes") || name.contains(" reference - "))) {
            // The source's notes and reference documents: beside the feed, where later work on it looks.
            final List<Map<String, Object>> feeds = ofType(docs, FeedDoc.TYPE);
            target = feeds.size() == 1
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
                            proposed.get(String.valueOf(same.get("uuid"))), destinations.get(PipelineDoc.TYPE));
        }
        if (target == null && DashboardDoc.TYPE.equals(type)) {
            target = dashboardTarget(doc, docs, destinations, proposed);
        }
        boolean besideProduction = false;
        if (target == null && DocumentationDoc.TYPE.equals(type)) {
            // The documentation of a production pipeline (an in-place change's) or an existing index: beside it.
            // An evaluation report goes beside the pipeline it evaluates.
            final String named = name.endsWith(WriteDocumentationTool.EVALUATION)
                    ? name.substring(0, name.length() - WriteDocumentationTool.EVALUATION.length())
                    : name;
            final List<DocRef> outside = new ArrayList<>();
            for (final String kind : List.of(PipelineDoc.TYPE, ElasticIndexDoc.TYPE, LuceneIndexDoc.TYPE)) {
                places.named(kind, named).stream()
                        .filter(found -> !BuildDocumentation.inBuild(docs, found.getUuid()))
                        .forEach(outside::add);
            }
            target = outside.size() == 1
                    ? places.folderOf(outside.getFirst()).orElse(null)
                    : null;
            besideProduction = target != null;
        }
        return new Placing(target, besideProduction);
    }

    // A verification dashboard: wherever the index it searches goes (seen in VS Code: promotion stopped for want
    // of its destination), else beside the pipelines.
    private String dashboardTarget(final Map<String, Object> doc,
                                   final List<Map<String, Object>> docs,
                                   final Map<String, String> destinations,
                                   final Map<String, String> proposed) {
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
                target = firstGiven(destinations.get(feedingUuid), proposed.get(feedingUuid));
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

    // `_stage_or_none`: a pipeline's stage, or null for one Stroom can't load (a choice then made without it, not a
    // failure).
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
                                              final boolean roles,
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
        result.put("hint", "Show the user the layout in the chat exactly as it is (a code block), with any warnings "
                           + "and the processing after promotion, and ask them to confirm it or say what to change "
                           + "(a folder: destinations by type or document uuid)"
                           + (roles
                ? "; and whether to keep the build's role subfolders under each destination, as shown "
                  + "(role_subfolders=false puts each document straight in its folder: show the tree again)"
                : "")
                           + ". Once they confirm it, promote_build again with the same destinations"
                           + (roles
                ? " and role_subfolders"
                : "") + " and reviewed='" + Consent.reviewId(tree) + "' (this layout's review_id): they approve it "
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
            ofType(docs, FeedDoc.TYPE).forEach(feed -> buildFeeds.add(String.valueOf(feed.get("uuid"))));
            for (final PromotionLayout.Step step : plan) {
                final String name = String.valueOf(step.doc().get("name"));
                doing = "restoring production settings on '" + name + "'";
                if (PromotionLayout.MOVE.equals(step.action()) && PipelineDoc.TYPE.equals(step.doc().get("type"))) {
                    final String uuid = String.valueOf(step.doc().get("uuid"));
                    if (indexingRuns.developmentBatch(uuid, false)) {
                        done.add("restored the default batch size on '" + name + "'");
                    }
                    if (outputFeeds.restoreOutputFeed(uuid, buildFeeds)) {
                        // Its Events went to the build's test feed (an existing feed's streams, processed in the
                        // build): in production they go into the input's feed.
                        done.add("'" + name + "' writes its Events into its input's feed again (the build's test "
                                 + "feed was for the build only)");
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
                yield "deleted working-copy pipeline '" + ref.getName() + "'";
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

    /// `consolidate_versions`: a build's pending changes, as one new line of each XSLT's version history and one
    /// new row of each doc's version control: called when the build is promoted.
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
        final List<Object> buttons = new ArrayList<>();
        for (final Map<String, Object> filter : filters) {
            final Object actions = filter.remove(UserActionEntries.USER_ACTIONS);
            if (actions instanceof final List<?> list) {
                buttons.addAll(list);
            }
        }
        predecessors.values().forEach(predecessor -> buttons.addAll(disableEarlier(context, predecessor)));
        if (!buttons.isEmpty()) {
            result.put(UserActionEntries.USER_ACTIONS, buttons);
        }
        if (!filters.isEmpty()) {
            result.put("processing_filters", filters);
            result.put("next", "Tell the user each promoted pipeline has a processing filter, created disabled (one "
                               + "marked existing is an enabled filter it already had, selecting what arrives: no "
                               + "second one was made): review the pipeline (pipeline_link) and enable it on its "
                               + "Processors tab when ready. Enabled, it processes everything on its feed, existing "
                               + "data included, and what the build processed again: if an earlier version still "
                               + "processes the same feed, it processes that history again, so decide with them "
                               + "first (disable the earlier one, or limit the filter in Stroom)."
                               + (filters.stream().anyMatch(f -> f.get("before_enabling") != null)
                    ? " A filter with before_enabling indexes into an index that must start fresh: ask the user to "
                      + "have it done first, as it says (an index left as it is holds the build's events twice)."
                    : ""));
        }
        return result;
    }

    // A suggestion: never a reason for promotion to fail.
    private List<Map<String, Object>> disableEarlier(final ToolCallContext context, final String predecessor) {
        try {
            final DocRef earlier = places.find(predecessor)
                    .map(found -> new DocRef(PipelineDoc.TYPE, found.getUuid(), found.getName()))
                    .orElse(null);
            if (earlier == null) {
                return List.of();
            }
            final List<Map<String, Object>> buttons = new ArrayList<>();
            for (final ProcessorFilter filter : filterWrites.filters(earlier)) {
                if (filter.isEnabled()) {
                    buttons.add(UserActionEntries.disableFilter(context.links(), filter.getId(), earlier,
                            "the earlier version; once the new one takes over, if you choose", true));
                }
            }
            return buttons;
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message(
                    "No filters of the earlier version {}: {}",
                    predecessor,
                    e.getMessage()));
            return List.of();
        }
    }

    private static Map<String, String> destinations(final ToolCall call) {
        Object value = argsMap(call).get(DESTINATIONS_ARG);
        if (value instanceof final String text && !text.isBlank()) {
            try {
                value = PyJson.loads(text.strip());
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
                .filter(p -> PromotionLayout.MOVE.equals(p.action()))
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
}
