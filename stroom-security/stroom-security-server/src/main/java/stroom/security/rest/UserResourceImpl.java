package stroom.security.rest;

import stroom.security.dao.UserDao;
import stroom.security.rest.CreateDTO;
import stroom.security.rest.UserResource;
import stroom.security.shared.User;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class UserResourceImpl implements UserResource {

    private final UserDao userDao;

    @Inject
    public UserResourceImpl(final UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public Response get(final String name,
                        final Boolean isGroup,
                        final String uuid) {
        final List<User> users = new ArrayList<>();

        // If all the identification fields are null, just do a find which should return them all
        if (null == name && null == uuid) {
            users.addAll(userDao.find(isGroup, name));
        } else {
            if (null != name) {
                if (null != isGroup) {
                    users.addAll(userDao.find(isGroup, name));
                } else {
                    Optional.ofNullable(userDao.getUserByName(name))
                            .ifPresent(users::add);
                }
            }
            if (null != uuid) {
                Optional.ofNullable(userDao.getByUuid(uuid))
                        .ifPresent(users::add);
            }
        }

        final List<User> distinct = users.stream()
                .distinct()
                .collect(Collectors.toList());

        return Response.ok(distinct).build();
    }

    @Override
    public Response findUsersInGroup(final String groupUuid) {
        final List<User> users = userDao.findUsersInGroup(groupUuid);

        return Response.ok(users).build();
    }

    @Override
    public Response findGroupsForUser(final String userUuid) {
        final List<User> groups = userDao.findGroupsForUser(userUuid);
        return Response.ok(groups).build();
    }

    @Override
    public Response create(final CreateDTO createDTO) {
        User user;

        if (null != createDTO.getGroup() && createDTO.getGroup()) {
            user = userDao.createUser(createDTO.getName());
        } else {
            user = userDao.createUserGroup(createDTO.getName());
        }

        return Response.ok(user).build();
    }

    @Override
    public Response deleteUser(final String uuid) {
        userDao.deleteUser(uuid);

        return Response.noContent().build();
    }

    @Override
    public Response addUserToGroup(final String userUuid,
                                  final String groupUuid) {
        userDao.addUserToGroup(userUuid, groupUuid);

        return Response.noContent().build();
    }

    @Override
    public Response removeUserFromGroup(final String userUuid,
                                       final String groupUuid) {
        userDao.removeUserFromGroup(userUuid, groupUuid);

        return Response.noContent().build();
    }
}
