package stroom.security.impl;

import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentUserPermissions;
import stroom.security.shared.FetchDocumentUserPermissionsRequest;
import stroom.util.shared.ResultPage;

import java.util.BitSet;
import java.util.List;
import java.util.Set;

public interface DocumentPermissionDao {

    UserDocumentPermissions getPermissionsForUser(String userUuid);

    DocumentPermission getDocumentUserPermission(String documentUuid, String userUuid);

    void setDocumentUserPermission(String documentUuid, String userUuid, DocumentPermission permission);

    void removeDocumentUserPermission(String documentUuid, String userUuid);

    void removeAllDocumentPermissions(String documentUuid);

    void clearAllDocumentPermissions();

    BitSet getDocumentUserCreatePermissionsBitSet(String documentUuid, String userUuid);

    Set<String> getDocumentUserCreatePermissions(String documentUuid, String userUuid);

    void addDocumentUserCreatePermission(String documentUuid, String userUuid, String documentType);

    void removeDocumentUserCreatePermission(String documentUuid, String userUuid, String documentType);

    void removeDocumentUserCreatePermissions(String documentUuid, String userUuid);

    void removeAllDocumentCreatePermissions(String documentUuid);

    void clearAllDocumentCreatePermissions();

    /**
     * BULK operation to add all permssions from a source document to a target document.
     */
    void addDocumentPermissions(String sourceDocUuid, String destDocUuid);

    /**
     * BULK operation to add all create permssions from a source document to a target document.
     */
    void addDocumentCreatePermissions(String sourceDocUuid, String destDocUuid);

    /**
     * BULK operation to set all permssions from a source document to a target document.
     */
    void setDocumentPermissions(String sourceDocUuid, String destDocUuid);

    /**
     * BULK operation to set all create permssions from a source document to a target document.
     */
    void setDocumentCreatePermissions(String sourceDocUuid, String destDocUuid);

    ResultPage<DocumentUserPermissions> fetchDocumentUserPermissions(FetchDocumentUserPermissionsRequest request);
}
