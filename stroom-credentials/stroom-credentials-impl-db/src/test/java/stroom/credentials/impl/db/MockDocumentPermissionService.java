package stroom.credentials.impl.db;

import stroom.docref.DocRef;
import stroom.security.api.DocumentPermissionService;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentUserPermissions;
import stroom.security.shared.FetchDocumentUserPermissionsRequest;
import stroom.security.shared.SingleDocumentPermissionChangeRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import java.util.Set;

public class MockDocumentPermissionService implements DocumentPermissionService {

    @Override
    public DocumentPermission getPermission(final DocRef docRef, final UserRef userRef) {
        return null;
    }

    @Override
    public void setPermission(final DocRef docRef,
                              final UserRef userRef,
                              final DocumentPermission permission) {

    }

    @Override
    public void removeAllDocumentPermissions(final DocRef docRef) {

    }

    @Override
    public void removeAllDocumentPermissions(final Set<DocRef> docRefs) {

    }

    @Override
    public void addDocumentPermissions(final DocRef sourceDocRef, final DocRef destDocRef) {

    }

    @Override
    public Boolean changeDocumentPermissions(final SingleDocumentPermissionChangeRequest request) {
        return null;
    }

    @Override
    public ResultPage<DocumentUserPermissions>
        fetchDocumentUserPermissions(final FetchDocumentUserPermissionsRequest request) {
        return null;
    }
}
