package stroom.security.impl;

import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.User;
import stroom.security.shared.UserResource;
import stroom.util.shared.ResultPage;
import stroom.util.shared.StringCriteria;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

public class UserResourceImpl implements UserResource {
    private final UserService userService;

    @Inject
    public UserResourceImpl(final UserService userService) {
        this.userService = userService;
    }

    @Override
    public ResultPage<User> find(final FindUserCriteria criteria) {
        criteria.setSort(FindUserCriteria.FIELD_NAME);
        if (criteria.getRelatedUser() != null) {
            User userRef = criteria.getRelatedUser();
            List<User> list;
            if (userRef.isGroup()) {
                list = userService.findUsersInGroup(userRef.getUuid());
            } else {
                list = userService.findGroupsForUser(userRef.getUuid());
            }

            if (criteria.getName() != null) {
                list = list.stream().filter(user -> criteria.getName().isMatch(user.getName())).collect(Collectors.toList());
            }

            // Create a result list limited by the page request.
            return ResultPage.createPageLimitedList(list, criteria.getPageRequest());
        }

        return ResultPage.createUnboundedList(userService.find(criteria));
    }

    @Override
    public List<User> get(final String name,
                          final Boolean isGroup,
                          final String uuid) {

        final FindUserCriteria criteria = new FindUserCriteria();
        criteria.setName(new StringCriteria(name));
        criteria.setGroup(isGroup);
        return userService.find(criteria);
    }

    @Override
    public User get(String userUuid) {
        return userService.loadByUuid(userUuid);
    }

//    @Override
//    public List<User> findUsersInGroup(final String groupUuid) {
//        return userService.findUsersInGroup(groupUuid);
//    }
//
//    @Override
//    public List<User> findGroupsForUserName(String userName) {
//        return userService.findGroupsForUserName(userName);
//    }
//
//    @Override
//    public List<User> findGroupsForUser(final String userUuid) {
//        return userService.findGroupsForUser(userUuid);
//    }

    @Override
    public User create(final String name,
                       final Boolean isGroup) {
        User user;

        if (isGroup) {
            user = userService.createUserGroup(name);
        } else {
            user = userService.createUser(name);
        }

        return user;
    }

    @Override
    public Boolean deleteUser(final String uuid) {
        return userService.delete(uuid);
    }

    @Override
    public Boolean setStatus(String userName, boolean status) {
        final User existingUser = userService.getUserByName(userName);
        if (existingUser != null) {
            final User user = userService.loadByUuid(existingUser.getUuid());
            user.setEnabled(status);
            userService.update(user);
            return true;
        } else {
            throw new RuntimeException("User not found");
        }
    }

    @Override
    public Boolean addUserToGroup(final String userUuid,
                                  final String groupUuid) {
        return userService.addUserToGroup(userUuid, groupUuid);
    }

    @Override
    public Boolean removeUserFromGroup(final String userUuid,
                                       final String groupUuid) {
        return userService.removeUserFromGroup(userUuid, groupUuid);
    }

    @Override
    public List<String> getAssociates(final String filter) {
        return userService.getAssociates(filter);
    }
}