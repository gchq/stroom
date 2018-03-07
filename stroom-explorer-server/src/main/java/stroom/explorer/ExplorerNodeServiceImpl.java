package stroom.explorer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.PermissionInheritance;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.query.api.v2.DocRef;
import stroom.security.SecurityContext;

import javax.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

class ExplorerNodeServiceImpl implements ExplorerNodeService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExplorerNodeServiceImpl.class);

    // TODO : This is a temporary means to set tags on nodes for the purpose of finding data source nodes.
    // TODO : The explorer will eventually allow a user to set custom tags and to find nodes searching by tag.
    private static final Map<String, String> DEFAULT_TAG_MAP = new HashMap<>();
    static {
        DEFAULT_TAG_MAP.put("StatisticStore", "DataSource");
        DEFAULT_TAG_MAP.put("ElasticIndex", "DataSource");
        DEFAULT_TAG_MAP.put("AnnotationsIndex", "DataSource");
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
        // Ensure permission inheritance is set to something.
        PermissionInheritance perms = permissionInheritance;
        if (perms == null) {
            LOGGER.warn("Null permission inheritance supplied for create operation");
            perms = PermissionInheritance.DESTINATION;
        }

        // Set the permissions on the new item.
        try {
            switch (perms) {
                case NONE:
                    // Make the new item owned by the current user.
                    addDocumentPermissions(null, docRef, true);
                    break;
                case DESTINATION:
                    // Copy permissions from the containing folder and make the new item owned by the current user.
                    addDocumentPermissions(destinationFolderRef, docRef, true);
                    break;
                default:
                    LOGGER.error("Unexpected permission inheritance '" + perms + "' supplied for create operation");
                    break;
            }
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

        addNode(destinationFolderRef, docRef);
    }

    @Override
    public void copyNode(final DocRef sourceDocRef, final DocRef destDocRef, final DocRef destinationFolderRef, final PermissionInheritance permissionInheritance) {
        // Ensure permission inheritance is set to something.
        PermissionInheritance perms = permissionInheritance;
        if (perms == null) {
            LOGGER.warn("Null permission inheritance supplied for copy operation");
            perms = PermissionInheritance.DESTINATION;
        }

        // Set the permissions on the copied item.
        try {
            switch (perms) {
                case NONE:
                    // Ignore original permissions, ignore permissions of the destination folder, just make the new item owned by the current user.
                    addDocumentPermissions(null, destDocRef, true);
                    break;
                case SOURCE:
                    // Copy permissions from the original, ignore permissions of the destination folder, and make the new item owned by the current user.
                    addDocumentPermissions(sourceDocRef, destDocRef, true);
                    break;
                case DESTINATION:
                    // Ignore permissions of the original, add permissions of the destination folder, and make the new item owned by the current user.
                    addDocumentPermissions(destinationFolderRef, destDocRef, true);
                    break;
                case COMBINED:
                    // Copy permissions from the original, add permissions of the destination folder, and make the new item owned by the current user.
                    addDocumentPermissions(sourceDocRef, destDocRef, true);
                    addDocumentPermissions(destinationFolderRef, destDocRef, true);
                    break;
                default:
                    LOGGER.error("Unexpected permission inheritance '" + perms + "' supplied for copy operation");
                    break;
            }
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

        addNode(destinationFolderRef, destDocRef);
    }

    @Override
    public void moveNode(final DocRef docRef, final DocRef destinationFolderRef, final PermissionInheritance permissionInheritance) {
        // Ensure permission inheritance is set to something.
        PermissionInheritance perms = permissionInheritance;
        if (perms == null) {
            LOGGER.warn("Null permission inheritance supplied for copy operation");
            perms = PermissionInheritance.DESTINATION;
        }

        // Set the permissions on the moved item.
        try {
            switch (perms) {
                case NONE:
                    // Remove all current permissions, ignore permissions of the destination folder, just make the new item owned by the current user.
                    clearDocumentPermissions(docRef);
                    addDocumentPermissions(null, docRef, true);
                    break;
                case SOURCE:
                    // We are keeping the permissions that we already have so do nothing.
                    break;
                case DESTINATION:
                    // Remove all current permissions, add permissions of the destination folder, and make the new item owned by the current user.
                    clearDocumentPermissions(docRef);
                    addDocumentPermissions(destinationFolderRef, docRef, true);
                    break;
                case COMBINED:
                    // Keep all current permissions, add permissions of the destination folder, and make the new item owned by the current user.
                    addDocumentPermissions(destinationFolderRef, docRef, true);
                    break;
                default:
                    LOGGER.error("Unexpected permission inheritance '" + perms + "' supplied for move operation");
                    break;
            }
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
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
            getNodeForDocRef(docRef).ifPresent(explorerTreeDao::remove);

        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private Optional<ExplorerTreeNode> getNodeForDocRef(final DocRef docRef) {
        return Optional.ofNullable(docRef)
                .map(DocRef::getUuid)
                .map(explorerTreeDao::findByUUID);
    }

    @Override
    public Optional<ExplorerNode> getRoot() {
        final List<ExplorerTreeNode> roots = Optional
                .ofNullable(explorerTreeDao.getRoots())
                .filter(r -> r.size() > 0)
                .orElseGet(() -> {
                    createRoot();
                    return explorerTreeDao.getRoots();
                });

        return Optional.ofNullable(roots)
                .filter(r -> r.size() > 0)
                .map(r -> r.get(0))
                .map(this::createExplorerNode);
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
    public Optional<ExplorerNode> getNode(final DocRef docRef) {
        return getNodeForDocRef(docRef)
                .map(this::createExplorerNode);
    }

    @Override
    public List<ExplorerNode> getPath(final DocRef docRef) {
        return getNodeForDocRef(docRef)
                .map(explorerTreeDao::getPath).orElse(Collections.emptyList()).stream()
                .map(this::createExplorerNode)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<ExplorerNode> getParent(DocRef docRef) {
        return getNodeForDocRef(docRef)
                .map(explorerTreeDao::getParent)
                .map(this::createExplorerNode);
    }

    @Override
    public List<ExplorerNode> getDescendants(final DocRef docRef) {
        return getDescendants(docRef, explorerTreeDao::getTree);
    }

    @Override
    public List<ExplorerNode> getChildren(final DocRef docRef) {
        return getDescendants(docRef, explorerTreeDao::getChildren);
    }

    /**
     * General form a function that returns a list of explorer nodes from the tree DAO
     *
     * @param folderDocRef  The root doc ref of the query
     * @param fetchFunction The function to call to get the list of ExplorerTreeNodes given a root ExplorerTreeNode
     * @return The list of converted ExplorerNodes
     */
    private List<ExplorerNode> getDescendants(final DocRef folderDocRef,
                                              final Function<ExplorerTreeNode, List<ExplorerTreeNode>> fetchFunction) {
        if (folderDocRef == null) {
            return explorerTreeDao.getRoots().stream()
                    .map(fetchFunction)
                    .flatMap(List::stream) // potential multiple roots returned from tree DAO
                    .map(this::createExplorerNode)
                    .collect(Collectors.toList());
        } else {
            return getNodeForDocRef(folderDocRef)
                    .map(fetchFunction)
                    .map(d -> d.stream().map(this::createExplorerNode)
                            .collect(Collectors.toList()))
                    .orElse(Collections.emptyList());
        }
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
            final ExplorerTreeNode folderNode = getNodeForDocRef(parentFolderRef).orElse(null);
            final ExplorerTreeNode docNode = ExplorerTreeNode.create(docRef);
            setTags(docNode);
            explorerTreeDao.addChild(folderNode, docNode);

        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void moveNode(final DocRef parentFolderRef, final DocRef docRef) {
        try {
            final ExplorerTreeNode folderNode = getNodeForDocRef(parentFolderRef).orElse(null);
            final ExplorerTreeNode docNode = getNodeForDocRef(docRef).orElse(null);
            explorerTreeDao.move(docNode, folderNode);

        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void updateNode(final DocRef docRef) {
        try {
            final ExplorerTreeNode docNode = getNodeForDocRef(docRef).orElse(null);
            if (docNode != null) {
                docNode.setType(docRef.getType());
                docNode.setUuid(docRef.getUuid());
                docNode.setName(docRef.getName());
                explorerTreeDao.update(docNode);
            }
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
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

    private void clearDocumentPermissions(final DocRef docRef) {
        securityContext.clearDocumentPermissions(docRef.getType(), docRef.getUuid());
    }

    private ExplorerNode createExplorerNode(final ExplorerTreeNode explorerTreeNode) {
        return new ExplorerNode.Builder()
                .type(explorerTreeNode.getType())
                .uuid(explorerTreeNode.getUuid())
                .name(explorerTreeNode.getName())
                .tags(explorerTreeNode.getTags())
                .build();
    }
}
