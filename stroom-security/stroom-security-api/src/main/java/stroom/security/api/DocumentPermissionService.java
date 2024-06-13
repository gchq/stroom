package stroom.security.api;

import stroom.docref.DocRef;

import java.util.Set;

public interface DocumentPermissionService {

    /**
     * Clear the permissions for an existing document, i.e. change the permissions.
     * Same as {@link DocumentPermissionService#deleteDocumentPermissions(String)} except it
     * requires Owner permission.
     */
    void clearDocumentPermissions(String docUuid);

    /**
     * Delete the permissions for a document that has already been deleted.
     * Same as {@link DocumentPermissionService#clearDocumentPermissions(String)} except it
     * requires Delete permission.
     */
    void deleteDocumentPermissions(String docUuid);

    void deleteDocumentPermissions(Set<String> docUuids);

    void addDocumentPermissions(DocRef sourceDocRef,
                                DocRef documentDocRef,
                                boolean owner);

    void setDocumentOwner(String documentUuid, String userUuid);

    Set<String> getDocumentOwnerUuids(String documentUuid);
}
