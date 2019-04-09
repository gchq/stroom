package stroom.security.impl;

import java.util.Set;

public interface AppPermissionDao {
    Set<String> getPermissionsForUser(String userUuid);

    Set<String> getPermissionsForUserName(String userName);

    void addPermission(String userUuid, String permission);

    void removePermission(String userUuid, String permission);
}
