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

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Where promotion puts a build's documents: a new version of a production document in a version subfolder beside its
/// predecessor, the version's other documents with it, and the layout shown to the user as a tree of the destination
/// folders. Folders are paths from System, e.g. `System/Feeds/Acme`.
class PromotionLayout {

    /// A step: a document moved into a folder.
    static final String MOVE = "move";
    /// A step: a working copy written back over its original.
    static final String WRITE_BACK = "write back";
    /// A step: a working copy of a pipeline deleted once its documents are written back.
    static final String DISCARD = "discard";
    /// A step: a document left in the workspace.
    static final String KEEP = "keep";

    private static final Pattern VERSION = Pattern.compile("(?i)(?<![A-Za-z0-9])v\\d+(?:\\.\\d+)*(?![A-Za-z0-9])");

    private final DocumentPlaces places;

    /// @param places Finds documents' folders, as the user.
    @Inject
    PromotionLayout(final DocumentPlaces places) {
        this.places = places;
    }

    /// For each new version of a production document (copy_pipeline: `version_of`), a version subfolder beside its
    /// predecessor, named by the version in its own name (…-V1.1-Events: V1.1), so old and new are told apart at a
    /// glance (asked for by the user). Beside the predecessor, in its folder's parent, when that folder is itself a
    /// version's. Documents given a destination of their own keep it, as do those whose type is given one elsewhere
    /// than beside the predecessor (seen in testing, when the version went beside its predecessor anyway and the
    /// build's other documents to the folder given: TWITTER's V1.1 pipeline under /Feeds/Twitter, its XSLT and
    /// documentation under System/MCP Testing/Twitter).
    ///
    /// @param docs         The build's documents.
    /// @param destinations The destinations given, by type or document UUID.
    /// @return Each new version's folder by document UUID, and the version folders by predecessor's folder.
    VersionFolders versionFolders(final List<Map<String, Object>> docs, final Map<String, String> destinations) {
        final Map<String, String> out = new LinkedHashMap<>();
        final Map<String, Set<String>> beside = new LinkedHashMap<>();
        for (final Map<String, Object> doc : docs) {
            final Object versionOf = doc.get("version_of");
            final String uuid = String.valueOf(doc.get("uuid"));
            if (versionOf == null || destinations.containsKey(uuid) || doc.get("working_copy_of") != null) {
                continue;
            }
            final Optional<DocRef> before = places.find(String.valueOf(versionOf));
            if (before.isEmpty()) {
                continue;
            }
            final String folder = places.folderOf(before.get()).orElse(null);
            final List<String> found = versions(String.valueOf(doc.get("name")));
            if (folder == null || found.isEmpty()) {
                continue;
            }
            final int slash = folder.lastIndexOf('/');
            final String parent = slash < 0
                    ? ""
                    : folder.substring(0, slash);
            final String last = folder.substring(slash + 1);
            final String version = VERSION.matcher(last).matches()
                    ? parent + "/" + found.getLast()
                    : folder + "/" + found.getLast();
            final String typeGiven = destinations.get(String.valueOf(doc.get("type")));
            if (typeGiven != null && !placesMeant(folder, version).contains(normalised(typeGiven))) {
                continue;
            }
            out.put(uuid, version);
            beside.computeIfAbsent(folder, ignored -> new LinkedHashSet<>()).add(version);
        }
        return new VersionFolders(out, beside);
    }

    /// Where a version's other new documents go: those headed for its predecessor's folder, or the version folder's
    /// parent, go in the version folder with it (its index, documentation and dashboard: seen in e2e, the new pipeline
    /// in v2/ and its index beside v1). Only where one version folder is meant for that place.
    ///
    /// @param beside The version folders by predecessor's folder.
    /// @return The version folder by place.
    static Map<String, String> joining(final Map<String, Set<String>> beside) {
        final Map<String, Set<String>> joins = new LinkedHashMap<>();
        beside.forEach((folder, versions) -> {
            for (final String version : versions) {
                for (final String place : placesMeant(folder, version)) {
                    joins.computeIfAbsent(place, ignored -> new LinkedHashSet<>()).add(version);
                }
            }
        });
        final Map<String, String> out = new LinkedHashMap<>();
        joins.forEach((place, versions) -> {
            if (versions.size() == 1) {
                out.put(place, versions.iterator().next());
            }
        });
        return out;
    }

    /// Where a promotion puts each document, as a tree of the destination folders (new folders marked), with the
    /// documents written back over production ones and those left in the workspace after it.
    ///
    /// @param plan     The promotion's steps.
    /// @param creating The folders to create.
    /// @return The tree, as text to show in the chat.
    String promotionTree(final List<Step> plan, final List<String> creating) {
        final Map<String, List<String>> placed = new LinkedHashMap<>();
        final List<String> written = new ArrayList<>();
        final List<String> kept = new ArrayList<>();
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
                case KEEP -> kept.add(name + " (" + type + ")");
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

    // The places that mean a version's folder: its predecessor's folder and the version folder's parent, as
    // explorer paths from System.
    private static List<String> placesMeant(final String folder, final String version) {
        final List<String> meant = new ArrayList<>(List.of(folder, parentOf(version)));
        if (folder.contains("/") && WriteGuard.ROLES.contains(folder.substring(folder.lastIndexOf('/') + 1))) {
            // The predecessor in a role subfolder its promotion kept (…/Acme/Indexing): the source's folder is
            // meant too (seen in e2e: v2's documentation and dashboard, given …/Acme, left out of Indexing/v2).
            meant.add(parentOf(folder));
        }
        return meant.stream().map(PromotionLayout::normalised).toList();
    }

    private static String normalised(final String place) {
        return String.join("/", WriteGuard.folderParts(place));
    }

    private static String parentOf(final String path) {
        final int slash = path.lastIndexOf('/');
        return slash < 0
                ? path
                : path.substring(0, slash);
    }


    // --------------------------------------------------------------------------------


    /// Each new version's folder.
    ///
    /// @param proposed The version folder by document UUID.
    /// @param beside   The version folders by predecessor's folder.
    record VersionFolders(Map<String, String> proposed, Map<String, Set<String>> beside) {

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
