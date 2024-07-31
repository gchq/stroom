package stroom.security.api;

import stroom.docref.DocRef;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.UserRef;

import java.util.Set;

public interface DocumentPermissionService {


//
//    /**
//     * Clear the permissions for an existing document, i.e. change the permissions.
//     * Same as {@link DocumentPermissionService#deleteDocumentPermissions(DocRef)} except it
//     * requires Owner permission.
//     */
//    void clearAllPermissions(DocRef docRef);
//
//    void clearAllPermissions(Set<DocRef> docRefs);
//


    DocumentPermission getPermission(DocRef docRef, UserRef userRef);

    void setPermission(DocRef docRef, UserRef userRef, DocumentPermission permission);

    void clearPermission(DocRef docRef, UserRef userRef);


//    Set<String> getDocumentCreatePermissions(DocRef docRef, UserRef userRef);

    void addDocumentCreatePermission(DocRef docRef, UserRef userRef, String documentType);

    void removeDocumentCreatePermission(DocRef docRef, UserRef userRef, String documentType);

    void clearDocumentCreatePermissions(DocRef docRef, UserRef userRef);

//    void clearDocumentCreatePermissionsForDocs(Set<DocRef> docRefs);


    void removeAllDocumentPermissions(DocRef docRef);

    void removeAllDocumentPermissions(Set<DocRef> docRefs);

    /**
     * Copy all permissions from one doc to another.
     *
     * @param sourceDocRef The source doc to copy permissions from.
     * @param destDocRef   The dest doc to copy permissions to.
     */
    void copyDocumentPermissions(DocRef sourceDocRef,
                                 DocRef destDocRef);
}
