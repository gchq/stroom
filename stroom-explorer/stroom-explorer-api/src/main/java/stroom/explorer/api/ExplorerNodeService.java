/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.explorer.api;

import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.PermissionInheritance;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ExplorerNodeService {

    void ensureRootNodeExists();

    void createNode(DocRef docRef,
                    DocRef destinationFolderRef,
                    PermissionInheritance permissionInheritance);

    void createNode(DocRef docRef,
                    DocRef destinationFolderRef,
                    PermissionInheritance permissionInheritance,
                    Set<String> tags);

    void copyNode(ExplorerNode sourceNode,
                  DocRef destDocRef,
                  DocRef destinationFolderRef,
                  PermissionInheritance permissionInheritance);

    void moveNode(DocRef docRef, DocRef destinationFolderRef, PermissionInheritance permissionInheritance);

    void renameNode(DocRef docRef);

    void updateTags(DocRef docRef, final Set<String> nodeTags);

    void deleteNode(DocRef docRef);

    ExplorerNode getRoot();

    /**
     * Get the root node containing another node
     */
    Optional<ExplorerNode> getNodeWithRoot(DocRef docRef);

    Optional<ExplorerNode> getNode(DocRef docRef);

    List<ExplorerNode> getPath(DocRef docRef);

    Optional<ExplorerNode> getParent(DocRef docRef);

    /**
     * This will return all descendants (including itself) for a doc ref
     *
     * @param folderRef The root of the tree query
     * @return A list of all descendants.
     */
    List<ExplorerNode> getDescendants(DocRef folderRef);

    /**
     * This will return the immediate children of a folder doc ref
     *
     * @param folderRef The root of the tree query
     * @return A list of the immediate children
     */
    List<ExplorerNode> getChildren(final DocRef folderRef);

    List<ExplorerNode> getNodesByName(ExplorerNode parent, String name);

    List<ExplorerNode> getNodesByNameAndType(ExplorerNode parent, String name, String type);

    // Used during testing.
    void deleteAllNodes();
}
