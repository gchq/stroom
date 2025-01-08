package stroom.security.api;

import stroom.docref.DocRef;

import java.util.Set;

public interface DocumentPermissionService {

    void addPermission(String docUuid,
                       String userUuid,
                       String permission);

    void clearDocumentPermissions(String documentUuid);

    void addDocumentPermissions(DocRef sourceDocRef,
                                DocRef documentDocRef,
                                boolean owner);

    void setDocumentOwner(String documentUuid, String userUuid);

    Set<String> getDocumentOwnerUuids(String documentUuid);
}
