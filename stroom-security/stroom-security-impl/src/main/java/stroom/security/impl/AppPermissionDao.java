package stroom.security.impl;

import java.util.Set;

public interface AppPermissionDao {
    Set<String> getPermissionsForUser(String userUuid);

    void addPermission(String userUuid, String permission);

    void removePermission(String userUuid, String permission);
}
