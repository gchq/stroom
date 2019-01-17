package stroom.security.impl;

import stroom.security.dao.UserDao;
import stroom.security.rest.CreateDTO;
import stroom.security.rest.UserResource;
import stroom.security.shared.UserJooq;

import javax.inject.Inject;
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
    public List<UserJooq> get(final String name,
                              final Boolean isGroup,
                              final String uuid) {
        final List<UserJooq> users = new ArrayList<>();

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

        return users.stream().distinct().collect(Collectors.toList());
    }

    @Override
    public List<UserJooq> findUsersInGroup(final String groupUuid) {
        return userDao.findUsersInGroup(groupUuid);
    }

    @Override
    public List<UserJooq> findGroupsForUser(final String userUuid) {
        return userDao.findGroupsForUser(userUuid);
    }

    @Override
    public UserJooq create(final CreateDTO createDTO) {
        if (null != createDTO.getGroup() && createDTO.getGroup()) {
            return userDao.createUser(createDTO.getName());
        } else {
            return userDao.createUserGroup(createDTO.getName());
        }
    }

    @Override
    public Boolean deleteUser(final String uuid) {
        return userDao.deleteUser(uuid);
    }

    @Override
    public Boolean addUserToGroup(final String userUuid,
                                  final String groupUuid) {
        return userDao.addUserToGroup(userUuid, groupUuid);
    }

    @Override
    public Boolean removeUserFromGroup(final String userUuid,
                                       final String groupUuid) {
        return userDao.removeUserFromGroup(userUuid, groupUuid);
    }
}
