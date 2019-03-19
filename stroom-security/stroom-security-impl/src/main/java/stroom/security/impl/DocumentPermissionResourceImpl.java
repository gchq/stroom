package stroom.security.impl;

import stroom.docref.DocRef;
import stroom.security.rest.DocumentPermissionResource;
import stroom.security.service.DocumentPermissionService;
import stroom.security.shared.DocumentPermissions;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class DocumentPermissionResourceImpl implements DocumentPermissionResource {

    private final DocumentPermissionService documentPermissionService;
    private final DocumentTypePermissions documentTypePermissions;

    @Inject
    public DocumentPermissionResourceImpl(final DocumentPermissionService documentPermissionService,
                                          final DocumentTypePermissions documentTypePermissions) {
        this.documentPermissionService = documentPermissionService;
        this.documentTypePermissions = documentTypePermissions;
    }

    @Override
    public Response getPermissionForDocType(final String docType) {
        final String[] permissions = documentTypePermissions.getPermissions(docType);
        return Response.ok(permissions).build();
    }

    @Override
    public Response getPermissionsForDocumentForUser(final String docUuid,
                                                     final String userUuid) {
        final Set<String> permissions =
                documentPermissionService.getPermissionsForDocumentForUser(docUuid, userUuid);
        return Response.ok(permissions).build();
    }

    @Override
    public Response addPermission(final String docUuid,
                                  final String userUuid,
                                  final String permissionName) {
        documentPermissionService.addPermission(docUuid, userUuid, permissionName);
        return Response.noContent().build();
    }

    @Override
    public Response removePermission(final String docUuid,
                                     final String userUuid,
                                     final String permissionName) {
        documentPermissionService.removePermission(docUuid, userUuid, permissionName);
        return Response.noContent().build();
    }

    @Override
    public Response removePermissionForDocumentForUser(final String docUuid,
                                                       final String userUuid) {
        documentPermissionService.clearDocumentPermissionsForUser(docUuid, userUuid);
        return Response.noContent().build();
    }

    @Override
    public Response getPermissionsForDocument(final String docUuid) {
        final DocumentPermissions permissions = documentPermissionService.getPermissionsForDocument(docUuid);
        return Response.ok(permissions).build();
    }

    @Override
    public Response clearDocumentPermissions(final String docUuid) {
        documentPermissionService.clearDocumentPermissions(docUuid);
        return Response.noContent().build();
    }
}
