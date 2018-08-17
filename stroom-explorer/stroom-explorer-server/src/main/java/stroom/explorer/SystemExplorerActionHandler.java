package stroom.explorer;

import stroom.entity.shared.PermissionException;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.ExplorerConstants;
import stroom.docref.DocRef;
import stroom.query.api.v2.DocRefInfo;
import stroom.security.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;

import javax.inject.Inject;
import java.util.Map;

class SystemExplorerActionHandler implements ExplorerActionHandler {
    private static final String SYSTEM = ExplorerConstants.SYSTEM;
    private static final String FOLDER = ExplorerConstants.FOLDER;
    private final SecurityContext securityContext;
    private final ExplorerTreeDao explorerTreeDao;

    @Inject
    SystemExplorerActionHandler(final SecurityContext securityContext,
                                final ExplorerTreeDao explorerTreeDao) {
        this.securityContext = securityContext;
        this.explorerTreeDao = explorerTreeDao;
    }

    @Override
    public DocRef createDocument(final String name) {
        throw new PermissionException(securityContext.getUserId(), "You cannot create the System node");
    }

    @Override
    public DocRef copyDocument(final String originalUuid,
                               final String copyUuid,
                               final Map<String, String> otherCopiesByOriginalUuid) {
        final ExplorerTreeNode explorerTreeNode = explorerTreeDao.findByUUID(originalUuid);
        if (explorerTreeNode == null) {
            throw new RuntimeException("Unable to find tree node to copy");
        }

        if (!securityContext.hasDocumentPermission(FOLDER, originalUuid, DocumentPermissionNames.READ)) {
            throw new PermissionException(securityContext.getUserId(), "You do not have permission to read (" + FOLDER + ")");
        }
        return new DocRef(FOLDER, copyUuid, explorerTreeNode.getName());
    }

    @Override
    public DocRef moveDocument(final String uuid) {
        throw new PermissionException(securityContext.getUserId(), "You cannot move the System node");
    }

    @Override
    public DocRef renameDocument(final String uuid, final String name) {
        throw new PermissionException(securityContext.getUserId(), "You cannot rename the System node");
    }

    @Override
    public void deleteDocument(final String uuid) {
        throw new PermissionException(securityContext.getUserId(), "You cannot delete the System node");
    }

    @Override
    public DocRefInfo info(final String uuid) {
        throw new PermissionException(securityContext.getUserId(), "You cannot get info about the System node");
    }

    @Override
    public DocumentType getDocumentType() {
        return new DocumentType(0, SystemExplorerActionHandler.SYSTEM, SystemExplorerActionHandler.SYSTEM);
    }
}
