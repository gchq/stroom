package stroom.security.impl;

import stroom.docref.DocRef;
import stroom.util.shared.EntityServiceException;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.ExplorerNode;
import stroom.security.SecurityContext;
import stroom.security.shared.CopyPermissionsFromParentAction;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.DocumentPermissions;
import stroom.task.api.AbstractTaskHandler;

import javax.inject.Inject;
import java.util.Optional;


public class CopyPermissionsFromParentHandler
        extends AbstractTaskHandler<CopyPermissionsFromParentAction, DocumentPermissions> {

    private final DocumentPermissionService documentPermissionService;
    private final SecurityContext securityContext;
    private final ExplorerNodeService explorerNodeService;

    @Inject
    CopyPermissionsFromParentHandler(
            final DocumentPermissionService documentPermissionService,
            final SecurityContext securityContext,
            final ExplorerNodeService explorerNodeService) {
        this.documentPermissionService = documentPermissionService;
        this.securityContext = securityContext;
        this.explorerNodeService = explorerNodeService;
    }

    @Override
    public DocumentPermissions exec(CopyPermissionsFromParentAction action) {
        final DocRef docRef = action.getDocRef();

        boolean isUserAllowedToChangePermissions = securityContext.
                hasDocumentPermission(docRef.getType(), docRef.getUuid(), DocumentPermissionNames.OWNER);
        if(!isUserAllowedToChangePermissions) {
            throw new EntityServiceException("You do not have sufficient privileges to change permissions for this document!");
        }

        Optional<ExplorerNode> parent = explorerNodeService.getParent(docRef);
        if(!parent.isPresent()){
            throw new EntityServiceException("This node does not have a parent to copy permissions from!");
        }

        DocumentPermissions parentsPermissions = documentPermissionService.getPermissionsForDocument(parent.get().getDocRef());
        return parentsPermissions;
    }
}