package stroom.explorer.server;

import org.springframework.stereotype.Component;
import stroom.entity.server.NameValidationUtil;
import stroom.entity.shared.PermissionException;
import stroom.explorer.shared.ExplorerConstants;
import stroom.query.api.v2.DocRef;
import stroom.security.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;

import java.util.UUID;

@Component
class FolderExplorerActionHandler implements ExplorerActionHandler {
    public static final String FOLDER = ExplorerConstants.FOLDER;
    private static final String NAME_PATTERN_VALUE = "^[a-zA-Z0-9_\\- \\.\\(\\)]{1,}$";
    private final SecurityContext securityContext;
    private final ExplorerTreeDao explorerTreeDao;

    FolderExplorerActionHandler(final SecurityContext securityContext, final ExplorerTreeDao explorerTreeDao) {
        this.securityContext = securityContext;
        this.explorerTreeDao = explorerTreeDao;
    }

    @Override
    public DocRef createDocument(final String name, final String parentFolderUUID) {
        if (!securityContext.hasDocumentPermission(FOLDER, parentFolderUUID, DocumentPermissionNames.getDocumentCreatePermission(FOLDER))) {
            throw new PermissionException("You do not have permission to create (" + FOLDER + ") in folder " + parentFolderUUID);
        }
        NameValidationUtil.validate(NAME_PATTERN_VALUE, name);
        return new DocRef(FOLDER, UUID.randomUUID().toString(), name);
    }

    @Override
    public DocRef copyDocument(final String uuid, final String parentFolderUUID) {
        final ExplorerTreeNode explorerTreeNode = explorerTreeDao.findByUUID(uuid);
        if (explorerTreeNode == null) {
            throw new RuntimeException("Unable to find tree node to copy");
        }

        if (!securityContext.hasDocumentPermission(FOLDER, uuid, DocumentPermissionNames.READ)) {
            throw new PermissionException("You do not have permission to read (" + FOLDER + ")");
        }
        if (!securityContext.hasDocumentPermission(FOLDER, parentFolderUUID, DocumentPermissionNames.getDocumentCreatePermission(FOLDER))) {
            throw new PermissionException("You do not have permission to create (" + FOLDER + ") in folder " + parentFolderUUID);
        }
        return new DocRef(FOLDER, UUID.randomUUID().toString(), "Copy of " + explorerTreeNode.getName());
    }

    @Override
    public DocRef moveDocument(final String uuid, final String parentFolderUUID) {
        final ExplorerTreeNode explorerTreeNode = explorerTreeDao.findByUUID(uuid);
        if (explorerTreeNode == null) {
            throw new RuntimeException("Unable to find tree node to move");
        }

        if (!securityContext.hasDocumentPermission(FOLDER, uuid, DocumentPermissionNames.READ)) {
            throw new PermissionException("You do not have permission to read (" + FOLDER + ")");
        }
        if (!securityContext.hasDocumentPermission(FOLDER, parentFolderUUID, DocumentPermissionNames.getDocumentCreatePermission(FOLDER))) {
            throw new PermissionException("You do not have permission to create (" + FOLDER + ") in folder " + parentFolderUUID);
        }
        return explorerTreeNode.getDocRef();
    }

    @Override
    public DocRef renameDocument(final String uuid, final String name) {
        final ExplorerTreeNode explorerTreeNode = explorerTreeDao.findByUUID(uuid);
        if (explorerTreeNode == null) {
            throw new RuntimeException("Unable to find tree node to rename");
        }
        if (!securityContext.hasDocumentPermission(FOLDER, uuid, DocumentPermissionNames.UPDATE)) {
            throw new PermissionException("You do not have permission to update (" + FOLDER + ")");
        }
        NameValidationUtil.validate(NAME_PATTERN_VALUE, name);
        return explorerTreeNode.getDocRef();
    }

    @Override
    public void deleteDocument(final String uuid) {
        final ExplorerTreeNode explorerTreeNode = explorerTreeDao.findByUUID(uuid);
        if (explorerTreeNode == null) {
            throw new RuntimeException("Unable to find tree node to delete");
        }
        if (!securityContext.hasDocumentPermission(FOLDER, uuid, DocumentPermissionNames.DELETE)) {
            throw new PermissionException("You do not have permission to delete (" + FOLDER + ")");
        }
    }
}
