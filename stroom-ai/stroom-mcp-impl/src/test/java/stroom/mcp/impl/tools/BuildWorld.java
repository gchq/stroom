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

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.AiConfig;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.shared.AbstractDoc;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.BulkActionResult;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.PermissionInheritance;
import stroom.feed.shared.FeedDoc;
import stroom.pipeline.PipelineService;

import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/// An in-memory Stroom explorer for the tests of the write guard and the build record (as the Stroom MCP server's
/// `tests/fake_explorer.py`): folders and documents with tags, as Stroom's explorer services show them, and
/// Documentation docs that refuse a stale save. Each part is a Mockito mock answering from the tree.
final class BuildWorld {

    static final String WORKSPACE = "MCP Workspace";

    final Map<String, Node> nodes = new LinkedHashMap<>();
    final Map<String, Object> docs = new LinkedHashMap<>();
    final Set<String> hidden = new HashSet<>();
    final List<String> created = new ArrayList<>();
    final AtomicInteger saves = new AtomicInteger();
    final ExplorerService explorerService = Mockito.mock(ExplorerService.class);
    final ExplorerNodeService explorerNodeService = Mockito.mock(ExplorerNodeService.class);
    final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    final AiAuditLog auditLog = Mockito.mock(AiAuditLog.class);
    final PipelineService pipelineService = PipelineLayers.service();
    final PipelineShapes pipelineShapes = PipelineLayers.shapes(pipelineService, new AiConfig());
    // When a feed made from now on was created.
    long createTimeMs = 1_000_000L;
    // Runs before a save, as another node saving the same doc first would.
    Runnable beforeSave = () -> {
    };
    private final AtomicInteger next = new AtomicInteger();
    private final WriteGuard guard;
    private final BuildRecords records;

    BuildWorld() {
        nodes.put("0", new Node(ExplorerConstants.SYSTEM_TYPE, "0", ExplorerConstants.SYSTEM, null));
        final BuildRecords[] holder = new BuildRecords[1];
        guard = new WriteGuard(() -> explorerService, () -> explorerNodeService, documentAccess, AiConfig::new,
                () -> auditLog, () -> holder[0]);
        records = new BuildRecords(() -> guard, documentAccess, () -> explorerService, pipelineShapes);
        holder[0] = records;
        wireNodeService();
        wireExplorerService();
        wireDocumentAccess();
    }

    WriteGuard guard() {
        return guard;
    }

    BuildRecords records() {
        return records;
    }

    /// @return The folder at `System/a/b`, made if missing.
    Node folder(final String path) {
        Node node = nodes.get("0");
        final String[] names = path.split("/");
        for (int i = 1; i < names.length; i++) {
            final String name = names[i];
            final Node parent = node;
            node = parent.children.stream()
                    .map(nodes::get)
                    .filter(child -> ExplorerConstants.FOLDER_TYPE.equals(child.type) && child.name.equals(name))
                    .findFirst()
                    .orElseGet(() -> add(ExplorerConstants.FOLDER_TYPE, name, parent.uuid, null));
        }
        return node;
    }

    /// Adds a document or folder.
    Node add(final String type, final String name, final String parent, final String uuid, final String... tags) {
        final String id = uuid != null
                ? uuid
                : type.toLowerCase() + "-" + next.incrementAndGet();
        final Node node = new Node(type, id, name, parent);
        node.tags.addAll(List.of(tags));
        nodes.put(id, node);
        nodes.get(parent).children.add(id);
        if (DocumentationDoc.TYPE.equals(type)) {
            docs.put(id, DocumentationDoc.builder().uuid(id).name(name).version("v0").build());
        } else if (FeedDoc.TYPE.equals(type)) {
            docs.put(id, FeedDoc.builder().uuid(id).name(name).version("v0").createTimeMs(createTimeMs).build());
        }
        return node;
    }

    /// Adds a document to a build, tagged as the tools' own.
    DocRef inBuild(final String build, final String type, final String name, final String uuid) {
        final Node folder = folder("System/" + WORKSPACE + "/" + build);
        return add(type, name, folder.uuid, uuid, WriteGuard.MANAGED, WriteGuard.GENERATED).docRef();
    }

    Set<String> tags(final String uuid) {
        return nodes.get(uuid).tags;
    }

    /// Moves a node to another folder.
    void move(final String uuid, final String folderUuid) {
        final Node node = nodes.get(uuid);
        nodes.get(node.parent).children.remove(uuid);
        node.parent = folderUuid;
        nodes.get(folderUuid).children.add(uuid);
    }

    String body(final String uuid) {
        return ((DocumentationDoc) docs.get(uuid)).getData();
    }

    private void wireNodeService() {
        Mockito.when(explorerNodeService.getRoot()).thenAnswer(inv -> nodes.get("0").explorerNode());
        Mockito.when(explorerNodeService.getNode(any(DocRef.class))).thenAnswer(inv ->
                Optional.ofNullable(nodes.get(((DocRef) inv.getArgument(0)).getUuid())).map(Node::explorerNode));
        Mockito.when(explorerNodeService.getNodesByNameAndType(any(), anyString(), anyString())).thenAnswer(inv -> {
            final ExplorerNode parent = inv.getArgument(0);
            final String name = inv.getArgument(1);
            final String type = inv.getArgument(2);
            return children(parent.getUuid()).stream()
                    .filter(node -> node.getName().equals(name) && node.getType().equals(type))
                    .toList();
        });
        Mockito.when(explorerNodeService.getChildren(any(DocRef.class))).thenAnswer(inv ->
                children(((DocRef) inv.getArgument(0)).getUuid()));
    }

    private void wireExplorerService() {
        Mockito.when(explorerService.create(anyString(), anyString(), any(), any())).thenAnswer(inv -> {
            final String type = inv.getArgument(0);
            final String name = inv.getArgument(1);
            final ExplorerNode destination = inv.getArgument(2);
            final Node made = add(type, name, destination.getUuid(), null);
            created.add(type + ":" + name);
            return made.explorerNode();
        });
        Mockito.doAnswer(inv -> {
            final List<DocRef> refs = inv.getArgument(0);
            final Set<String> tags = inv.getArgument(1);
            refs.forEach(ref -> nodes.get(ref.getUuid()).tags.addAll(tags));
            return null;
        }).when(explorerService).addTags(any(), any());
        Mockito.doAnswer(inv -> {
            final List<DocRef> refs = inv.getArgument(0);
            final Set<String> tags = inv.getArgument(1);
            refs.forEach(ref -> nodes.get(ref.getUuid()).tags.removeAll(tags));
            return null;
        }).when(explorerService).removeTags(any(), any());
        Mockito.when(explorerService.delete(any())).thenAnswer(inv -> {
            final List<ExplorerNode> deleted = inv.getArgument(0);
            deleted.forEach(node -> delete(node.getUuid()));
            return new BulkActionResult(deleted, "");
        });
        Mockito.when(explorerService.move(any(), any(), any(PermissionInheritance.class))).thenAnswer(inv -> {
            final List<ExplorerNode> moved = inv.getArgument(0);
            final ExplorerNode destination = inv.getArgument(1);
            moved.forEach(node -> move(node.getUuid(), destination.getUuid()));
            return new BulkActionResult(moved, "");
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void wireDocumentAccess() {
        Mockito.when(documentAccess.canView(any())).thenAnswer(inv -> {
            final DocRef ref = inv.getArgument(0);
            return ref != null && !hidden.contains(ref.getUuid());
        });
        Mockito.when(documentAccess.node(any())).thenAnswer(inv ->
                explorerNodeService.getNode(inv.getArgument(0)));
        Mockito.when(documentAccess.children(any())).thenAnswer(inv ->
                children(((DocRef) inv.getArgument(0)).getUuid()).stream()
                        .filter(node -> !hidden.contains(node.getUuid()))
                        .toList());
        Mockito.when(documentAccess.path(any())).thenAnswer(inv -> path(((DocRef) inv.getArgument(0)).getUuid()));
        Mockito.when(documentAccess.findDocRef(anyString())).thenAnswer(inv ->
                Optional.ofNullable(nodes.get((String) inv.getArgument(0)))
                        .filter(node -> !hidden.contains(node.uuid))
                        .map(Node::docRef));
        Mockito.when(documentAccess.read(any(DocRef.class), any(Class.class))).thenAnswer(inv -> {
            final DocRef ref = inv.getArgument(0);
            final Class<?> type = inv.getArgument(1);
            return Optional.ofNullable(hidden.contains(ref.getUuid())
                            ? null
                            : docs.get(ref.getUuid()))
                    .filter(type::isInstance);
        });
        Mockito.when(documentAccess.read(any(DocRef.class))).thenAnswer(inv ->
                Optional.ofNullable(docs.get(((DocRef) inv.getArgument(0)).getUuid())));
        final DocumentActionHandler handler = new DocumentActionHandler<AbstractDoc>() {
            @Override
            public AbstractDoc readDocument(final DocRef docRef) {
                return (AbstractDoc) docs.get(docRef.getUuid());
            }

            @Override
            public AbstractDoc writeDocument(final AbstractDoc document) {
                beforeSave.run();
                final AbstractDoc current = (AbstractDoc) docs.get(document.getUuid());
                if (current == null || !current.getVersion().equals(document.getVersion())) {
                    throw new RuntimeException("DocRef{uuid='" + document.getUuid() + "'} has been modified by "
                                               + "another user");
                }
                final AbstractDoc saved = document instanceof final DocumentationDoc doc
                        ? doc.copy().version("v" + saves.incrementAndGet()).build()
                        : document;
                docs.put(document.getUuid(), saved);
                return saved;
            }

            @Override
            public String getType() {
                return DocumentationDoc.TYPE;
            }
        };
        Mockito.when(documentAccess.handler(anyString())).thenReturn(Optional.of(handler));
    }

    private List<ExplorerNode> children(final String uuid) {
        final Node node = nodes.get(uuid);
        if (node == null) {
            return List.of();
        }
        return node.children.stream().map(nodes::get).map(Node::explorerNode).toList();
    }

    private String path(final String uuid) {
        final List<String> names = new ArrayList<>();
        Node node = nodes.get(uuid);
        while (node != null && node.parent != null) {
            node = nodes.get(node.parent);
            if (node != null && node.parent != null) {
                names.addFirst(node.name);
            }
        }
        return "/" + String.join("/", names);
    }

    private void delete(final String uuid) {
        final Node node = nodes.get(uuid);
        if (node == null) {
            return;
        }
        new ArrayList<>(node.children).forEach(this::delete);
        nodes.get(node.parent).children.remove(uuid);
        nodes.remove(uuid);
        docs.remove(uuid);
    }


    // --------------------------------------------------------------------------------


    /// A folder or document in the tree.
    static final class Node {

        final String type;
        final String uuid;
        final String name;
        final Set<String> tags = new LinkedHashSet<>();
        final List<String> children = new ArrayList<>();
        String parent;

        Node(final String type, final String uuid, final String name, final String parent) {
            this.type = type;
            this.uuid = uuid;
            this.name = name;
            this.parent = parent;
        }

        DocRef docRef() {
            return new DocRef(type, uuid, name);
        }

        ExplorerNode explorerNode() {
            return ExplorerNode.builder().docRef(docRef()).tags(new LinkedHashSet<>(tags)).build();
        }
    }
}
