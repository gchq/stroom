/*
 * Copyright 2025 Crown Copyright
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

package stroom.pathways.client.component;

import stroom.pathways.client.ShowTracesEvent;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.pathway.PathNode;
import stroom.pathways.shared.pathway.PathNodeSequence;
import stroom.pathways.shared.pathway.Pathway;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;
import stroom.widget.util.client.MySingleSelectionModel;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/// Shows a pathway's graph ([PathwayTree]), read only as its document is, and shows the traces
/// that match the path to the chosen node.
public class PathwayTreeController extends Controller {

    private PathwayTree view;

    private PathwaysDoc pathwaysDoc;
    private Pathway pathway;

    /// Made by GIN; [#attach(PathwayTree)] gives it its graph.
    ///
    /// @param eventBus The application's event bus, which the traces are asked for on.
    @Inject
    public PathwayTreeController(final EventBus eventBus) {
        super(eventBus);
    }

    /// Attaches this to a graph, e.g. one a template declares. The graph is then this controller's
    /// widget.
    ///
    /// @param view The graph.
    /// @return This, for its owner to keep.
    public PathwayTreeController attach(final PathwayTree view) {
        this.view = Objects.requireNonNull(view, "view");
        initWidget(view);
        watch(Contexts.READ_ONLY, view::setReadOnly);
        view.setOnViewTraces(this::onViewTraces);
        return this;
    }

    /// Shows a pathway of a document.
    ///
    /// @param pathwaysDoc The document, whose traces store the traces are searched in.
    /// @param pathway     The pathway, or null for none.
    public void read(final PathwaysDoc pathwaysDoc,
                     final Pathway pathway) {
        this.pathwaysDoc = pathwaysDoc;
        this.pathway = pathway;
        view.read(pathway);
    }

    /// @return The chosen node.
    public MySingleSelectionModel<PathNode> getSelectionModel() {
        return view.getSelectionModel();
    }

    private void onViewTraces() {
        ShowTracesEvent.fire(
                this,
                pathwaysDoc.getTracesDocRef(),
                null,
                pathTo(pathway, view.getSelectionModel().getSelectedObject()));
    }

    /// The pathway cut down to the path from its root to a node: each node on the way keeps only
    /// the sequence that leads on to the next.
    ///
    /// @param pathway The pathway.
    /// @param node    The node, or null for the whole pathway.
    /// @return The pathway to the node.
    static Pathway pathTo(final Pathway pathway, final PathNode node) {
        final Map<String, String> parentMap = new HashMap<>();
        final Map<String, PathNodeSequence> pathNodeSequenceMap = new HashMap<>();
        final Map<String, PathNode> pathNodeMap = new HashMap<>();
        addToMap(pathway.getRoot(), parentMap, pathNodeSequenceMap, pathNodeMap);

        PathNode pathNode = node;
        PathNode root = pathNode;
        while (pathNode != null) {
            // Get parent sequence.
            final String parentSequenceUuid = parentMap.get(pathNode.getUuid());
            if (parentSequenceUuid != null) {
                final PathNodeSequence pathNodeSequence = pathNodeSequenceMap.get(parentSequenceUuid);
                // Get parent node.
                final String parentNodeUuid = parentMap.get(pathNodeSequence.getUuid());
                pathNode = pathNodeMap.get(parentNodeUuid);

                // The sequence holds the path cut down so far in place of the node it was cut from
                final String childUuid = root.getUuid();
                final PathNode child = root;
                final List<PathNode> nodes = NullSafe.list(pathNodeSequence.getNodes())
                        .stream()
                        .map(sequenceNode -> sequenceNode.getUuid().equals(childUuid)
                                ? child
                                : sequenceNode)
                        .collect(Collectors.toList());
                final PathNodeSequence leadingOn = pathNodeSequence.copy().nodes(nodes).build();
                root = pathNode.copy().targets(Collections.singletonList(leadingOn)).build();
            } else {
                pathNode = null;
            }
        }

        if (root != null) {
            return pathway.copy().root(root).build();
        } else {
            return pathway;
        }
    }

    private static void addToMap(final PathNode pathNode,
                                 final Map<String, String> parentMap,
                                 final Map<String, PathNodeSequence> pathNodeSequenceMap,
                                 final Map<String, PathNode> pathNodeMap) {
        pathNodeMap.put(pathNode.getUuid(), pathNode);
        NullSafe.list(pathNode.getTargets()).forEach(target -> {
            pathNodeSequenceMap.put(target.getUuid(), target);
            parentMap.put(target.getUuid(), pathNode.getUuid());
            NullSafe.list(target.getNodes()).forEach(node -> {
                parentMap.put(node.getUuid(), target.getUuid());
                addToMap(node, parentMap, pathNodeSequenceMap, pathNodeMap);
            });
        });
    }
}
