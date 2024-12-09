package stroom.explorer.impl;

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.docstore.api.UniqueNameUtil;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.DocumentTypeGroup;
import stroom.explorer.shared.ExplorerConstants;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.svg.shared.SvgImage;
import stroom.util.NullSafe;
import stroom.util.shared.PermissionException;

import jakarta.inject.Inject;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

class FolderExplorerActionHandler implements ExplorerActionHandler {

    private static final String FOLDER = ExplorerConstants.FOLDER_TYPE;
    public static final DocumentType DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.STRUCTURE,
            FolderExplorerActionHandler.FOLDER,
            FolderExplorerActionHandler.FOLDER,
            SvgImage.FOLDER);
    private static final String NAME_PATTERN_VALUE = "^[a-zA-Z0-9_\\- \\.\\(\\)]{1,}$";
    private final SecurityContext securityContext;
    private final ExplorerTreeDao explorerTreeDao;

    @Inject
    FolderExplorerActionHandler(final SecurityContext securityContext,
                                final ExplorerTreeDao explorerTreeDao) {
        this.securityContext = securityContext;
        this.explorerTreeDao = explorerTreeDao;
    }

//    @Override
//    public DocRef createDocument(final String name) {
//        NameValidationUtil.validate(NAME_PATTERN_VALUE, name);
//        return new DocRef(FOLDER, UUID.randomUUID().toString(), name);
//    }

    @Override
    public DocRef copyDocument(final DocRef docRef,
                               final String name,
                               final boolean makeNameUnique,
                               final Set<String> existingNames) {
        final ExplorerTreeNode explorerTreeNode = explorerTreeDao.findByUUID(docRef.getUuid());
        if (explorerTreeNode == null) {
            throw new RuntimeException("Unable to find tree node to copy");
        }

        if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW)) {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to copy (" + FOLDER + ")");
        }

        String folderName = name;
        if (NullSafe.isBlankString(folderName)) {
            folderName = explorerTreeNode.getName();
        }

        final String newName = UniqueNameUtil.getCopyName(folderName, makeNameUnique, existingNames);
        return new DocRef(FOLDER, UUID.randomUUID().toString(), newName);
    }

    @Override
    public DocRef moveDocument(final DocRef docRef) {
        final ExplorerTreeNode explorerTreeNode = explorerTreeDao.findByUUID(docRef.getUuid());
        if (explorerTreeNode == null) {
            throw new RuntimeException("Unable to find tree node to move");
        }

        if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT)) {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to move (" + FOLDER + ")");
        }
        return explorerTreeNode.getDocRef();
    }

    @Override
    public DocRef renameDocument(final DocRef docRef, final String name) {
        final ExplorerTreeNode explorerTreeNode = explorerTreeDao.findByUUID(docRef.getUuid());
        if (explorerTreeNode == null) {
            throw new RuntimeException("Unable to find tree node to rename");
        }
        if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT)) {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to rename (" + FOLDER + ")");
        }
        NameValidationUtil.validate(NAME_PATTERN_VALUE, name);
        explorerTreeNode.setName(name);
        return explorerTreeNode.getDocRef();
    }

    @Override
    public void deleteDocument(final DocRef docRef) {
        final ExplorerTreeNode explorerTreeNode = explorerTreeDao.findByUUID(docRef.getUuid());
        if (explorerTreeNode == null) {
            throw new RuntimeException("Unable to find tree node to delete");
        }
        if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.DELETE)) {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to delete (" + FOLDER + ")");
        }
    }

    @Override
    public DocRefInfo info(final DocRef docRef) {
        final ExplorerTreeNode explorerTreeNode = explorerTreeDao.findByUUID(docRef.getUuid());
        if (explorerTreeNode == null) {
            throw new DocumentNotFoundException(DocRef.builder()
                    .uuid(docRef.getUuid())
                    .build());
        }

        if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW)) {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to read (" + FOLDER + ")");
        }

        return DocRefInfo
                .builder()
                .docRef(DocRef.builder()
                        .type(explorerTreeNode.getType())
                        .uuid(explorerTreeNode.getUuid())
                        .name(explorerTreeNode.getName())
                        .build())
                .otherInfo("DB ID: " + explorerTreeNode.getId())
                .build();
    }

    @Override
    public DocumentType getDocumentType() {
        return DOCUMENT_TYPE;
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
    public List<DocRef> findByNames(final List<String> names, final boolean allowWildCards) {
        return explorerTreeDao.findByNames(names, allowWildCards)
                .stream()
                .map(ExplorerTreeNode::getDocRef)
                .collect(Collectors.toList());
    }

    @Override
    public Set<DocRef> listDocuments() {
        return explorerTreeDao.findByType(FOLDER)
                .stream()
                .map(ExplorerTreeNode::getDocRef)
                .collect(Collectors.toSet());
    }
}
