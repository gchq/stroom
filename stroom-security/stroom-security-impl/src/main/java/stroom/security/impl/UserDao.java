package stroom.security.impl;

import stroom.security.shared.User;

import java.util.List;
import java.util.Set;

public interface UserDao {
    User create(User user);

//    User createUser(String name);
//
//    User createUserGroup(String name);

    User getById(int id);

    User getByUuid(String uuid);

    User getByName(String name);

    User update(User user);

    void delete(String uuid);

    List<User> find(String name, Boolean userGroup);

    List<User> findUsersInGroup(String groupUuid);

    List<User> findGroupsForUser(String userUuid);

    Set<String> findGroupUuidsForUser(String userUuid);

    List<User> findGroupsForUserName(String userName);

    void addUserToGroup(String userUuid, String groupUuid);

    void removeUserFromGroup(String userUuid, String groupUuid);
}
