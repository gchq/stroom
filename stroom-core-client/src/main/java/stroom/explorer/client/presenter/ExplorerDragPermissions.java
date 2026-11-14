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

package stroom.explorer.client.presenter;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerNodePermissions;
import stroom.explorer.shared.ExplorerResource;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Fetches and caches the user's permissions on the nodes involved in a drag, so that a drop can be
/// refused before the move/copy dialog opens.
///
/// The dragged nodes are fetched when the drag starts and each folder is fetched the first time the
/// drag passes over it. Until a response arrives the drop is treated as not permitted; browsers keep
/// firing `dragover` while the pointer is still, so the drop becomes available as soon as it arrives.
class ExplorerDragPermissions {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);

    private final RestFactory restFactory;
    private final TaskMonitorFactory taskMonitorFactory;
    private final Map<DocRef, ExplorerNodePermissions> permissions = new HashMap<>();
    private final Set<DocRef> requested = new HashSet<>();
    // Identifies the current drag so late responses from an earlier drag are ignored.
    private int dragId;

    ExplorerDragPermissions(final RestFactory restFactory,
                            final TaskMonitorFactory taskMonitorFactory) {
        this.restFactory = restFactory;
        this.taskMonitorFactory = taskMonitorFactory;
    }

    /// Forgets any previous drag and starts fetching permissions for the newly dragged nodes.
    ///
    /// @param draggedNodes The nodes being dragged.
    void start(final List<ExplorerNode> draggedNodes) {
        clear();
        fetch(draggedNodes);
    }

    /// Forgets all permissions, e.g. when a drag ends. Responses still in flight are ignored.
    void clear() {
        dragId++;
        permissions.clear();
        requested.clear();
    }

    /// Tests whether the user may drop `draggedNodes` onto `target`, fetching the target's permissions
    /// if they have not been requested yet.
    ///
    /// @param draggedNodes The nodes being dragged.
    /// @param target       The folder under the pointer.
    /// @param copy         True if the drop will copy, false if it will move.
    /// @return True if permitted, false if not permitted or not yet known.
    boolean isPermitted(final List<ExplorerNode> draggedNodes,
                        final ExplorerNode target,
                        final boolean copy) {
        if (target != null) {
            fetch(List.of(target));
        }
        return ExplorerDragDropRules.isPermittedDrop(draggedNodes, target, permissions, copy);
    }

    private void fetch(final List<ExplorerNode> nodes) {
        final List<ExplorerNode> toFetch = new ArrayList<>();
        for (final ExplorerNode node : nodes) {
            if (requested.add(node.getDocRef())) {
                toFetch.add(node);
            }
        }
        if (!toFetch.isEmpty()) {
            final int requestDragId = dragId;
            restFactory
                    .create(EXPLORER_RESOURCE)
                    .method(res -> res.fetchExplorerPermissions(toFetch))
                    .onSuccess(response -> {
                        if (requestDragId == dragId) {
                            for (final ExplorerNodePermissions nodePermissions : response) {
                                permissions.put(nodePermissions.getExplorerNode().getDocRef(), nodePermissions);
                            }
                        }
                    })
                    .taskMonitorFactory(taskMonitorFactory)
                    .exec();
        }
    }
}
