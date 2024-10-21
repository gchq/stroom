package stroom.security.impl;

import stroom.security.shared.AppPermission;
import stroom.security.shared.AppUserPermissions;
import stroom.security.shared.FetchAppUserPermissionsRequest;
import stroom.util.shared.ResultPage;

import java.util.Set;

public interface AppPermissionDao {

    Set<AppPermission> getPermissionsForUser(String userUuid);

    void addPermission(String userUuid, AppPermission permission);

    void removePermission(String userUuid, AppPermission permission);

    ResultPage<AppUserPermissions> fetchAppUserPermissions(FetchAppUserPermissionsRequest request);
}
