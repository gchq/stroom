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

import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.generation.ModuleChecks;
import stroom.ai.impl.tools.generation.SourceTypeModule;
import stroom.feed.shared.FeedDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/// Where each role of a build's documents goes at promotion: each role's root, from a destination, the user's
/// choice in a form (asked again while an answer is no folder) or a proposal, and the layout of the build's documents
/// in their role's and version's folders ([PromoteBuildTool]'s).
class PromotionRoleRoots {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PromotionRoleRoots.class);

    // The last option of where a role goes: a folder the user types.
    private static final String OTHER_FOLDER = "another folder (type it)";
    // The most times the form of where each role goes is asked in a call, while an answer is no folder.
    private static final int MAX_ROLE_ASKS = 3;

    private final BuildFacts facts;
    private final PromotionLayout layout;
    private final DocumentPlaces places;
    private final WriteGuard guard;
    private final BuildRecords buildRecords;
    private final Consent consent;
    private final DocumentAccess documentAccess;

    /// @param facts          Reads a pipeline's stage.
    /// @param layout         Lays the build's documents out in their role's and version's folders.
    /// @param places         Finds documents and production pipelines.
    /// @param guard          Resolves folders.
    /// @param buildRecords   Holds the role locations the user chose.
    /// @param consent        Asks the user where each role goes.
    /// @param documentAccess Reads the sibling feeds' folders.
    PromotionRoleRoots(final BuildFacts facts,
                       final PromotionLayout layout,
                       final DocumentPlaces places,
                       final WriteGuard guard,
                       final BuildRecords buildRecords,
                       final Consent consent,
                       final DocumentAccess documentAccess) {
        this.facts = facts;
        this.layout = layout;
        this.places = places;
        this.guard = guard;
        this.buildRecords = buildRecords;
        this.consent = consent;
        this.documentAccess = documentAccess;
    }

    /// The role a document is laid out under: its build role subfolder's, or for a pipeline at the build's top, its
    /// stage's. None for a feed (beside its source, never in a version's folder) or a working copy.
    ///
    /// @param doc The document, as the build lists it.
    /// @return Its role, or null.
    String roleOf(final Map<String, Object> doc) {
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

    /// An aggregated feed's module's role: a shared module's own (Common, Helpers: decided by the user, 2026-11-17),
    /// a group's XSLT its pipeline's; null for anything else.
    ///
    /// @param doc    The module, as the build lists it.
    /// @param marker Its mark, or null.
    /// @return Its role, or null.
    static String moduleRole(final Map<String, Object> doc, final ModuleChecks.Marker marker) {
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

    /// Each role's root, for a build whose source's folder is known (decided by the user, 2026-11-17): a destination
    /// keyed by the role, else the feed's own folder given for the feed (seen in chat 194: the user's correction,
    /// passed as `{'Feed': <the feed's folder>}`, left their earlier choice in place), else what the user chose in a
    /// form before (kept in the build's record, so the approval's layout is the one reviewed), else asked now in one
    /// form on the review's call, else the proposal: a new version's predecessor's root, else where the sibling
    /// feeds' pipelines of the role are, else the feed's own. A destination given replaces the choice recorded.
    ///
    /// @param call         The call.
    /// @param context      The call's context.
    /// @param build        The build.
    /// @param docs         The build's documents.
    /// @param plan         The layout's steps.
    /// @param destinations The destinations given.
    /// @param placed       What decides where the build's documents go.
    /// @param markers      The build's modules' marks, by UUID.
    /// @param asking       True if the user may be asked in a form now.
    /// @return Each role's root.
    List<RoleRoot> roleRoots(final ToolCall call,
                             final ToolCallContext context,
                             final String build,
                             final List<Map<String, Object>> docs,
                             final List<PromotionLayout.Step> plan,
                             final Map<String, String> destinations,
                             final PromoteBuildTool.Placed placed,
                             final Map<String, ModuleChecks.Marker> markers,
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
        final Map<String, String> proposals = new LinkedHashMap<>();
        final Map<String, String> whys = new LinkedHashMap<>();
        final Map<String, String> feedsOwn = new LinkedHashMap<>();
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
            proposals.put(role, proposed);
            whys.put(role, why);
            feedsOwn.put(role, feedRoot);
        }
        // Each option shown as the folder it gives (seen in chat 194: "System/Feeds", the folder holding the feed's,
        // read as the wrong place), as the layout puts the role's pipeline.
        final Map<String, String> proposedFolders = trialFolders(plan, proposals, placed, markers);
        final Map<String, String> ownFolders = trialFolders(plan, feedsOwn, placed, markers);
        final boolean feedsOwnGiven = feedsOwnFolderGiven(docs, destinations, placed.source());
        final Map<String, Object> recorded = buildRecords.promotionRoots(build);
        final List<RoleRoot> out = new ArrayList<>();
        final Map<String, Consent.OpenQuestion> questions = new LinkedHashMap<>();
        final Map<String, Map<String, String>> rootByLabel = new LinkedHashMap<>();
        for (final String role : roles) {
            final String proposed = proposals.get(role);
            final Map<String, String> labels = new LinkedHashMap<>();
            labels.put(optionLabel(proposedFolders.get(role), proposed, feedRoot, whys.get(role)), proposed);
            final List<String> options = new ArrayList<>(List.of(proposedFolders.get(role)));
            if (!PromotionLayout.sameFolder(proposed, feedRoot)) {
                labels.put(optionLabel(ownFolders.get(role), feedRoot, feedRoot, null), feedRoot);
                options.add(ownFolders.get(role));
            }
            // The feed's own folder given (or chosen) as a role's root is the feed's own location (seen in a user test,
            // 2026-11-18: read as a root elsewhere, it gave <feed>/<feed>/V1.0 with no role folders).
            final String given = roleKeyed(destinations, role);
            final Object chosen = recorded.get(role);
            if (given != null) {
                final String root = PromotionLayout.givenRoot(given, feedFolder);
                record(build, role, root, chosen);
                out.add(new RoleRoot(role, root, "as given", options, false, null));
            } else if (feedsOwnGiven) {
                record(build, role, feedRoot, chosen);
                out.add(new RoleRoot(role, feedRoot, "as given: the feed's own folder", options, false, null));
            } else if (chosen instanceof final String root && !root.isBlank()) {
                out.add(new RoleRoot(role, PromotionLayout.ownRoot(root, feedFolder), "as the user chose", options,
                        false, null));
            } else {
                out.add(new RoleRoot(role, proposed, whys.get(role), options, true, null));
                if (asking) {
                    final String typedTitle = role + ", if another folder: the folder to hold "
                                              + PromotionLayout.lastPart(feedFolder) + "/<version>, e.g. "
                                              + "System/Elastic Indices";
                    questions.put(role, new Consent.OpenQuestion("Where " + role + " goes",
                            new ArrayList<>(labels.keySet()), OTHER_FOLDER, typedTitle));
                    rootByLabel.put(role, labels);
                }
            }
        }
        askRoleRoots(call, context, build, feedFolder, questions, rootByLabel,
                feedKeyCoversRoles(docs, placed.source())).forEach((role, root) ->
                out.replaceAll(each -> each.role().equals(role)
                        ? new RoleRoot(role, root, "as the user chose", each.options(), false, null)
                        : each));
        return out;
    }

    // Each role's root the user chose in a form, recorded; none where the form can't be shown. An answer that is no
    // folder (seen in chat 194: "feed's location" typed, taken for a folder) is never recorded nor used: the form is
    // asked again, saying why, and after MAX_ROLE_ASKS the call is refused.
    private Map<String, String> askRoleRoots(final ToolCall call,
                                             final ToolCallContext context,
                                             final String build,
                                             final String feedFolder,
                                             final Map<String, Consent.OpenQuestion> questions,
                                             final Map<String, Map<String, String>> rootByLabel,
                                             final boolean feedKeyCovers) {
        if (questions.isEmpty()) {
            return Map.of();
        }
        final String asked = "Where the build's documents of each role go: choose a folder for each (the first is "
                             + "proposed), or '" + OTHER_FOLDER + "' and type its path.";
        String intro = asked;
        for (int n = 1; n <= MAX_ROLE_ASKS; n++) {
            final Optional<Map<String, String>> answered = consent.chooseOrTypeEach(call, context,
                    PromoteBuildTool.NAME, intro, questions);
            if (answered.isEmpty()) {
                return Map.of();
            }
            final Map<String, String> chosen = new LinkedHashMap<>();
            final List<String> problems = new ArrayList<>();
            answered.get().forEach((role, answer) -> {
                final String root = rootByLabel.get(role).get(answer);
                final String problem = root != null
                        ? null
                        : typedFolderProblem(answer);
                if (root != null) {
                    chosen.put(role, root);
                } else if (problem == null) {
                    chosen.put(role, PromotionLayout.givenRoot(answer, feedFolder));
                } else {
                    problems.add(role + ": " + problem);
                }
            });
            if (problems.isEmpty()) {
                chosen.forEach((role, root) -> buildRecords.rememberPromotionRoot(build, role, root));
                return chosen;
            }
            LOGGER.debug(() -> LogUtil.message("Role locations for build {} not folders: {}", build, problems));
            if (n == MAX_ROLE_ASKS) {
                throw new ToolRefusal("The user's answers for where the build's documents go are not folders ("
                                      + String.join("; ", problems) + "): nothing was recorded or moved. Ask the "
                                      + "user in the chat which folder each role's documents go in. "
                                      + roleKeysHint(feedFolder, questions.keySet(), feedKeyCovers));
            }
            intro = "Not a folder: " + String.join("; ", problems) + ". " + asked + " (Asked again: " + (n + 1)
                    + " of " + MAX_ROLE_ASKS + ".)";
        }
        return Map.of();
    }

    // Why what the user typed for a role's location can't be used as a folder, or null if it can: a path (with a
    // '/'), or the name of a folder in System.
    private String typedFolderProblem(final String typed) {
        if (OTHER_FOLDER.equals(typed) || NullSafe.isBlankString(typed)) {
            return "'" + OTHER_FOLDER + "' was chosen, but no folder was typed";
        }
        try {
            if (typed.contains("/") || guard.resolveFolder(typed).missing().isEmpty()) {
                // A path, or a folder in System.
                WriteGuard.folderParts(typed);
                return null;
            }
        } catch (final ToolRefusal e) {
            // No folder: said below.
        }
        return "'" + typed + "' is no folder path (e.g. System/Elastic Indices), nor a folder in System";
    }

    // A role's root recorded, replacing the one recorded before, where it differs.
    private void record(final String build, final String role, final String root, final Object recorded) {
        if (!(recorded instanceof final String before) || !PromotionLayout.sameFolder(before, root)) {
            buildRecords.rememberPromotionRoot(build, role, root);
        }
    }

    // An option of where a role goes, as the folder its pipeline goes in, saying what it is.
    private static String optionLabel(final String folder,
                                      final String root,
                                      final String feedRoot,
                                      final String why) {
        final String reason = why == null || why.endsWith("the feed's own location")
                ? null
                : why.replace("proposed: ", "");
        final String said = PromotionLayout.sameFolder(root, feedRoot)
                ? "the feed's own folder" + (reason == null
                        ? ""
                        : ", " + reason)
                : reason;
        return folder + (said == null
                ? ""
                : " (" + said + ")");
    }

    // The folder each role's (first) pipeline goes in, its root as given: the layout tried on a copy of the plan.
    private Map<String, String> trialFolders(final List<PromotionLayout.Step> plan,
                                             final Map<String, String> rootByRole,
                                             final PromoteBuildTool.Placed placed,
                                             final Map<String, ModuleChecks.Marker> markers) {
        final List<PromotionLayout.Step> trial = new ArrayList<>(plan);
        layOut(trial, rootByRole, placed, markers);
        final Map<String, String> out = new LinkedHashMap<>();
        rootByRole.forEach((role, root) -> out.put(role, pipelineFolder(trial, role).orElseGet(() ->
                PromotionLayout.rolePath(placed.source().own(), root, placed.group() == null
                        ? null
                        : placed.group().name(), role, PromotionLayout.FIRST_VERSION))));
        return out;
    }

    // The folder a role's first pipeline moves to, as laid out.
    private static Optional<String> pipelineFolder(final List<PromotionLayout.Step> plan, final String role) {
        return plan.stream()
                .filter(step -> PromotionLayout.MOVE.equals(step.action()) && role.equals(step.role())
                                && PipelineDoc.TYPE.equals(step.doc().get("type")) && step.target() != null)
                .map(PromotionLayout.Step::target)
                .findFirst();
    }

    /// Each role's root with the folder its pipeline goes in, as laid out.
    ///
    /// @param roots Each role's root.
    /// @param plan  The layout's steps, laid out.
    /// @return Each role's root with its folder.
    static List<RoleRoot> withFolders(final List<RoleRoot> roots, final List<PromotionLayout.Step> plan) {
        return roots.stream()
                .map(root -> new RoleRoot(root.role(), root.root(), root.why(), root.options(), root.toAsk(),
                        pipelineFolder(plan, root.role()).orElse(root.root())))
                .toList();
    }

    // Whether the feed's own folder is given for a feed already in production: under its type, Feed, its name or
    // its document's uuid. Such a destination moves nothing, so it can only say where the roles go (seen in chat
    // 194). A feed the build makes is placed by it, so there it says nothing of the roles: a role key does.
    private boolean feedsOwnFolderGiven(final List<Map<String, Object>> docs,
                                        final Map<String, String> destinations,
                                        final PromoteBuildTool.Source source) {
        if (!feedKeyCoversRoles(docs, source)) {
            return false;
        }
        final Set<String> keys = new LinkedHashSet<>();
        keys.add(FeedDoc.TYPE.toLowerCase(Locale.ROOT));
        keys.add(source.name().toLowerCase(Locale.ROOT));
        places.named(FeedDoc.TYPE, source.name())
                .forEach(ref -> keys.add(NullSafe.string(ref.getUuid()).toLowerCase(Locale.ROOT)));
        return destinations.entrySet().stream()
                .anyMatch(entry -> keys.contains(entry.getKey().strip().toLowerCase(Locale.ROOT))
                                   && NullSafe.isNonBlankString(entry.getValue())
                                   && !entry.getValue().strip().equals("/")
                                   && PromotionLayout.sameFolder(entry.getValue(), source.own()));
    }

    /// Whether the build's source feed is in production, not one the build makes: the Feed key then says where the
    /// roles go.
    ///
    /// @param docs   The build's documents.
    /// @param source The build's source, or null.
    /// @return True if the Feed key says where the roles go.
    static boolean feedKeyCoversRoles(final List<Map<String, Object>> docs, final PromoteBuildTool.Source source) {
        return source != null && PromoteBuildTool.ofType(docs, FeedDoc.TYPE).stream()
                .noneMatch(doc -> source.name().equalsIgnoreCase(String.valueOf(doc.get("name"))));
    }

    /// What changes where a role's documents go, for the agent.
    ///
    /// @param feedFolder    The feed's folder.
    /// @param roles         The roles.
    /// @param feedKeyCovers True if the Feed key says where the roles go.
    /// @return The hint.
    static String roleKeysHint(final String feedFolder,
                               final Collection<String> roles,
                               final boolean feedKeyCovers) {
        return "To change where a role's documents go, call promote_build again with no reviewed and destinations "
               + "keyed by the role (" + String.join(", ", roles) + "): the folder to hold "
               + PromotionLayout.lastPart(feedFolder) + "/<version>, or the folder the layout showed for it; the "
               + "feed's own folder ('" + feedFolder + "') " + (feedKeyCovers
                ? "under Feed (or under a role) puts every role it covers"
                : "under a role puts it") + " in " + feedFolder + "/<Role>/<version>.";
    }

    /// Whether a role (or null) is one of these.
    ///
    /// @param roles The roles.
    /// @param role  The role, or null.
    /// @return True if it is one of them.
    static boolean isRole(final List<String> roles, final String role) {
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
                                                 final PromoteBuildTool.GroupPlace group) {
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

    /// Each document of a role put in its role's and version's folder (PromotionLayout.layOut), each role's root as
    /// chosen. What the layout warns of, for the user.
    ///
    /// @param plan       The layout's steps, laid out in place.
    /// @param rootByRole Each role's root.
    /// @param placed     What decides where the build's documents go.
    /// @param markers    The build's modules' marks, by UUID.
    /// @return What the layout warns of.
    List<String> layOut(final List<PromotionLayout.Step> plan,
                        final Map<String, String> rootByRole,
                        final PromoteBuildTool.Placed placed,
                        final Map<String, ModuleChecks.Marker> markers) {
        // The names a pipeline's name may hold whose version tokens are theirs (found in review, 2026-11-18).
        final List<String> held = Stream.concat(placed.sources().stream(), Stream.ofNullable(placed.source())
                .map(PromoteBuildTool.Source::name)).toList();
        return layout.layOut(plan, rootByRole, placed.source() == null
                ? null
                : placed.source().own(), placed.group() == null
                ? null
                : placed.group().name(), step -> groupOf(step, placed, markers), held);
    }

    // The source type group a document of a role is laid out under: a group XSLT's own (by its mark: another
    // group's, the build may hold), else the build's group's; null in a build of none.
    private static String groupOf(final PromotionLayout.Step step,
                                  final PromoteBuildTool.Placed placed,
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

    /// What the layout says of each role's location, so the review (and the approval bound to it) covers them.
    ///
    /// @param roots Each role's root.
    /// @return The note, or empty if there are no roles.
    static String rootsNote(final List<RoleRoot> roots) {
        if (roots.isEmpty()) {
            return "";
        }
        return "\n\nWhere each role goes (destinations keyed by the role change it):\n" + String.join("\n",
                roots.stream().map(root -> "- " + root.role() + ": " + root.folder() + " (" + root.why() + ")")
                        .toList());
    }

    /// A pipeline's stage, or null for one Stroom can't load (a choice then made without it, not a failure).
    ///
    /// @param pipelineUuid The pipeline's UUID.
    /// @return Its stage, or null.
    String stageOrNone(final String pipelineUuid) {
        try {
            return facts.pipelineShape(pipelineUuid).stage();
        } catch (final PipelineUnreadableException e) {
            return null;
        }
    }


    // --------------------------------------------------------------------------------


    /// A role's root: the folder holding the feed's folder for that role (the feed's own location, or the feed's
    /// folder itself, keeps them in `<feed>/<Role>/<version>`; another, `<root>/<feed>/<version>`).
    ///
    /// @param role    The role, e.g. `Indexing`.
    /// @param root    The root chosen, from System.
    /// @param why     Where it came from, for the user.
    /// @param options The folders offered, as the role's pipeline would go in each: the proposal first, then the
    ///                feed's own folder where it differs (the user may name another).
    /// @param toAsk   True if it is the proposal, the user not asked yet.
    /// @param folder  The folder the role's pipeline goes in, as laid out; null until it is.
    record RoleRoot(String role, String root, String why, List<String> options, boolean toAsk, String folder) {

    }
}
