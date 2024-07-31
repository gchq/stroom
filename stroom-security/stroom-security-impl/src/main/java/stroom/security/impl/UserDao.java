package stroom.security.impl;

import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.User;
import stroom.util.shared.ResultPage;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public interface UserDao {

    User create(User user);

    default User tryCreate(User user) {
        return tryCreate(user, null);
    }

    User tryCreate(User user, final Consumer<User> onUserCreateAction);

    Optional<User> getByUuid(String uuid);

    Optional<User> getUserBySubjectId(String subjectId);

    Optional<User> getGroupByName(String groupName);

    User update(User user);

    /**
     * Delete a user by their UUID
     */
    void delete(String uuid);

    ResultPage<User> find(FindUserCriteria criteria);

    ResultPage<User> findUsersInGroup(String groupUuid, FindUserCriteria criteria);

    ResultPage<User> findGroupsForUser(String userUuid, FindUserCriteria criteria);

    void addUserToGroup(String userUuid, String groupUuid);

    void removeUserFromGroup(String userUuid, String groupUuid);
}
