package stroom.security.impl;

import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.User;
import stroom.security.shared.UserResource;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;
import javax.ws.rs.NotFoundException;
import java.util.List;

public class UserResourceImpl implements UserResource {

    private final UserService userService;

    @Inject
    public UserResourceImpl(final UserService userService) {
        this.userService = userService;
    }

    @Override
    public ResultPage<User> find(final FindUserCriteria criteria) {
        // Apply default sort
        if (criteria.getSortList() == null || criteria.getSortList().isEmpty()) {
            criteria.setSort(FindUserCriteria.FIELD_NAME);
        }

        if (criteria.getRelatedUser() != null) {
            final User userRef = criteria.getRelatedUser();
            List<User> list;
            if (userRef.isGroup()) {
                list = userService.findUsersInGroup(userRef.getUuid(), criteria.getQuickFilterInput());
            } else {
                list = userService.findGroupsForUser(userRef.getUuid(), criteria.getQuickFilterInput());
            }

//            if (criteria.getQuickFilterInput() != null) {
//
//                list = list.stream()
////                        .filter(user ->
////                                criteria.getName().isMatch(user.getName()))
//                        .filter(predicate)
//                        .collect(Collectors.toList());
//            }

            // Create a result list limited by the page request.
            return ResultPage.createPageLimitedList(list, criteria.getPageRequest());
        }

        return ResultPage.createUnboundedList(userService.find(criteria));
    }

    @Override
    public List<User> get(final String name,
                          final Boolean isGroup,
                          final String uuid) {

        // TODO @AT Doesn't appear to be used by java code, may be used by new react screens
        //   that are currently unused.
        // TODO @AT Not clear what this method is trying to do. uuid is not used, is name a filter
        //   input or an exact match.

        final FindUserCriteria criteria = new FindUserCriteria();
        criteria.setQuickFilterInput(name);
        criteria.setGroup(isGroup);
        return userService.find(criteria);
    }

    @Override
    public User get(String userUuid) {
        // TODO @AT Doesn't appear to be used by java code, may be used by new react screens
        //   that are currently unused.

        return userService.loadByUuid(userUuid)
                .orElseThrow(() -> new NotFoundException("User " + userUuid + " does not exist"));
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
        userService.getUserByName(userName)
                .ifPresentOrElse(
                        user -> {
                            user.setEnabled(status);
                            userService.update(user);
                        },
                        () -> {
                            throw new RuntimeException("User not found");
                        });
        return true;
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