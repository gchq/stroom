package stroom.security.api;

import stroom.security.shared.AppPermission;
import stroom.security.shared.AppUserPermissions;
import stroom.security.shared.AppUserPermissionsReport;
import stroom.security.shared.FetchAppUserPermissionsRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import java.util.Set;

public interface AppPermissionService {

    ResultPage<AppUserPermissions> fetchAppUserPermissions(FetchAppUserPermissionsRequest request);

    Set<AppPermission> getDirectAppUserPermissions(UserRef userRef);

    AppUserPermissionsReport getAppUserPermissionsReport(UserRef userRef);

    void addPermission(UserRef userRef, AppPermission permission);

    void removePermission(UserRef userRef, AppPermission permission);
}
