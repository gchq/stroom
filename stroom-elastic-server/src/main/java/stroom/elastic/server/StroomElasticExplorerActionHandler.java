package stroom.elastic.server;

import stroom.entity.shared.PermissionException;
import stroom.explorer.server.ExplorerActionHandler;
import stroom.explorer.server.ExplorerTreeDao;
import stroom.query.api.v2.DocRef;
import stroom.security.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;

import java.util.UUID;

import static stroom.explorer.shared.ExplorerConstants.ELASTIC_SEARCH;
import static stroom.explorer.shared.ExplorerConstants.FOLDER;

public class StroomElasticExplorerActionHandler implements ExplorerActionHandler {
    private final SecurityContext securityContext;
    private final ExplorerTreeDao explorerTreeDao;

    StroomElasticExplorerActionHandler(final SecurityContext securityContext, final ExplorerTreeDao explorerTreeDao) {
        this.securityContext = securityContext;
        this.explorerTreeDao = explorerTreeDao;
    }

    @Override
    public DocRef createDocument(String name, String parentFolderUUID) {
        if (!securityContext.hasDocumentPermission(FOLDER, parentFolderUUID, DocumentPermissionNames.getDocumentCreatePermission(FOLDER))) {
            throw new PermissionException("You do not have permission to create (" + FOLDER + ") in folder " + parentFolderUUID);
        }
        return new DocRef(ELASTIC_SEARCH, UUID.randomUUID().toString(), "Elastic Search");
        //throw new PermissionException("You cannot create the Elastic Search node");
    }

    @Override
    public DocRef copyDocument(String uuid, String parentFolderUUID) {
        throw new PermissionException("You cannot copy the Elastic Search node");
    }

    @Override
    public DocRef moveDocument(String uuid, String parentFolderUUID) {
        throw new PermissionException("You cannot move the Elastic Search node");
    }

    @Override
    public DocRef renameDocument(String uuid, String name) {
        throw new PermissionException("You cannot rename the Elastic Search node");
    }

    @Override
    public void deleteDocument(String uuid) {
        throw new PermissionException("You cannot delete the Elastic Search node");
    }
}
