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


package stroom.gwt.workbench.client.widgets.tree;

import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.gwt.workbench.client.app.rest.JsonValues;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// A fake of Stroom's explorer tree service (`POST /explorer/v2/fetchExplorerNodes`), answering
/// the requests of Stroom's real `ExplorerTreeModel` from a fixed tree of folders and documents.
///
/// It does what the server's `ExplorerServiceImpl` does that the GWT tree depends on:
///
/// * it returns the children of the open folders only, so opening a folder fetches again;
/// * it opens the folders above the request's `minDepth` (the first fetch asks for 1, so the
///   roots arrive open) and the ancestors of the `ensureVisible` nodes, reporting them as
///   `openedItems`;
/// * it filters by the included types (keeping the folders that hold a matching document) and by
///   name (a case-insensitive contains, marking the matches `FM` and the folders holding them
///   `FN`, which it opens temporarily as `temporaryOpenedItems`);
/// * when alerts are asked for, it returns each node's issues (`nodeInfoList`) and flags the
///   folders above them (`I`).
///
/// Plain Java, so it is unit tested on the JVM.
public final class ExplorerFixture {

    /// The type of Stroom's folders.
    public static final String FOLDER = "Folder";

    private static final String LEAF_FLAG = "L";
    private static final String FOLDER_FLAG = "F";
    private static final String FILTER_MATCH_FLAG = "FM";
    private static final String FILTER_NON_MATCH_FLAG = "FN";
    private static final String DESCENDANT_NODE_INFO_FLAG = "I";

    private final List<Node> roots;
    private final Map<String, Node> nodesByUuid = new HashMap<>();
    private final Map<String, Node> parents = new HashMap<>();
    private int openToDepth;

    /// @param roots The root nodes, e.g. a single `System` folder. Their uuids, depths and root
    ///              uuids are set here.
    public ExplorerFixture(final Node... roots) {
        this.roots = Arrays.asList(roots);
        final Map<String, Integer> counters = new HashMap<>();
        for (final Node root : this.roots) {
            index(root, null, root, 0, counters);
        }
    }

    /// Creates a folder.
    ///
    /// @param name     The folder's name.
    /// @param children The folder's contents.
    /// @return The folder.
    public static Node folder(final String name, final Node... children) {
        return new Node(name, FOLDER, true, Arrays.asList(children));
    }

    /// Creates a folder of a type other than `Folder`, e.g. `System`.
    ///
    /// @param name     The folder's name.
    /// @param type     The folder's type.
    /// @param children The folder's contents.
    /// @return The folder.
    public static Node folder(final String name, final String type, final Node... children) {
        return new Node(name, type, true, Arrays.asList(children));
    }

    /// Creates a document.
    ///
    /// @param name The document's name.
    /// @param type The document's type, e.g. `Feed`.
    /// @return The document.
    public static Node doc(final String name, final String type) {
        return new Node(name, type, false, Collections.emptyList());
    }

    /// Makes the first fetch (whose `minDepth` is more than 0) open every folder above the given
    /// depth, as the real server's results can, e.g. `2` for a root and its child folders.
    ///
    /// @param depth The depth above which folders are opened.
    /// @return This fixture.
    public ExplorerFixture openToDepth(final int depth) {
        this.openToDepth = depth;
        return this;
    }

    /// Finds a node by name.
    ///
    /// @param name The node's name, which the fixtures keep unique.
    /// @return The node.
    /// @throws IllegalArgumentException If there is no node with the name.
    public Node get(final String name) {
        for (final Node node : nodesByUuid.values()) {
            if (node.name.equals(name)) {
                return node;
            }
        }
        throw new IllegalArgumentException("No node named " + name);
    }

    /// Gets a node as Stroom's client holds it, e.g. to ask the tree to show it.
    ///
    /// @param name The node's name.
    /// @return The node, with its key (type, uuid and root uuid) and name.
    public ExplorerNode toExplorerNode(final String name) {
        final Node node = get(name);
        return ExplorerNode.builder()
                .type(node.type)
                .uuid(node.uuid)
                .name(node.name)
                .rootNodeUuid(node.rootNodeUuid)
                .build();
    }

    /// Gets a node as a document reference.
    ///
    /// @param name The node's name.
    /// @return The node's reference.
    public DocRef toDocRef(final String name) {
        final Node node = get(name);
        return DocRef.builder()
                .type(node.type)
                .uuid(node.uuid)
                .name(node.name)
                .build();
    }

    /// Answers a `fetchExplorerNodes` request.
    ///
    /// @param requestJson The request's body, a `FetchExplorerNodesRequest`.
    /// @return The reply's body, a `FetchExplorerNodeResult`.
    public String fetch(final String requestJson) {
        final Map<?, ?> request = asMap(JsonValues.parse(requestJson));
        final Map<?, ?> filter = asMap(request.get("filter"));
        final Set<String> includedTypes = asStringSet(filter.get("includedTypes"));
        final String nameFilter = normaliseNameFilter(filter.get("nameFilter"));
        final int minDepth = asInt(request.get("minDepth"));
        final boolean showAlerts = Boolean.TRUE.equals(request.get("showAlerts"));

        final Set<String> open = new LinkedHashSet<>(keysOf(request.get("openItems")));
        final List<Node> opened = new ArrayList<>();
        for (final String uuid : keysOf(request.get("ensureVisible"))) {
            Node parent = parents.get(uuid);
            final List<Node> ancestors = new ArrayList<>();
            while (parent != null) {
                ancestors.add(0, parent);
                parent = parents.get(parent.uuid);
            }
            openAll(ancestors, open, opened);
        }
        final int depth = minDepth > 0
                ? Math.max(minDepth, openToDepth)
                : 0;
        openAbove(roots, depth, open, opened);

        final Set<String> temporaryOpened = new LinkedHashSet<>();
        final Context context = new Context(includedTypes, nameFilter, showAlerts, open, temporaryOpened);
        final StringBuilder sb = new StringBuilder("{\"rootNodes\":");
        appendNodes(sb, roots, context);
        sb.append(",\"openedItems\":[");
        for (int i = 0; i < opened.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            appendKey(sb, opened.get(i));
        }
        sb.append("],\"temporaryOpenedItems\":[");
        boolean first = true;
        for (final String uuid : temporaryOpened) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            appendKey(sb, nodesByUuid.get(uuid));
        }
        sb.append("],\"qualifiedFilterInput\":").append(quote(nameFilter)).append('}');
        return sb.toString();
    }

    /// Answers a `getFromDocRef` request, which gets a document's node (with its root's uuid).
    ///
    /// @param docRefJson The request's body, a `DocRef`.
    /// @return The reply's body: the node (without its children) or, for a document that isn't
    /// in the tree, a node made from the reference.
    public String getFromDocRef(final String docRefJson) {
        final Map<?, ?> docRef = asMap(JsonValues.parse(docRefJson));
        final Node node = nodesByUuid.get(String.valueOf(docRef.get("uuid")));
        if (node == null) {
            final String type = (String) docRef.get("type");
            final String uuid = (String) docRef.get("uuid");
            return "{\"type\":" + quote(type)
                   + ",\"uuid\":" + quote(uuid)
                   + ",\"name\":" + quote((String) docRef.get("name"))
                   + ",\"uniqueKey\":{\"type\":" + quote(type) + ",\"uuid\":" + quote(uuid) + "}}";
        }
        final StringBuilder sb = new StringBuilder();
        appendNodeFields(sb, node, List.of(node.folder
                ? FOLDER_FLAG
                : LEAF_FLAG));
        return sb.append('}').toString();
    }

    private void index(final Node node,
                       final Node parent,
                       final Node root,
                       final int depth,
                       final Map<String, Integer> counters) {
        if (node.uuid == null) {
            final String prefix = node.type.toLowerCase(Locale.ROOT);
            final int count = counters.merge(prefix, 1, Integer::sum);
            node.uuid = prefix + "-" + count;
        }
        node.depth = depth;
        node.rootNodeUuid = root == node
                ? node.uuid
                : root.uuid;
        nodesByUuid.put(node.uuid, node);
        if (parent != null) {
            parents.put(node.uuid, parent);
        }
        for (final Node child : node.children) {
            index(child, node, root, depth + 1, counters);
        }
    }

    private static void openAll(final List<Node> nodes, final Set<String> open, final List<Node> opened) {
        for (final Node node : nodes) {
            if (open.add(node.uuid)) {
                opened.add(node);
            }
        }
    }

    private static void openAbove(final List<Node> nodes,
                                  final int depth,
                                  final Set<String> open,
                                  final List<Node> opened) {
        for (final Node node : nodes) {
            if (node.folder && node.depth < depth) {
                if (open.add(node.uuid)) {
                    opened.add(node);
                }
                openAbove(node.children, depth, open, opened);
            }
        }
    }

    /// Appends the nodes that pass the filters, as JSON.
    private void appendNodes(final StringBuilder sb, final List<Node> nodes, final Context context) {
        sb.append('[');
        boolean any = false;
        for (final Node node : nodes) {
            if (isIncluded(node, context)) {
                if (any) {
                    sb.append(',');
                }
                any = true;
                appendNode(sb, node, context);
            }
        }
        sb.append(']');
    }

    private void appendNode(final StringBuilder sb, final Node node, final Context context) {
        final List<String> flags = new ArrayList<>();
        flags.add(node.folder
                ? FOLDER_FLAG
                : LEAF_FLAG);
        if (context.nameFilter != null) {
            flags.add(matchesName(node, context.nameFilter)
                    ? FILTER_MATCH_FLAG
                    : FILTER_NON_MATCH_FLAG);
        }
        if (context.showAlerts && node.folder && hasDescendantInfo(node)) {
            flags.add(DESCENDANT_NODE_INFO_FLAG);
        }

        appendNodeFields(sb, node, flags);
        if (context.showAlerts && !node.infos.isEmpty()) {
            sb.append(",\"nodeInfoList\":[");
            for (int i = 0; i < node.infos.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append("{\"severity\":").append(quote(node.infos.get(i)[0]))
                        .append(",\"description\":").append(quote(node.infos.get(i)[1])).append('}');
            }
            sb.append(']');
        }
        if (node.folder) {
            // A name filter opens the folders holding matches, temporarily
            final boolean temporarilyOpen = context.nameFilter != null && hasIncludedChild(node, context);
            if (temporarilyOpen && !context.open.contains(node.uuid)) {
                context.temporaryOpened.add(node.uuid);
            }
            if (context.open.contains(node.uuid) || temporarilyOpen) {
                sb.append(",\"children\":");
                appendNodes(sb, node.children, context);
            }
        }
        sb.append('}');
    }

    /// Appends a node's fields, leaving its object open for more.
    private static void appendNodeFields(final StringBuilder sb, final Node node, final List<String> flags) {
        sb.append("{\"type\":").append(quote(node.type))
                .append(",\"uuid\":").append(quote(node.uuid))
                .append(",\"name\":").append(quote(node.name))
                .append(",\"depth\":").append(node.depth)
                .append(",\"rootNodeUuid\":").append(quote(node.rootNodeUuid))
                .append(",\"uniqueKey\":");
        appendKey(sb, node);
        sb.append(",\"nodeFlags\":[");
        for (int i = 0; i < flags.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(quote(flags.get(i)));
        }
        sb.append(']');
    }

    private boolean hasIncludedChild(final Node folder, final Context context) {
        for (final Node child : folder.children) {
            if (isIncluded(child, context)) {
                return true;
            }
        }
        return false;
    }

    private boolean isIncluded(final Node node, final Context context) {
        final boolean typeIncluded = context.includedTypes == null
                                     || context.includedTypes.contains(node.type);
        final boolean nameIncluded = context.nameFilter == null || matchesName(node, context.nameFilter);
        if (typeIncluded && nameIncluded) {
            return true;
        }
        // A folder is shown if it holds something that is
        return node.folder && hasIncludedChild(node, context);
    }

    private static boolean matchesName(final Node node, final String nameFilter) {
        return node.name.toLowerCase(Locale.ROOT).contains(nameFilter);
    }

    private static boolean hasDescendantInfo(final Node folder) {
        for (final Node child : folder.children) {
            if (!child.infos.isEmpty() || hasDescendantInfo(child)) {
                return true;
            }
        }
        return false;
    }

    private static void appendKey(final StringBuilder sb, final Node node) {
        sb.append("{\"type\":").append(quote(node.type))
                .append(",\"uuid\":").append(quote(node.uuid))
                .append(",\"rootNodeUuid\":").append(quote(node.rootNodeUuid)).append('}');
    }

    private static List<String> keysOf(final Object keys) {
        final List<String> uuids = new ArrayList<>();
        if (keys instanceof List) {
            for (final Object key : (List<?>) keys) {
                final Object uuid = asMap(key).get("uuid");
                if (uuid != null) {
                    uuids.add(uuid.toString());
                }
            }
        }
        return uuids;
    }

    private static String normaliseNameFilter(final Object nameFilter) {
        if (nameFilter == null || nameFilter.toString().trim().isEmpty()) {
            return null;
        }
        return nameFilter.toString().trim().toLowerCase(Locale.ROOT);
    }

    private static Map<?, ?> asMap(final Object value) {
        return value instanceof Map
                ? (Map<?, ?>) value
                : Collections.emptyMap();
    }

    private static Set<String> asStringSet(final Object value) {
        if (!(value instanceof List) || ((List<?>) value).isEmpty()) {
            return null;
        }
        final Set<String> set = new LinkedHashSet<>();
        for (final Object item : (List<?>) value) {
            set.add(String.valueOf(item));
        }
        return set;
    }

    private static int asInt(final Object value) {
        return value instanceof Number
                ? ((Number) value).intValue()
                : 0;
    }

    /// @param text Some text, or null.
    /// @return The text as a JSON string, or `null`.
    static String quote(final String text) {
        if (text == null) {
            return "null";
        }
        final StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        final String hex = Integer.toHexString(c);
                        sb.append("\\u");
                        for (int pad = hex.length(); pad < 4; pad++) {
                            sb.append('0');
                        }
                        sb.append(hex);
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"').toString();
    }


    // --------------------------------------------------------------------------------


    /// A folder or document in the fixture tree.
    public static final class Node {

        private final String name;
        private final String type;
        private final boolean folder;
        private final List<Node> children;
        private final List<String[]> infos = new ArrayList<>();
        private String uuid;
        private String rootNodeUuid;
        private int depth;

        private Node(final String name, final String type, final boolean folder, final List<Node> children) {
            this.name = Objects.requireNonNull(name);
            this.type = Objects.requireNonNull(type);
            this.folder = folder;
            this.children = children;
        }

        /// Sets the node's uuid, rather than the fixture numbering it.
        ///
        /// @param uuid The uuid.
        /// @return This node.
        public Node withUuid(final String uuid) {
            this.uuid = Objects.requireNonNull(uuid);
            return this;
        }

        /// Adds an issue, shown when the tree shows alerts.
        ///
        /// @param severity    The severity, e.g. `WARNING` or `ERROR`.
        /// @param description The description.
        /// @return This node.
        public Node withInfo(final String severity, final String description) {
            infos.add(new String[]{severity, description});
            return this;
        }

        /// @return The node's name.
        public String getName() {
            return name;
        }

        /// @return The node's type, e.g. `Feed`.
        public String getType() {
            return type;
        }

        /// @return The node's uuid, set by the fixture, e.g. `feed-1`.
        public String getUuid() {
            return uuid;
        }

        /// @return The uuid of the node's root.
        public String getRootNodeUuid() {
            return rootNodeUuid;
        }

        /// @return The node's depth, 0 for a root.
        public int getDepth() {
            return depth;
        }

        /// @return True for a folder.
        public boolean isFolder() {
            return folder;
        }
    }


    // --------------------------------------------------------------------------------


    /// The filters of one request, and the folders open for it.
    private static final class Context {

        private final Set<String> includedTypes;
        private final String nameFilter;
        private final boolean showAlerts;
        private final Set<String> open;
        private final Set<String> temporaryOpened;

        private Context(final Set<String> includedTypes,
                        final String nameFilter,
                        final boolean showAlerts,
                        final Set<String> open,
                        final Set<String> temporaryOpened) {
            this.includedTypes = includedTypes;
            this.nameFilter = nameFilter;
            this.showAlerts = showAlerts;
            this.open = open;
            this.temporaryOpened = temporaryOpened;
        }
    }
}
