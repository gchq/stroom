/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.explorer.api;

import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.PermissionInheritance;

import java.util.List;
import java.util.Optional;

public interface ExplorerNodeService {
    void createNode(DocRef docRef, DocRef destinationFolderRef, PermissionInheritance permissionInheritance);

    void copyNode(DocRef sourceDocRef, DocRef destDocRef, DocRef destinationFolderRef, PermissionInheritance permissionInheritance);

    void moveNode(DocRef docRef, DocRef destinationFolderRef, PermissionInheritance permissionInheritance);

    void renameNode(DocRef docRef);

    void deleteNode(DocRef docRef);

    Optional<ExplorerNode> getRoot();

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

    // Used during testing.
    void deleteAllNodes();
}
