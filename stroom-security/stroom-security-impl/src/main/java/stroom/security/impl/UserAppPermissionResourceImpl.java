package stroom.security.impl;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.util.Set;

public class UserAppPermissionResourceImpl implements UserAppPermissionResource {
    private final UserAppPermissionService userAppPermissionService;

    @Inject
    public UserAppPermissionResourceImpl(final UserAppPermissionService userAppPermissionService) {
        this.userAppPermissionService = userAppPermissionService;
    }

    @Override
    public Response getPermissionNamesForUser(final String userUuid) {
        final Set<String> permissions = userAppPermissionService.getPermissionNamesForUser(userUuid);
        return Response.ok(permissions).build();
    }

    @Override
    public Response getPermissionNamesForUserName(final String userName) {
        final Set<String> permissions = userAppPermissionService.getPermissionNamesForUserName(userName);
        return Response.ok(permissions).build();
    }


    @Override
    public Response getAllPermissionNames() {
        final Set<String> allPermissions = userAppPermissionService.getAllPermissionNames();
        return Response.ok(allPermissions).build();
    }

    @Override
    public Response addPermission(final String userUuid,
                                  final String permission) {
        userAppPermissionService.addPermission(userUuid, permission);
        return Response.noContent().build();
    }

    @Override
    public Response removePermission(final String userUuid,
                                     final String permission) {
        userAppPermissionService.removePermission(userUuid, permission);
        return Response.noContent().build();
    }
}
