package stroom.security.impl;

import stroom.docref.DocRef;
import stroom.security.rest.DocumentPermissionResource;
import stroom.security.service.DocumentPermissionService;
import stroom.security.shared.DocumentPermissions;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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

    /**
     * Convert document permissions into something javascript safe (non object key)
     */
    private class DocumentPermissionsDTO {
        private DocRef document;
        private Map<String, Set<String>> byUser =new HashMap<>();
        private Set<String> users = new HashSet<>();
        private Set<String> groups = new HashSet<>();

        public DocumentPermissionsDTO(final DocumentPermissions permissions) {
            this.document = permissions.getDocument();
            permissions.getUserPermissions()
                    .forEach(
                    (key, value) -> {
                        byUser.put(key.getUuid(), value);
                        if (key.isGroup()) {
                            groups.add(key.getUuid());
                        } else {
                            users.add(key.getUuid());
                        }
                    });
        }

        public DocRef getDocument() {
            return document;
        }

        public Map<String, Set<String>> getByUser() {
            return byUser;
        }

        public Set<String> getGroups() {
            return groups;
        }

        public Set<String> getUsers() {
            return users;
        }
    }

    @Override
    public Response getPermissionsForDocument(final String docType,
                                              final String docUuid) {
        final DocRef docRef = new DocRef.Builder()
                .type(docType)
                .uuid(docUuid)
                .build();
        final DocumentPermissions permissions = documentPermissionService.getPermissionsForDocument(docRef);
        final DocumentPermissionsDTO dto = new DocumentPermissionsDTO(permissions);
        return Response.ok(dto).build();
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
