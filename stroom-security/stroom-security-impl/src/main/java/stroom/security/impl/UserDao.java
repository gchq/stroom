package stroom.security.impl;

import stroom.security.shared.FindUserContext;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.User;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserInfo;
import stroom.util.shared.UserRef;

import java.util.Optional;
import java.util.function.Consumer;

public interface UserDao {

    User create(User user);

    default User tryCreate(final User user) {
        return tryCreate(user, u -> {
        });
    }

    User tryCreate(User user, final Consumer<User> onUserCreateAction);

    Optional<User> getByUuid(String uuid);

    Optional<User> getByUuid(String uuid, String currentUserUuid, FindUserContext context);

    Optional<User> getUserBySubjectId(String subjectId);

    Optional<User> getGroupByName(String groupName);

    User update(User user);

    /**
     * Find users and groups.
     *
     * @param criteria Criteria to apply.
     * @return A page of users.
     */
    ResultPage<User> find(FindUserCriteria criteria);

    /**
     * Find users and groups that are related to the current user.
     *
     * @param currentUserUuid The current user uuid.
     * @param criteria        Additional criteria to apply.
     * @return A page of users.
     */
    ResultPage<User> findRelatedUsers(String currentUserUuid, FindUserCriteria criteria);

    ResultPage<User> findUsersInGroup(String groupUuid, FindUserCriteria criteria);

    ResultPage<User> findGroupsForUser(String userUuid, FindUserCriteria criteria);

    void addUserToGroup(String userUuid, String groupUuid);

    void removeUserFromGroup(String userUuid, String groupUuid);

    /**
     * Delete a user and remove it from any groups it is a member of.
     */
    boolean deleteUser(UserRef userRef);

    /**
     * Get the {@link UserRef} of a user that may or may not have been deleted.
     * To be used for cases where references to userUuids are held after the corresponding
     * user has been deleted, e.g. annotation entries.
     */
    Optional<UserInfo> getUserInfoByUserUuid(final String userUuid);
}
