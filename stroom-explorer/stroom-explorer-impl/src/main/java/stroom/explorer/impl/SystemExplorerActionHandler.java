package stroom.explorer.impl;

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docstore.api.UniqueNameUtil;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.DocumentTypeGroup;
import stroom.explorer.shared.ExplorerConstants;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.shared.PermissionException;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.inject.Inject;

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
    public DocRef copyDocument(final DocRef docRef, final Set<String> existingNames) {
        final ExplorerTreeNode explorerTreeNode = explorerTreeDao.findByUUID(docRef.getUuid());
        if (explorerTreeNode == null) {
            throw new RuntimeException("Unable to find tree node to copy");
        }

        if (!securityContext.hasDocumentPermission(docRef.getUuid(), DocumentPermissionNames.READ)) {
            throw new PermissionException(securityContext.getUserId(),
                    "You do not have permission to read (" + FOLDER + ")");
        }

        final String newName = UniqueNameUtil.getCopyName(explorerTreeNode.getName(), existingNames);
        return new DocRef(FOLDER, UUID.randomUUID().toString(), newName);
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
        return new DocumentType(DocumentTypeGroup.SYSTEM, SystemExplorerActionHandler.SYSTEM,
                SystemExplorerActionHandler.SYSTEM);
    }

    ////////////////////////////////////////////////////////////////////////
    // START OF HasDependencies
    ////////////////////////////////////////////////////////////////////////

    @Override
    public Map<DocRef, Set<DocRef>> getDependencies() {
        return Collections.emptyMap();
    }

    @Override
    public Set<DocRef> getDependencies(final DocRef docRef) {
        return Collections.emptySet();
    }

    @Override
    public void remapDependencies(final DocRef docRef, final Map<DocRef, DocRef> remappings) {
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF HasDependencies
    ////////////////////////////////////////////////////////////////////////


    @Override
    public List<DocRef> findByNames(final List<String> name, final boolean allowWildCards) {
        throw new PermissionException(
                securityContext.getUserId(),
                "You cannot perform findByNames on the System node handler");
    }

    @Override
    public Set<DocRef> listDocuments() {
        throw new PermissionException(
                securityContext.getUserId(),
                "You cannot perform listDocuments on the System node handler");
    }
}
