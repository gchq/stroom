package stroom.annotations;

import org.springframework.stereotype.Component;
import stroom.entity.shared.PermissionException;
import stroom.explorer.server.ExplorerActionHandler;
import stroom.explorer.server.ExplorerTreeDao;
import stroom.query.api.v2.DocRef;
import stroom.security.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;

import java.util.UUID;

import static stroom.explorer.shared.ExplorerConstants.ANNOTATIONS;
import static stroom.explorer.shared.ExplorerConstants.FOLDER;

@Component
public class StroomAnnotationsExplorerActionHandler implements ExplorerActionHandler {
    private final SecurityContext securityContext;
    private final ExplorerTreeDao explorerTreeDao;

    StroomAnnotationsExplorerActionHandler(final SecurityContext securityContext, final ExplorerTreeDao explorerTreeDao) {
        this.securityContext = securityContext;
        this.explorerTreeDao = explorerTreeDao;
    }

    @Override
    public DocRef createDocument(String name, String parentFolderUUID) {
//        if (!securityContext.hasDocumentPermission(FOLDER, parentFolderUUID, DocumentPermissionNames.getDocumentCreatePermission(FOLDER))) {
//            throw new PermissionException("You do not have permission to create (" + FOLDER + ") in folder " + parentFolderUUID);
//        }
//        return new DocRef(ANNOTATIONS, UUID.randomUUID().toString(), "Annotations Service");
        throw new PermissionException(securityContext.getUserId(), "You cannot create the Annotations node");
    }

    @Override
    public DocRef copyDocument(String uuid, String parentFolderUUID) {
        throw new PermissionException(securityContext.getUserId(), "You cannot copy the Annotations node");
    }

    @Override
    public DocRef moveDocument(String uuid, String parentFolderUUID) {
        throw new PermissionException(securityContext.getUserId(), "You cannot move the Annotations node");
    }

    @Override
    public DocRef renameDocument(String uuid, String name) {
        throw new PermissionException(securityContext.getUserId(), "You cannot rename the Annotations node");
    }

    @Override
    public void deleteDocument(String uuid) {
        throw new PermissionException(securityContext.getUserId(), "You cannot delete the Annotations node");
    }
}
