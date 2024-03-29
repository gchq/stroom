package stroom.explorer.impl;

import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.PermissionInheritance;

import java.util.Collections;
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
    public void copyNode(final DocRef sourceDocRef,
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
        return createTestNode();
    }

    @Override
    public Optional<ExplorerNode> getNodeWithRoot(final DocRef docRef) {
        return Optional.empty();
    }

    @Override
    public Optional<ExplorerNode> getNode(final DocRef docRef) {
        return Optional.of(createTestNode());
    }

    @Override
    public List<ExplorerNode> getPath(final DocRef docRef) {
        return Collections.singletonList(createTestNode());
    }

    @Override
    public Optional<ExplorerNode> getParent(final DocRef docRef) {
        return Optional.empty();
    }

    @Override
    public List<ExplorerNode> getDescendants(final DocRef folderRef) {
        return Collections.emptyList();
    }

    @Override
    public List<ExplorerNode> getChildren(final DocRef folderRef) {
        return Collections.emptyList();
    }

    @Override
    public List<ExplorerNode> getNodesByName(final ExplorerNode parent, final String name) {
        return Collections.emptyList();
    }

    @Override
    public void deleteAllNodes() {

    }

    private ExplorerNode createTestNode() {
        return ExplorerNode
                .builder()
                .type("test")
                .uuid("test")
                .name("test")
                .tags(Set.of("test"))
                .build();
    }
}
