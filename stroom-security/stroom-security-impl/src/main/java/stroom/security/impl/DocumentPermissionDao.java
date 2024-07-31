package stroom.security.impl;

import stroom.docref.DocRef;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.UserRef;

import java.util.BitSet;
import java.util.List;
import java.util.Set;

public interface DocumentPermissionDao {

    UserDocumentPermissions getPermissionsForUser(String userUuid);

    DocumentPermission getPermission(String documentUuid, String userUuid);

    void setPermission(String documentUuid, String userUuid, DocumentPermission permission);

    void clearPermission(String documentUuid, String userUuid);

    void removeAllDocumentPermissions(String documentUuid);

    List<Integer> getDocumentCreatePermissions(String documentUuid, String userUuid);

    void addDocumentCreatePermission(String documentUuid, String userUuid, String documentType);

    void removeDocumentCreatePermission(String documentUuid, String userUuid, String documentType);

    void clearDocumentCreatePermissions(String documentUuid, String userUuid);

    void removeAllDocumentCreatePermissions(String documentUuid);

//
//    void clearDocumentPermissionsForDoc(String documentUuid);
//
//    void clearDocumentPermissionsForDocs(Set<String> documentUuids);
//
//    int getDocumentTypeId(String documentType);

    void copyDocumentPermissions(String sourceDocUuid, String destDocUuid);

    void copyDocumentCreatePermissions(String sourceDocUuid, String destDocUuid);
}
