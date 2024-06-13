package stroom.security.impl;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface DocumentPermissionDao {

    Set<String> getPermissionsForDocumentForUser(String docUuid, String userUuid);

    BasicDocPermissions getPermissionsForDocument(String docUuid);

    Map<String, BasicDocPermissions> getPermissionsForDocuments(Collection<String> docUuids);

    Set<String> getDocumentOwnerUuids(String documentUuid);

    UserDocumentPermissions getPermissionsForUser(String userUuid);

    void addPermission(String docRefUuid, String userUuid, String permission);

    void removePermission(String docRefUuid, String userUuid, String permission);

    void removePermissions(String docRefUuid, String userUuid, Set<String> permissions);

    void clearDocumentPermissionsForUser(String docRefUuid, String userUuid);

    void clearDocumentPermissionsForDoc(String docRefUuid);

    void clearDocumentPermissionsForDocs(Set<String> docRefUuids);

    void setOwner(String docRefUuid, String ownerUuid);
}
