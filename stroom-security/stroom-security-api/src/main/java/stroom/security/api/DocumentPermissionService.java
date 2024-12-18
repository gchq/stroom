package stroom.security.api;

import stroom.docref.DocRef;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentUserPermissions;
import stroom.security.shared.FetchDocumentUserPermissionsRequest;
import stroom.security.shared.SingleDocumentPermissionChangeRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import java.util.Set;

public interface DocumentPermissionService {

    DocumentPermission getPermission(DocRef docRef, UserRef userRef);

    void setPermission(DocRef docRef, UserRef userRef, DocumentPermission permission);

    void removeAllDocumentPermissions(DocRef docRef);

    void removeAllDocumentPermissions(Set<DocRef> docRefs);

    /**
     * Add all permissions from one doc to another.
     *
     * @param sourceDocRef The source doc to copy permissions from.
     * @param destDocRef   The dest doc to copy permissions to.
     */
    void addDocumentPermissions(DocRef sourceDocRef,
                                DocRef destDocRef);

    Boolean changeDocumentPermissions(SingleDocumentPermissionChangeRequest request);

    ResultPage<DocumentUserPermissions> fetchDocumentUserPermissions(FetchDocumentUserPermissionsRequest request);
}
