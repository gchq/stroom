package stroom.explorer.server;

import org.springframework.stereotype.Component;
import stroom.entity.server.NameValidationUtil;
import stroom.entity.shared.PermissionException;
import stroom.explorer.shared.ExplorerConstants;
import stroom.query.api.v1.DocRef;
import stroom.security.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;

import java.util.UUID;

@Component
class SystemExplorerActionHandler implements ExplorerActionHandler {
    public static final String SYSTEM = ExplorerConstants.SYSTEM;
    public static final String FOLDER = ExplorerConstants.FOLDER;
    private final SecurityContext securityContext;
    private final ExplorerTreeDao explorerTreeDao;

    SystemExplorerActionHandler(final SecurityContext securityContext, final ExplorerTreeDao explorerTreeDao) {
        this.securityContext = securityContext;
        this.explorerTreeDao = explorerTreeDao;
    }

    @Override
    public DocRef createDocument(final String name, final String parentFolderUUID) {
        throw new PermissionException("You cannot create the System node");
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
        throw new PermissionException("You cannot move the System node");
    }

    @Override
    public DocRef renameDocument(final String uuid, final String name) {
        throw new PermissionException("You cannot rename the System node");
    }

    @Override
    public void deleteDocument(final String uuid) {
        throw new PermissionException("You cannot delete the System node");
    }
}
