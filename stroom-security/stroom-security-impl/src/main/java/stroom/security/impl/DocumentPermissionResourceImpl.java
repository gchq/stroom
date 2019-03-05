package stroom.security.impl;

import stroom.docref.DocRef;
import stroom.security.rest.DocumentPermissionResource;
import stroom.security.service.DocumentPermissionService;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
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
    public Response getPermissionsForDocumentForUser(final String docType,
                                                     final String docUuid,
                                                     final String userUuid) {
        final DocRef docRef = new DocRef.Builder()
                .type(docType)
                .uuid(docUuid)
                .build();
        final Set<String> permissions =
                documentPermissionService.getPermissionsForDocumentForUser(docRef, userUuid);
        return Response.ok(permissions).build();
    }

    @Override
    public Response addPermission(final String docType,
                                  final String docUuid,
                                  final String userUuid,
                                  final String permissionName) {
        final DocRef docRef = new DocRef.Builder()
                .type(docType)
                .uuid(docUuid)
                .build();
        documentPermissionService.addPermission(userUuid, docRef, permissionName);
        return Response.noContent().build();
    }

    @Override
    public Response removePermission(final String docType,
                                     final String docUuid,
                                     final String userUuid,
                                     final String permissionName) {
        final DocRef docRef = new DocRef.Builder()
                .type(docType)
                .uuid(docUuid)
                .build();
        documentPermissionService.removePermission(userUuid, docRef, permissionName);
        return Response.noContent().build();
    }

    @Override
    public Response clearDocumentPermissions(final String docType,
                                             final String docUuid) {
        final DocRef docRef = new DocRef.Builder()
                .type(docType)
                .uuid(docUuid)
                .build();
        documentPermissionService.clearDocumentPermissions(docRef);
        return Response.noContent().build();
    }
}
