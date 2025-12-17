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

package stroom.explorer.impl;

import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.PermissionInheritance;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class MockExplorerNodeService implements ExplorerNodeService {

    @Override
    public void ensureRootNodeExists() {

    }

    @Override
    public void createNode(final DocRef docRef,
                           final DocRef destinationFolderRef,
                           final PermissionInheritance permissionInheritance) {

    }

    @Override
    public void createNode(final DocRef docRef,
                           final DocRef destinationFolderRef,
                           final PermissionInheritance permissionInheritance,
                           final Set<String> tags) {

    }

    @Override
    public void copyNode(final ExplorerNode sourceNode,
                         final DocRef destDocRef,
                         final DocRef destinationFolderRef,
                         final PermissionInheritance permissionInheritance) {

    }

    @Override
    public void moveNode(final DocRef docRef,
                         final DocRef destinationFolderRef,
                         final PermissionInheritance permissionInheritance) {

    }

    @Override
    public void renameNode(final DocRef docRef) {

    }

    @Override
    public void updateTags(final DocRef docRef, final Set<String> nodeTags) {

    }

    @Override
    public void deleteNode(final DocRef docRef) {

    }

    @Override
    public ExplorerNode getRoot() {
        return ExplorerConstants.SYSTEM_NODE;
    }

    @Override
    public Optional<ExplorerNode> getNodeWithRoot(final DocRef docRef) {
        return Optional.empty();
    }

    @Override
    public Optional<ExplorerNode> getNode(final DocRef docRef) {
        return Optional.of(ExplorerNode
                .builder()
                .type(docRef.getType())
                .uuid(docRef.getUuid())
                .name(docRef.getName())
                .tags(Set.of("test"))
                .build());
    }

    @Override
    public List<ExplorerNode> getPath(final DocRef docRef) {
        // Make sure returned list is mutable
        final ArrayList<ExplorerNode> path = new ArrayList<>(1);
        path.add(getRoot());
        return path;
    }

    @Override
    public Optional<ExplorerNode> getParent(final DocRef docRef) {
        return Optional.of(getRoot());
    }

    @Override
    public List<ExplorerNode> getDescendants(final DocRef folderRef) {
        // Make sure returned list is mutable
        return new ArrayList<>();
    }

    @Override
    public List<ExplorerNode> getChildren(final DocRef folderRef) {
        // Make sure returned list is mutable
        return new ArrayList<>();
    }

    @Override
    public List<ExplorerNode> getNodesByName(final ExplorerNode parent, final String name) {
        // Make sure returned list is mutable
        return new ArrayList<>();
    }

    @Override
    public List<ExplorerNode> getNodesByNameAndType(final ExplorerNode parent, final String name, final String type) {
        // Make sure returned list is mutable
        return new ArrayList<>();
    }

    @Override
    public void deleteAllNodes() {

    }
}
