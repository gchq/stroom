package stroom.security.impl;

import java.util.Set;

public interface UserAppPermissionService {
    Set<String> getPermissionNamesForUser(String userUuid);

    Set<String> getAllPermissionNames();

    Set<String> getPermissionNamesForUserName(String userName);

    void addPermission(String userUuid, String permission);

    void removePermission(String userUuid, String permission);
}
