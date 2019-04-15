package stroom.security.impl;

import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.User;
import stroom.security.shared.User;
import stroom.util.shared.StringCriteria;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.util.List;

public class UserResourceImpl implements UserResource {

    private final UserService userService;

    @Inject
    public UserResourceImpl(final UserService userService) {
        this.userService = userService;
    }

    @Override
    public Response get(final String name,
                        final Boolean isGroup,
                        final String uuid) {

        final FindUserCriteria criteria = new FindUserCriteria();
        criteria.setName(new StringCriteria(name));
        criteria.setGroup(isGroup);
        final List<User> users = userService.find(criteria);
        
        return Response.ok(users).build();
    }

    @Override
    public Response get(String userUuid) {
        final User user = userService.loadByUuid(userUuid);
        
        return Response.ok(user).build();
    }

    @Override
    public Response findUsersInGroup(final String groupUuid) {
        final List<User> users = userService.findUsersInGroup(groupUuid);

        return Response.ok(users).build();
    }

    @Override
    public Response findGroupsForUserName(String userName) {
        final List<User> users = userService.findGroupsForUserName(userName);

        return Response.ok(users).build();
    }

    @Override
    public Response findGroupsForUser(final String userUuid) {
        final List<User> groups = userService.findGroupsForUser(userUuid);
        return Response.ok(groups).build();
    }

    @Override
    public Response create(final String name,
                           final Boolean isGroup) {
        User user;

        if (isGroup) {
            user = userService.createUserGroup(name);
        } else {
            user = userService.createUser(name);
        }

        return Response.ok(user).build();
    }

    @Override
    public Response deleteUser(final String uuid) {
        userService.delete(uuid);

        return Response.noContent().build();
    }

    @Override
    public Response addUserToGroup(final String userUuid,
                                  final String groupUuid) {
        userService.addUserToGroup(userUuid, groupUuid);

        return Response.noContent().build();
    }

    @Override
    public Response removeUserFromGroup(final String userUuid,
                                       final String groupUuid) {
        userService.removeUserFromGroup(userUuid, groupUuid);

        return Response.noContent().build();
    }
}
