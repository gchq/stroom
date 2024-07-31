package stroom.security.impl;

import stroom.security.shared.AppPermission;

import java.util.Set;

public interface AppPermissionDao {
    Set<AppPermission> getPermissionsForUser(String userUuid);

    void addPermission(String userUuid, AppPermission permission);

    void removePermission(String userUuid, AppPermission permission);
}
