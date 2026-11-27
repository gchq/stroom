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

import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/// Where promotion puts a build's documents, role first, then version (decided by the user, 2026-11-17):
/// `<root>/<feed>/<Role>/<version>` under the feed's own root, `<root>/<feed>/<version>` under a root of the role's
/// own elsewhere; a new version in a sibling version folder of its predecessor's; and the layout shown to the user as
/// a tree of the destination folders. Folders are paths from System, e.g. `System/Feeds/Acme`.
class PromotionLayout {

    /// A step: a document moved into a folder.
    static final String MOVE = "move";
    /// A step: a working copy written back over its original.
    static final String WRITE_BACK = "write back";
    /// A step: a working copy of a pipeline deleted once its documents are written back.
    static final String DISCARD = "discard";
    /// A step: a document left in the workspace.
    static final String KEEP = "keep";
    /// A kept step's target: a build document nothing promoted uses (decided by the user, 2026-11-18), left in the
    /// build.
    static final String NOT_USED = "not promoted: nothing uses it";

    /// An aggregated feed's shared modules' folders, each a role of its own beside [WriteGuard#ROLES], always under
    /// the feed's folder: `<feed>/Common/V1.0`, `<feed>/Helpers/V1.0`.
    static final List<String> MODULE_ROLES = List.of("Common", "Helpers");
    /// A step's document's key naming what it is called now, where promotion renames it as it moves it: named for
    /// another version than the pipeline it goes with ([#layOut]).
    static final String RENAMED_FROM = "renamed_from";
    /// The version folder of a pipeline whose name holds no version token (with a warning in the layout).
    static final String FIRST_VERSION = "V1.0";
    /// The most production pipelines of sibling feeds read for their roles, a promotion.
    static final int MAX_SIBLINGS_READ = 200;

    private static final Pattern VERSION = Pattern.compile("(?i)(?<![A-Za-z0-9])v\\d+(?:\\.\\d+)*(?![A-Za-z0-9])");

    private final DocumentPlaces places;

    /// @param places Finds documents' folders, as the user.
    @Inject
    PromotionLayout(final DocumentPlaces places) {
        this.places = places;
    }

    /// Whether a folder is one of a role's: [WriteGuard#ROLES], or an aggregated feed's shared modules'
    /// ([#MODULE_ROLES]).
    ///
    /// @param name A folder's name.
    /// @return True if it names a role's folder.
    static boolean isRoleFolder(final String name) {
        return Stream.concat(WriteGuard.ROLES.stream(), MODULE_ROLES.stream()).anyMatch(role -> role.equalsIgnoreCase(
                NullSafe.string(name)));
    }

    /// @param name A folder's name.
    /// @return True if it is a version's folder, e.g. `V1.1` or `v2`.
    static boolean isVersionFolder(final String name) {
        return VERSION.matcher(NullSafe.string(name)).matches();
    }

    /// A predecessor's folder with its role and version folders taken off the end, in either order and one of each
    /// at most: `<feed>/<Role>/V1.0`, `<feed>/V1.0/<Role>`, `<feed>/<Role>`, `<feed>/v2` and `<feed>` alone all give
    /// `<feed>` (production laid out the old way is never moved, so a new version is placed beside any of them).
    ///
    /// @param folder The predecessor's folder, an explorer path.
    /// @return The folder above its role and version folders, with the role and version folder names taken off.
    static Predecessor predecessor(final String folder) {
        String at = normalised(folder);
        String role = null;
        String version = null;
        for (int n = 0; n < 2 && at.contains("/"); n++) {
            final String last = lastPart(at);
            if (role == null && isRoleFolder(last)) {
                role = last;
                at = parentOf(at);
            } else if (version == null && isVersionFolder(last)) {
                version = last;
                at = parentOf(at);
            }
        }
        return new Predecessor(at, role, version);
    }

    /// The folder a role's documents of one version go in (decided by the user, 2026-11-17): role first, then
    /// version. Under the feed's own root, `<feed>/<Role>/<version>` (an aggregated feed's group's:
    /// `<feed>/<group>/<Role>/<version>`); under a root of the role's own, elsewhere, no role folder:
    /// `<root>/<feed>/<version>` (`<root>/<feed>/<group>/<version>`), e.g. `System/Elastic Indices/App-Logs/V1.0`.
    /// An aggregated feed's shared modules always go under the feed: `<feed>/Common/<version>`. A root that is the
    /// feed's folder itself is the feed's own ([#ownRoot]).
    ///
    /// @param feedFolder The feed's folder, e.g. `System/Parent/App-Logs`.
    /// @param root       The role's root, or null for the feed's own.
    /// @param group      The source type group, or null.
    /// @param role       The role, e.g. `Events`.
    /// @param version    The version folder's name, e.g. `V1.0`.
    /// @return The folder, as a path from System.
    static String rolePath(final String feedFolder,
                           final String root,
                           final String group,
                           final String role,
                           final String version) {
        final String feed = normalised(feedFolder);
        final String groupPart = NullSafe.isBlankString(group)
                ? ""
                : "/" + group;
        final String own = ownRoot(root, feed);
        if (own == null || sameFolder(own, parentOf(feed)) || MODULE_ROLES.contains(role)) {
            return feed + groupPart + "/" + role + "/" + version;
        }
        return normalised(own) + "/" + lastPart(feed) + groupPart + "/" + version;
    }

    /// A role's root as the layout reads it: the feed's folder itself given as the root is the feed's own location,
    /// the folder holding it. Seen in a user test (2026-11-18): the feed in a folder named after it, its own folder
    /// given as the root (the question asked for "the folder holding the feed's Events folders") gave
    /// `<feed>/<feed>/V1.0`, every role together with no role folders.
    ///
    /// A folder in the feed's (seen in chat 194: a role's folder the layout showed) is read the same way.
    ///
    /// @param root       A role's root, or null.
    /// @param feedFolder The feed's folder, or null when it isn't known.
    /// @return The root, the folder holding the feed's where the root is the feed's folder or one in it; null for
    /// null.
    static String ownRoot(final String root, final String feedFolder) {
        return root != null && feedFolder != null && within(root, feedFolder)
                ? parentOf(normalised(feedFolder))
                : root;
    }

    /// A folder the user (or the agent) gave for a role, read as the role's root: the feed's folder, or one in it
    /// (`<feed>/Indexing/V1.0`, a folder the layout showed), is the feed's own location; a folder ending in the
    /// feed's folder's name, with or without its role and version folders (`System/Elastic Indices/Acme/V1.0`), is
    /// the folder holding it. Seen in chat 194: the user named the full folder they wanted, which read as a root
    /// would have nested the feed's folder again.
    ///
    /// @param given      The folder given, an explorer path.
    /// @param feedFolder The feed's folder.
    /// @return The role's root, from System.
    static String givenRoot(final String given, final String feedFolder) {
        final String folder = normalised(given);
        if (within(folder, feedFolder)) {
            return parentOf(normalised(feedFolder));
        }
        final String base = predecessor(folder).base();
        if (base.contains("/") && lastPart(base).equalsIgnoreCase(lastPart(normalised(feedFolder)))) {
            return parentOf(base);
        }
        return folder;
    }

    // Whether a folder is another or in it (any case, with or without System).
    private static boolean within(final String folder, final String outer) {
        final String inner = normalised(folder).toLowerCase(Locale.ROOT);
        final String holding = normalised(outer).toLowerCase(Locale.ROOT);
        return inner.equals(holding) || inner.startsWith(holding + "/");
    }

    /// A new version's folder beside its predecessor's (decided by the user, 2026-11-17): a sibling version folder in
    /// the role's folder (`<feed>/Events/V1.0` gives `<feed>/Events/V1.1`; `<root>/<feed>/V1.0` gives
    /// `<root>/<feed>/V1.1`). A predecessor with no role folder of its own gets one where it is the feed's folder or a
    /// group's in it, or, with the feed's folder unknown, where it has no version folder either.
    ///
    /// @param before     The predecessor's folder, taken apart ([#predecessor]).
    /// @param feedFolder The feed's folder, or null when it isn't known.
    /// @param role       The role.
    /// @param version    The new version's folder name.
    /// @return The new version's folder.
    static String nextVersionPath(final Predecessor before,
                                  final String feedFolder,
                                  final String role,
                                  final String version) {
        final String base = before.base();
        final boolean feedLevel = feedFolder != null
                                  && (sameFolder(base, feedFolder) || sameFolder(parentOf(base), feedFolder));
        final boolean roleFolder = before.role() != null || feedLevel
                                   || (feedFolder == null && before.version() == null);
        return base + (roleFolder
                ? "/" + NullSafe.nonBlankStringElse(before.role(), role)
                : "") + "/" + version;
    }

    /// @param before     A predecessor's folder, taken apart ([#predecessor]).
    /// @param feedFolder The feed's folder, or null.
    /// @param group      The build's source type group, or null.
    /// @return The root its role is under: the folder holding its feed's folder (above a group's folder).
    static String rootOf(final Predecessor before, final String feedFolder, final String group) {
        String base = before.base();
        if (feedFolder != null && sameFolder(parentOf(base), feedFolder)) {
            // A group's folder in the feed's.
            base = parentOf(base);
        } else if (group != null && lastPart(base).equalsIgnoreCase(group) && base.contains("/")) {
            base = parentOf(base);
        }
        return parentOf(base);
    }

    /// Where the production pipelines of each role of the sibling feeds are: those in folders named for a sibling
    /// (`<root>/<sibling>/V1.0`, `<root>/<sibling>/<Role>/V1.0`, `<root>/<sibling>`), by the root most of them are
    /// under. Only a sibling's own folders are read as its: a pipeline elsewhere tells nothing of the convention.
    ///
    /// @param pipelines The pipelines the user can view.
    /// @param siblings  The sibling feeds' names and their folders' names, any case.
    /// @param roleOf    A pipeline's role by its UUID, or null when it has none.
    /// @param skip      True for a pipeline not to read: one in a build, say.
    /// @return Each role's root, by role.
    static Map<String, String> siblingRoots(final Collection<PipelineIndex.Entry> pipelines,
                                            final Set<String> siblings,
                                            final Function<String, String> roleOf,
                                            final Predicate<PipelineIndex.Entry> skip) {
        final Set<String> names = new LinkedHashSet<>();
        siblings.forEach(name -> names.add(NullSafe.string(name).toLowerCase(Locale.ROOT)));
        final Map<String, List<String>> bases = new LinkedHashMap<>();
        int read = 0;
        for (final PipelineIndex.Entry pipeline : pipelines) {
            final String base = predecessor(pipeline.path()).base();
            if (read >= MAX_SIBLINGS_READ || !names.contains(lastPart(base).toLowerCase(Locale.ROOT))
                || skip.test(pipeline)) {
                continue;
            }
            read++;
            final String role = roleOf.apply(pipeline.uuid());
            if (role != null && WriteGuard.ROLES.contains(role)) {
                bases.computeIfAbsent(role, ignored -> new ArrayList<>()).add(base);
            }
        }
        final Map<String, String> out = new LinkedHashMap<>();
        bases.forEach((role, found) -> out.put(role, commonRoot(found)));
        return out;
    }

    // The root most of the folders are under: their parent, the most common one (the first of those as common).
    private static String commonRoot(final List<String> bases) {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        bases.forEach(base -> counts.merge(parentOf(base).toLowerCase(Locale.ROOT), 1, Integer::sum));
        final int most = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return bases.stream()
                .map(PromotionLayout::parentOf)
                .filter(parent -> counts.get(parent.toLowerCase(Locale.ROOT)) == most)
                .findFirst()
                .orElse(null);
    }

    /// @param left  A folder, an explorer path.
    /// @param right Another.
    /// @return True if they are the same folder (any case, with or without System).
    static boolean sameFolder(final String left, final String right) {
        return left != null && right != null && normalised(left).equalsIgnoreCase(normalised(right));
    }

    /// Puts each document of a role in its role's and version's folder (decided by the user, 2026-11-17): a pipeline by
    /// the version its name holds ([#FIRST_VERSION] when it holds none, with a warning), a new version beside its
    /// predecessor unless its role's root was changed; a shared module by its own version; the role's other documents
    /// with the pipeline they belong to: of their group, the one whose version their name holds, else the one sharing
    /// most of their name. A document with nowhere to go is left so, for the caller to name.
    ///
    /// @param plan       The promotion's steps, each document of a role in its source's folder: changed in place.
    /// @param rootByRole Each role's root, by role; none for the feed's own.
    /// @param feedFolder The source's own folder, or null when it isn't known.
    /// @param buildGroup The build's source type group, or null.
    /// @param groupOf    The source type group a step's document is laid out under, or null.
    /// @param held       The names a pipeline's name may hold whose version tokens are theirs, not its version
    ///                   (the feeds the build works from; its group's is added per document): found in review,
    ///                   2026-11-18, `CISCO-ASA-V9-EVENTS` of feed `CISCO-ASA-V9` was filed in V9.
    /// @return What the layout warns of, for the user.
    List<String> layOut(final List<Step> plan,
                        final Map<String, String> rootByRole,
                        final String feedFolder,
                        final String buildGroup,
                        final Function<Step, String> groupOf,
                        final Collection<String> held) {
        final List<String> warnings = new ArrayList<>();
        final List<LaidPipeline> pipelines = new ArrayList<>();
        for (int i = 0; i < plan.size(); i++) {
            final Step step = plan.get(i);
            if (!MOVE.equals(step.action()) || !isRole(step.role())
                || !PipelineDoc.TYPE.equals(step.doc().get("type"))) {
                continue;
            }
            final String name = String.valueOf(step.doc().get("name"));
            final String group = groupOf.apply(step);
            final Optional<String> token = VersionNames.token(name, withGroup(held, group));
            if (token.isEmpty()) {
                warnings.add("Pipeline '" + name + "' has no version in its name (e.g. -V1.0-): placed in "
                             + FIRST_VERSION + ".");
            }
            final String version = token.orElse(FIRST_VERSION);
            final String role = step.role();
            final String base = feedFolder != null
                    ? feedFolder
                    : step.target();
            if (base == null) {
                // Nowhere to lay it out: named with the others that have no destination.
                continue;
            }
            final Optional<String> before = predecessorFolder(step.doc());
            final String root = ownRoot(rootByRole.get(role), base);
            String target = rolePath(base, root, group, role, version);
            if (before.isPresent()) {
                final Predecessor predecessor = predecessor(before.get());
                if (root == null || sameFolder(root, ownRoot(rootOf(predecessor, feedFolder, buildGroup),
                        feedFolder))) {
                    target = nextVersionPath(predecessor, feedFolder, role, version);
                }
            }
            plan.set(i, step.withTarget(target));
            pipelines.add(new LaidPipeline(role, group, name, version, target));
        }
        for (int i = 0; i < plan.size(); i++) {
            final Step step = plan.get(i);
            final String role = step.role();
            if (!MOVE.equals(step.action()) || role == null
                || PipelineDoc.TYPE.equals(step.doc().get("type")) && isRole(role)) {
                continue;
            }
            final String name = String.valueOf(step.doc().get("name"));
            if (MODULE_ROLES.contains(role)) {
                if (step.target() != null) {
                    plan.set(i, step.withTarget(rolePath(step.target(), null, null, role,
                            VersionNames.moduleVersion(name))));
                }
                continue;
            }
            final String group = groupOf.apply(step);
            final Optional<LaidPipeline> with = belongsTo(pipelines, role, group, name, withGroup(held, group));
            final String base = feedFolder != null
                    ? feedFolder
                    : step.target();
            if (with.isPresent()) {
                plan.set(i, forVersion(step, with.get(), withGroup(held, group), warnings)
                        .withTarget(with.get().target()));
            } else if (base != null) {
                plan.set(i, step.withTarget(rolePath(base, rootByRole.get(role), group, role,
                        VersionNames.token(name, withGroup(held, group)).orElse(FIRST_VERSION))));
            }
        }
        return warnings;
    }

    // A document going with a pipeline of one version but named for another (seen in chat 192: a group's XSLT named
    // 'Messaging V1.0' went with pipeline 'Messaging V1.1' into its V1.1 folder, beside production's XSLT of that
    // name): promoted under the name for the pipeline's version, said in the layout's warnings. The step keeps the
    // name it has now as RENAMED_FROM, for promotion to rename it as it moves it.
    private static Step forVersion(final Step step,
                                   final LaidPipeline with,
                                   final Collection<String> held,
                                   final List<String> warnings) {
        final String name = String.valueOf(step.doc().get("name"));
        final Optional<String> token = VersionNames.token(name, held);
        if (token.isEmpty()) {
            return step;
        }
        final Optional<String> renamed = VersionNames.retokened(name, token.get(), with.version(), held);
        if (renamed.isEmpty()) {
            return step;
        }
        warnings.add(step.doc().get("type") + " '" + name + "' is named for " + token.get() + " but goes with "
                     + "pipeline '" + with.name() + "' (" + with.version() + "): promoted as '" + renamed.get()
                     + "'.");
        final Map<String, Object> doc = new LinkedHashMap<>(step.doc());
        doc.put("name", renamed.get());
        doc.put(RENAMED_FROM, name);
        return new Step(doc, step.action(), step.target(), step.role());
    }

    // The names held, with the group's (when there is one).
    private static List<String> withGroup(final Collection<String> held, final String group) {
        final List<String> out = new ArrayList<>(NullSafe.collection(held));
        NullSafe.consumeNonBlankString(group, out::add);
        return out;
    }

    // Whether a role (or null) is one of WriteGuard.ROLES.
    private static boolean isRole(final String role) {
        return role != null && WriteGuard.ROLES.contains(role);
    }

    /// @param doc A document, as the build lists it.
    /// @return Its predecessor's folder, for a new version; empty for a document that is no new version, or one gone.
    Optional<String> predecessorFolder(final Map<String, Object> doc) {
        final Object versionOf = doc.get("version_of");
        return versionOf == null
                ? Optional.empty()
                : places.find(String.valueOf(versionOf)).flatMap(places::folderOf);
    }

    // The pipeline a role's other document belongs with: of its group, the one whose version its name holds, else
    // the one whose name it shares most of, else the first.
    private static Optional<LaidPipeline> belongsTo(final List<LaidPipeline> pipelines,
                                                    final String role,
                                                    final String group,
                                                    final String name,
                                                    final Collection<String> held) {
        List<LaidPipeline> candidates = pipelines.stream().filter(p -> p.role().equals(role)).toList();
        final List<LaidPipeline> grouped = candidates.stream().filter(p -> Objects.equals(p.group(), group)).toList();
        if (!grouped.isEmpty()) {
            candidates = grouped;
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        final Optional<String> token = VersionNames.token(name, held);
        if (token.isPresent()) {
            final Optional<LaidPipeline> same = candidates.stream()
                    .filter(p -> p.version().equalsIgnoreCase(token.get()))
                    .findFirst();
            if (same.isPresent()) {
                return same;
            }
        }
        return candidates.stream().max(Comparator.comparingInt(p -> sharedStart(p.name(), name)));
    }

    // How many characters two names start with in common, any case.
    private static int sharedStart(final String left, final String right) {
        int n = 0;
        while (n < left.length() && n < right.length()
               && Character.toLowerCase(left.charAt(n)) == Character.toLowerCase(right.charAt(n))) {
            n++;
        }
        return n;
    }

    /// @param warnings What the layout warns of.
    /// @return Them, as the tree's last lines; nothing for none.
    static String warningsNote(final List<String> warnings) {
        return warnings.isEmpty()
                ? ""
                : "\n\n" + String.join("\n", warnings);
    }

    /// Where a promotion puts each document, as a tree of the destination folders (new folders marked), with the
    /// documents written back over production ones and those left in the workspace after it.
    ///
    /// @param plan     The promotion's steps.
    /// @param creating The folders to create.
    /// @return The tree, as text to show in the chat.
    String promotionTree(final List<Step> plan, final List<String> creating) {
        return promotionTree(plan, creating, Set.of());
    }

    /// The tree, as [#promotionTree(List, List)] gives it, once promoted: what the tidy-up deleted isn't left in the
    /// workspace.
    ///
    /// @param plan     The promotion's steps.
    /// @param creating The folders to create.
    /// @param gone     The UUIDs of the documents deleted since, none of them left in the workspace.
    /// @return The tree, as text to show in the chat.
    String promotionTree(final List<Step> plan, final List<String> creating, final Set<String> gone) {
        final Map<String, List<String>> placed = new LinkedHashMap<>();
        final List<String> written = new ArrayList<>();
        final List<String> kept = new ArrayList<>();
        final List<String> unused = new ArrayList<>();
        for (final Step step : plan) {
            final Map<String, Object> doc = step.doc();
            final String type = String.valueOf(doc.get("type"));
            final String name = String.valueOf(doc.get("name"));
            switch (step.action()) {
                case MOVE -> placed.computeIfAbsent(step.target(), ignored -> new ArrayList<>())
                        .add(name + "  (" + type + ")");
                case WRITE_BACK -> {
                    final Optional<DocRef> original = places.find(step.target());
                    if (original.isPresent()) {
                        placed.computeIfAbsent(places.folderOf(original.get()).orElse("(its folder)"),
                                ignored -> new ArrayList<>()).add(original.get().getName() + "  (" + type
                                                                   + ": updated from its working copy, backed "
                                                                   + "up first)");
                    } else {
                        written.add(name + " (" + type + ")");
                    }
                }
                case KEEP -> {
                    if (!gone.contains(String.valueOf(doc.get("uuid")))) {
                        (NOT_USED.equals(step.target())
                                ? unused
                                : kept).add(name + " (" + type + ")");
                    }
                }
                default -> {
                    // A discarded working copy isn't shown.
                }
            }
        }
        final Map<String, Object> tree = new LinkedHashMap<>();
        placed.forEach((folder, items) -> {
            Map<String, Object> node = tree;
            for (final String part : folder.split("/", -1)) {
                node = folderNode(node, part + "/");
            }
            final Map<String, Object> holder = node;
            items.stream().sorted().forEach(item -> holder.put(item, null));
        });
        final List<String> lines = new ArrayList<>();
        // From the deepest folder all destinations share, so the tree starts where they part.
        String root = "";
        Map<String, Object> node = tree;
        while (node.size() == 1 && onlyValue(node) instanceof final Map<?, ?> only && only.size() == 1
               && onlyValue(only) instanceof Map) {
            final String key = node.keySet().iterator().next();
            root += key;
            node = folder(node.get(key));
        }
        final String first = node.size() == 1
                ? node.keySet().iterator().next()
                : null;
        if (first != null && node.get(first) instanceof Map) {
            lines.add(root + first + (creating.contains(stripSlashes(root + first))
                    ? "  (new)"
                    : ""));
            walk(folder(node.get(first)), "", root + first, creating, lines);
        } else {
            lines.add(root.isEmpty()
                    ? "/"
                    : root);
            walk(node, "", root, creating, lines);
        }
        String out = String.join("\n", lines);
        if (!written.isEmpty()) {
            out += "\n\nUpdated from working copies: " + String.join(", ", written);
        }
        if (!kept.isEmpty()) {
            out += "\n\nLeft in the workspace: " + String.join(", ", kept);
        }
        if (!unused.isEmpty()) {
            out += "\n\nNot promoted, left in the build (nothing uses them): " + String.join(", ", unused);
        }
        return out;
    }

    /// @param name A document's name.
    /// @return The versions it names, e.g. `V1.1` in `ACME-V1.1-Events`, in order.
    static List<String> versions(final String name) {
        final List<String> found = new ArrayList<>();
        final Matcher matcher = VERSION.matcher(name);
        while (matcher.find()) {
            found.add(matcher.group());
        }
        return found;
    }

    private static void walk(final Map<String, Object> node,
                             final String prefix,
                             final String path,
                             final List<String> creating,
                             final List<String> lines) {
        final List<String> keys = new ArrayList<>(node.keySet());
        for (int n = 0; n < keys.size(); n++) {
            final String key = keys.get(n);
            final boolean last = n == keys.size() - 1;
            final String here = path + key;
            final String mark = key.endsWith("/") && creating.contains(stripSlashes(here))
                    ? "  (new)"
                    : "";
            lines.add(prefix + (last
                    ? "└── "
                    : "├── ") + key + mark);
            if (node.get(key) instanceof final Map<?, ?> child && !child.isEmpty()) {
                walk(folder(child), prefix + (last
                        ? "    "
                        : "│   "), here, creating, lines);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> folderNode(final Map<String, Object> node, final String key) {
        return (Map<String, Object>) node.computeIfAbsent(key, ignored -> new LinkedHashMap<String, Object>());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> folder(final Object node) {
        return (Map<String, Object>) node;
    }

    private static Object onlyValue(final Map<?, ?> node) {
        return node.values().iterator().next();
    }

    // The path without its trailing slashes.
    private static String stripSlashes(final String path) {
        int end = path.length();
        while (end > 0 && path.charAt(end - 1) == '/') {
            end--;
        }
        return path.substring(0, end);
    }

    /// @param place An explorer path, with or without System.
    /// @return It from System, its folders' names stripped.
    static String normalised(final String place) {
        return String.join("/", WriteGuard.folderParts(place));
    }

    /// @param path An explorer path.
    /// @return Its last folder's name.
    static String lastPart(final String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /// @param path An explorer path.
    /// @return The folder holding it; the path itself at the top.
    static String parentOf(final String path) {
        final int slash = path.lastIndexOf('/');
        return slash < 0
                ? path
                : path.substring(0, slash);
    }


    // --------------------------------------------------------------------------------


    /// A pipeline as laid out.
    ///
    /// @param role    Its role.
    /// @param group   Its source type group, or null.
    /// @param name    Its name.
    /// @param version Its version folder's name.
    /// @param target  Its folder.
    private record LaidPipeline(String role, String group, String name, String version, String target) {

    }

    /// A predecessor's folder taken apart.
    ///
    /// @param base    The folder above its role and version folders: its feed's, or a group's.
    /// @param role    The role folder taken off, or null.
    /// @param version The version folder taken off, or null.
    record Predecessor(String base, String role, String version) {

    }

    /// One step of a promotion.
    ///
    /// @param doc    The document, as the build lists it.
    /// @param action [#MOVE], [#WRITE_BACK], [#DISCARD] or [#KEEP].
    /// @param target The folder it moves to, the original a working copy is written back over, or what becomes
    ///               of it.
    /// @param role   The role subfolder kept under the folder, or null.
    record Step(Map<String, Object> doc, String action, String target, String role) {

        /// @param newTarget The target with the role subfolder.
        /// @return The step moving into it.
        Step withTarget(final String newTarget) {
            return new Step(doc, action, newTarget, role);
        }
    }
}
