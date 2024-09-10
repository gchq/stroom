package stroom.explorer.impl;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.PermissionInheritance;
import stroom.security.api.DocumentPermissionService;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.NullSafe;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

class ExplorerNodeServiceImpl implements ExplorerNodeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExplorerNodeServiceImpl.class);

    private static final String LOCK_NAME = "ExplorerNodeService";

    private final ExplorerTreeDao explorerTreeDao;
    private final DocumentPermissionService documentPermissionService;
    private final SecurityContext securityContext;
    private final ClusterLockService clusterLockService;

    @Inject
    ExplorerNodeServiceImpl(final ExplorerTreeDao explorerTreeDao,
                            final DocumentPermissionService documentPermissionService,
                            final SecurityContext securityContext,
                            final ClusterLockService clusterLockService) {
        this.explorerTreeDao = explorerTreeDao;
        this.documentPermissionService = documentPermissionService;
        this.securityContext = securityContext;
        this.clusterLockService = clusterLockService;
    }

    @Override
    public void ensureRootNodeExists() {
        final ExplorerTreeNode rootNode = ExplorerTreeNode.create(ExplorerConstants.SYSTEM_DOC_REF);
        if (explorerTreeDao.doesNodeExist(rootNode)) {
            LOGGER.debug("Root node {} already exists", rootNode);
        } else {
            // Doesn't exist so get a cluster lock then check again
            // in case another node beat us to it
            clusterLockService.lock(LOCK_NAME, () -> {
                if (!explorerTreeDao.doesNodeExist(rootNode)) {
                    LOGGER.info("Creating explorer root node in the database {}", rootNode);
                    try {
                        explorerTreeDao.addChild(null, rootNode);
                    } catch (Exception e) {
                        throw new RuntimeException("Error creating explorer root node " + rootNode, e);
                    }
                }
            });
        }
    }

    @Override
    public void createNode(final DocRef docRef,
                           final DocRef destinationFolderRef,
                           final PermissionInheritance permissionInheritance) {
        createNode(docRef, destinationFolderRef, permissionInheritance, null);
    }

    @Override
    public void createNode(final DocRef docRef,
                           final DocRef destinationFolderRef,
                           final PermissionInheritance permissionInheritance,
                           final Set<String> tags) {
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
                    addDocumentPermissions(null, docRef, true, false);
                    break;
                case DESTINATION:
                    // Copy permissions from the containing folder and make the new item owned by the current user.
                    addDocumentPermissions(destinationFolderRef, docRef, true, false);
                    break;
                default:
                    LOGGER.error("Unexpected permission inheritance '" + perms + "' supplied for create operation");
                    break;
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }

        addNode(destinationFolderRef, docRef, tags);
    }

    @Override
    public void copyNode(final DocRef sourceDocRef,
                         final DocRef destDocRef,
                         final DocRef destinationFolderRef,
                         final PermissionInheritance permissionInheritance) {
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
                    // Ignore original permissions, ignore permissions of the destination folder,
                    // just make the new item owned by the current user.
                    addDocumentPermissions(null, destDocRef, true, true);
                    break;
                case SOURCE:
                    // Copy permissions from the original, ignore permissions of the destination
                    // folder, and make the new item owned by the current user.
                    addDocumentPermissions(sourceDocRef, destDocRef, true, true);
                    break;
                case DESTINATION:
                    // Ignore permissions of the original, add permissions of the destination folder,
                    // and make the new item owned by the current user.
                    addDocumentPermissions(destinationFolderRef, destDocRef, true, true);
                    break;
                case COMBINED:
                    // Copy permissions from the original, add permissions of the destination folder,
                    // and make the new item owned by the current user.
                    addDocumentPermissions(sourceDocRef, destDocRef, true, true);
                    addDocumentPermissions(destinationFolderRef, destDocRef, true, true);
                    break;
                default:
                    LOGGER.error("Unexpected permission inheritance '" + perms + "' supplied for copy operation");
                    break;
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }

        addNode(destinationFolderRef, destDocRef);
    }

    @Override
    public void moveNode(final DocRef docRef,
                         final DocRef destinationFolderRef,
                         final PermissionInheritance permissionInheritance) {
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
                    // Remove all current permissions, ignore permissions of the destination folder, just make the
                    // new item owned by the current user.
                    removeAllDocumentPermissions(docRef);
                    addDocumentPermissions(null, docRef, true, true);
                    break;
                case SOURCE:
                    // We are keeping the permissions that we already have so do nothing.
                    break;
                case DESTINATION:
                    // Remove all current permissions, add permissions of the destination folder, and make the new
                    // item owned by the current user.
                    removeAllDocumentPermissions(docRef);
                    addDocumentPermissions(destinationFolderRef, docRef, true, true);
                    break;
                case COMBINED:
                    // Keep all current permissions, add permissions of the destination folder, and make the new
                    // item owned by the current user.
                    addDocumentPermissions(destinationFolderRef, docRef, true, true);
                    break;
                default:
                    LOGGER.error("Unexpected permission inheritance '" + perms + "' supplied for move operation");
                    break;
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }

        moveNode(destinationFolderRef, docRef);
    }

    @Override
    public void renameNode(final DocRef docRef) {
        updateNode(docRef);
    }

    @Override
    public void updateTags(final DocRef docRef, final Set<String> nodeTags) {
        final ExplorerTreeNode explorerTreeNode = getNodeForDocRef(docRef)
                .orElse(null);
        if (explorerTreeNode != null) {
            explorerTreeNode.setTags(nodeTags);
            explorerTreeDao.update(explorerTreeNode);
        }
    }

    @Override
    public void deleteNode(final DocRef docRef) {
        try {
            getNodeForDocRef(docRef).ifPresent(explorerTreeDao::remove);

        } catch (final RuntimeException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private Optional<ExplorerTreeNode> getNodeForDocRef(final DocRef docRef) {
        return Optional.ofNullable(docRef)
                .map(DocRef::getUuid)
                .map(explorerTreeDao::findByUUID);
    }

    @Override
    public ExplorerNode getRoot() {
        // Assumes ensureRootNodeExists has been called already
        return ExplorerConstants.SYSTEM_NODE;
    }

    @Override
    public Optional<ExplorerNode> getNodeWithRoot(final DocRef docRef) {
        return getNodeForDocRef(docRef)
                .map(node -> {
                    final String rootNodeUuid = explorerTreeDao.getRoot(node).getUuid();

                    // Set the root node UUID
                    return node.buildExplorerNode()
                            .rootNodeUuid(rootNodeUuid)
                            .build();
                });
    }

    private synchronized void createRoot() {
        final List<ExplorerTreeNode> roots = explorerTreeDao.getRoots();
        if (roots == null || roots.size() == 0) {
            // Insert System root node.
            final DocRef root = ExplorerConstants.SYSTEM_DOC_REF;
            addNode(null, root);
        }
    }

    @Override
    public Optional<ExplorerNode> getNode(final DocRef docRef) {
        // Only return entries the user has permission to see.
        if (docRef != null && securityContext.hasDocumentPermission(docRef, DocumentPermission.USE)) {
            return getNodeForDocRef(docRef)
                    .map(this::createExplorerNode);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public List<ExplorerNode> getPath(final DocRef docRef) {
        return getNodeForDocRef(docRef)
                .map(explorerTreeDao::getPath)
                .orElse(Collections.emptyList())
                .stream()
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
                    .map(d -> d.stream()
                            .map(this::createExplorerNode)
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
        addNode(null, ExplorerConstants.SYSTEM_DOC_REF);
    }

    private void addNode(final DocRef parentFolderRef, final DocRef docRef) {
        addNode(parentFolderRef, docRef, null);
    }

    private void addNode(final DocRef parentFolderRef, final DocRef docRef, final Set<String> tags) {
        final ExplorerTreeNode folderNode = getNodeForDocRef(parentFolderRef).orElse(null);
        final ExplorerTreeNode docNode = ExplorerTreeNode.create(docRef);
//        setTags(docNode);
        explorerTreeDao.addChild(folderNode, docNode);
    }

    private void moveNode(final DocRef parentFolderRef, final DocRef docRef) {
        final ExplorerTreeNode folderNode = getNodeForDocRef(parentFolderRef).orElse(null);
        final ExplorerTreeNode docNode = getNodeForDocRef(docRef).orElse(null);
        explorerTreeDao.move(docNode, folderNode);
    }

    private void updateNode(final DocRef docRef) {
        final ExplorerTreeNode docNode = getNodeForDocRef(docRef).orElse(null);
        if (docNode != null) {
            docNode.setType(docRef.getType());
            docNode.setUuid(docRef.getUuid());
            docNode.setName(docRef.getName());
            explorerTreeDao.update(docNode);
        }
    }

//    private void setTags(final ExplorerTreeNode explorerTreeNode) {
//        if (explorerTreeNode != null) {
//            explorerTreeNode.setTags(ExplorerFlags.getFlag(explorerTreeNode.getType()));
//        }
//    }

    private void addDocumentPermissions(final DocRef source,
                                        final DocRef dest,
                                        final boolean owner,
                                        final boolean cascade) {
        final String sourceType = NullSafe.get(source, DocRef::getType);
        final String sourceUuid = NullSafe.get(source, DocRef::getUuid);

        if (cascade
                && sourceType != null
                && sourceUuid != null
                && DocumentTypes.isFolder(sourceType)) {
            final List<ExplorerNode> descendants = getDescendants(dest);
            descendants.forEach(descendant -> {
                documentPermissionService.addDocumentPermissions(source, descendant.getDocRef());
                if (owner) {
                    documentPermissionService.setPermission(
                            descendant.getDocRef(),
                            securityContext.getUserRef(),
                            DocumentPermission.OWNER);
                }
            });
        }

        documentPermissionService.addDocumentPermissions(source, dest);
        if (owner) {
            documentPermissionService.setPermission(
                    dest,
                    securityContext.getUserRef(),
                    DocumentPermission.OWNER);
        }
    }

    private void removeAllDocumentPermissions(final DocRef docRef) {
        documentPermissionService.removeAllDocumentPermissions(docRef);
    }

    private ExplorerNode createExplorerNode(final ExplorerTreeNode explorerTreeNode) {
        if (Objects.equals(ExplorerConstants.SYSTEM_NODE.getType(), explorerTreeNode.getType())
                && Objects.equals(ExplorerConstants.SYSTEM_NODE.getUuid(), explorerTreeNode.getUuid())) {
            return ExplorerConstants.SYSTEM_NODE;
        } else if (Objects.equals(ExplorerConstants.FAVOURITES_NODE.getType(), explorerTreeNode.getType())
                && Objects.equals(ExplorerConstants.FAVOURITES_NODE.getUuid(), explorerTreeNode.getUuid())) {
            return ExplorerConstants.FAVOURITES_NODE;
        } else {
            return explorerTreeNode.buildExplorerNode()
                    .addNodeFlag(ExplorerFlags.getStandardFlagByDocType(explorerTreeNode.getType()).orElse(null))
                    .build();
        }
    }
}
