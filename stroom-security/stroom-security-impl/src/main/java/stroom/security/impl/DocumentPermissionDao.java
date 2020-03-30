package stroom.security.impl;

import java.util.Map;
import java.util.Set;

public interface DocumentPermissionDao {
    Set<String> getPermissionsForDocumentForUser(String docUuid, String userUuid);

    Map<String, Set<String>> getPermissionsForDocument(String docUuid);

    UserDocumentPermissions getPermissionsForUser(String userUuid);

    void addPermission(String docRefUuid, String userUuid, String permission);

    void removePermission(String docRefUuid, String userUuid, String permission);

    void clearDocumentPermissionsForUser(String docRefUuid, String userUuid);

    void clearDocumentPermissions(String docRefUuid);
}
