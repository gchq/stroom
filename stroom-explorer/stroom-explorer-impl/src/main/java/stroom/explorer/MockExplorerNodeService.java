package stroom.explorer;

import stroom.explorer.shared.PermissionInheritance;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.ExplorerNode;
import stroom.docref.DocRef;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class MockExplorerNodeService implements ExplorerNodeService {
    @Override
    public void createNode(final DocRef docRef, final DocRef destinationFolderRef, final PermissionInheritance permissionInheritance) {

    }

    @Override
    public void copyNode(final DocRef sourceDocRef, final DocRef destDocRef, final DocRef destinationFolderRef, final PermissionInheritance permissionInheritance) {

    }

    @Override
    public void moveNode(final DocRef docRef, final DocRef destinationFolderRef, final PermissionInheritance permissionInheritance) {

    }

    @Override
    public void renameNode(final DocRef docRef) {

    }

    @Override
    public void deleteNode(final DocRef docRef) {

    }

    @Override
    public Optional<ExplorerNode> getRoot() {
        return Optional.of(createTestNode());
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
        return new ExplorerNode("test", "test", "test", "test");
    }
}
