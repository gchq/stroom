package stroom.security.impl;

import stroom.security.shared.AppPermission;
import stroom.util.shared.UserRef;

import java.util.Set;

public interface AppPermissionService {

    Set<AppPermission> getPermissions(UserRef userRef);

    void addPermission(UserRef userRef, AppPermission permission);

    void removePermission(UserRef userRef, AppPermission permission);
}
