package stroom.explorer.server;

import org.springframework.stereotype.Component;
import stroom.entity.shared.PermissionInheritance;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.query.api.v2.DocRef;
import stroom.security.SecurityContext;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
class ExplorerNodeServiceImpl implements ExplorerNodeService {
    // TODO : This is a temporary means to set tags on nodes for the purpose of finding data source nodes.
    // TODO : The explorer will eventually allow a user to set custom tags and to find nodes searching by tag.
    private static Map<String, String> DEFAULT_TAG_MAP = new HashMap<>();

    static {
        DEFAULT_TAG_MAP.put("StatisticStore", "DataSource");
        DEFAULT_TAG_MAP.put(ExplorerConstants.ELASTIC_SEARCH, "DataSource");
        DEFAULT_TAG_MAP.put(ExplorerConstants.ANNOTATIONS, "DataSource");
        DEFAULT_TAG_MAP.put("StroomStatsStore", "DataSource");
        DEFAULT_TAG_MAP.put("Index", "DataSource");
    }

    private final ExplorerTreeDao explorerTreeDao;
    private final SecurityContext securityContext;

    @Inject
    ExplorerNodeServiceImpl(final ExplorerTreeDao explorerTreeDao,
                            final SecurityContext securityContext) {
        this.explorerTreeDao = explorerTreeDao;
        this.securityContext = securityContext;
    }

    @Override
    public void createNode(final DocRef docRef, final DocRef destinationFolderRef, final PermissionInheritance permissionInheritance) {
        // Set the permissions on the new item.
        if (permissionInheritance == null || PermissionInheritance.NONE.equals(permissionInheritance)) {
            // Make the new item owned by the current user.
            addDocumentPermissions(null, docRef, true);
        } else if (PermissionInheritance.COMBINED.equals(permissionInheritance) || PermissionInheritance.INHERIT.equals(permissionInheritance)) {
            // Copy permissions from the containing folder and make the new item owned by the current user.
            addDocumentPermissions(destinationFolderRef, docRef, true);
        }

        addNode(destinationFolderRef, docRef);
    }

    @Override
    public void copyNode(final DocRef sourceDocRef, final DocRef destDocRef, final DocRef destinationFolderRef, final PermissionInheritance permissionInheritance) {
        // Set the permissions on the copied item.
        if (permissionInheritance == null || PermissionInheritance.NONE.equals(permissionInheritance)) {
            // Copy permissions from the original and make the new item owned by the current user.
            addDocumentPermissions(sourceDocRef, destDocRef, true);
        } else if (PermissionInheritance.COMBINED.equals(permissionInheritance)) {
            // Copy permissions from the original and make the new item owned by the current user.
            addDocumentPermissions(sourceDocRef, destDocRef, true);
            // Add additional permissions from the folder of the new copy.
            addDocumentPermissions(destinationFolderRef, destDocRef, true);
        } else if (PermissionInheritance.INHERIT.equals(permissionInheritance)) {
            // Just add permissions from the folder of the new copy and make the new item owned by the current user.
            addDocumentPermissions(destinationFolderRef, destDocRef, true);
        }

        addNode(destinationFolderRef, destDocRef);
    }

    @Override
    public void moveNode(final DocRef docRef, final DocRef destinationFolderRef, final PermissionInheritance permissionInheritance) {
        // Set the permissions on the moved item.
        if (permissionInheritance != null) {
            if (PermissionInheritance.COMBINED.equals(permissionInheritance)) {
                // Add permissions from the new folder.
                addDocumentPermissions(destinationFolderRef, docRef, false);
            } else if (PermissionInheritance.INHERIT.equals(permissionInheritance)) {
                // Clear existing permissions from this item.
                securityContext.clearDocumentPermissions(docRef.getType(), docRef.getUuid());
                // Add permissions from the new folder and make the current user the owner.
                addDocumentPermissions(destinationFolderRef, docRef, true);
            }
        }

        moveNode(destinationFolderRef, docRef);
    }

    @Override
    public void renameNode(final DocRef docRef) {
        updateNode(docRef);
    }

    @Override
    public void deleteNode(final DocRef docRef) {
        try {
            final ExplorerTreeNode docNode = getNodeForDocRef(docRef);
            explorerTreeDao.remove(docNode);

        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private ExplorerTreeNode getNodeForDocRef(final DocRef docRef) {
        if (docRef == null) {
            return null;
        }

        return explorerTreeDao.findByUUID(docRef.getUuid());
    }

    @Override
    public ExplorerNode getRoot() {
        List<ExplorerTreeNode> roots = explorerTreeDao.getRoots();
        if (roots == null || roots.size() == 0) {
            createRoot();
            roots = explorerTreeDao.getRoots();
        }
        if (roots == null || roots.size() == 0) {
            return null;
        }
        return createExplorerNode(roots.get(0));
    }

    private synchronized void createRoot() {
        final List<ExplorerTreeNode> roots = explorerTreeDao.getRoots();
        if (roots == null || roots.size() == 0) {
            // Insert System root node.
            final DocRef root = ExplorerConstants.ROOT_DOC_REF;
            addNode(null, root);
        }
    }

    @Override
    public ExplorerNode getNode(final DocRef docRef) {
        final ExplorerTreeNode node = getNodeForDocRef(docRef);
        if (node == null) {
            return null;
        }

        return createExplorerNode(node);
    }

    @Override
    public List<ExplorerNode> getPath(final DocRef docRef) {
        final ExplorerTreeNode node = getNodeForDocRef(docRef);
        if (node == null) {
            return null;
        }

        final List<ExplorerTreeNode> path = explorerTreeDao.getPath(node);
        return path.stream()
                .map(this::createExplorerNode)
                .collect(Collectors.toList());
    }

    @Override
    public List<ExplorerNode> getDescendants(final DocRef docRef) {
        List<ExplorerTreeNode> nodes;

        if (docRef == null) {
            nodes = new ArrayList<>();
            final List<ExplorerTreeNode> roots = explorerTreeDao.getRoots();
            roots.forEach(root -> nodes.addAll(explorerTreeDao.getTree(root)));
        } else {
            final ExplorerTreeNode node = getNodeForDocRef(docRef);
            if (node == null) {
                nodes = Collections.emptyList();
            } else {
                nodes = explorerTreeDao.getTree(node);
            }
        }

        return nodes.stream()
                .map(this::createExplorerNode)
                .collect(Collectors.toList());
    }

    @Override
    public List<ExplorerNode> getNodesByName(final ExplorerNode parent, final String name) {
        ExplorerTreeNode parentNode = null;
        if (parent != null) {
            parentNode = explorerTreeDao.findByUUID(parent.getUuid());
            if (parentNode == null) {
                throw new RuntimeException("Unable to find parent node");
            }
        }

        final List<ExplorerTreeNode> children = explorerTreeDao.getChildren(parentNode);
        return children.stream()
                .filter(n -> name.equals(n.getName()))
                .map(this::createExplorerNode)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteAllNodes() {
        explorerTreeDao.removeAll();
        addNode(null, ExplorerConstants.ROOT_DOC_REF);
    }

    private void addNode(final DocRef parentFolderRef, final DocRef docRef) {
        try {
            final ExplorerTreeNode folderNode = getNodeForDocRef(parentFolderRef);
            final ExplorerTreeNode docNode = ExplorerTreeNode.create(docRef);
            setTags(docNode);
            explorerTreeDao.addChild(folderNode, docNode);

        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private void moveNode(final DocRef parentFolderRef, final DocRef docRef) {
        try {
            final ExplorerTreeNode folderNode = getNodeForDocRef(parentFolderRef);
            final ExplorerTreeNode docNode = getNodeForDocRef(docRef);
            explorerTreeDao.move(docNode, folderNode);

        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private void updateNode(final DocRef docRef) {
        try {
            final ExplorerTreeNode docNode = getNodeForDocRef(docRef);
            explorerTreeDao.update(docNode);

        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private void setTags(final ExplorerTreeNode explorerTreeNode) {
        if (explorerTreeNode != null) {
            explorerTreeNode.setTags(DEFAULT_TAG_MAP.get(explorerTreeNode.getType()));
        }
    }

    private void addDocumentPermissions(final DocRef source, final DocRef dest, final boolean owner) {
        String sourceType = null;
        String sourceUuid = null;
        String destType = null;
        String destUuid = null;

        if (source != null) {
            sourceType = source.getType();
            sourceUuid = source.getUuid();
        }

        if (dest != null) {
            destType = dest.getType();
            destUuid = dest.getUuid();
        }

        securityContext.addDocumentPermissions(sourceType, sourceUuid, destType, destUuid, owner);
    }

    private ExplorerNode createExplorerNode(final ExplorerTreeNode explorerTreeNode) {
        return new ExplorerNode(explorerTreeNode.getType(), explorerTreeNode.getUuid(), explorerTreeNode.getName(), explorerTreeNode.getTags());
    }
}
