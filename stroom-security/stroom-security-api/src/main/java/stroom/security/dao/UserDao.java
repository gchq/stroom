package stroom.security.dao;

import stroom.security.model.User;

import java.util.List;

public interface UserDao {
    List<User> find(Boolean isGroup, String name);

    User getById(long id);

    User getByUuid(String uuid);

    User getUserByName(String name);

    List<User> findUsersInGroup(String groupUuid);

    List<User> findGroupsForUser(String userUuid);

    User createUser(String name);

    User createUserGroup(String name);

    Boolean deleteUser(String uuid);

    Boolean addUserToGroup(String userUuid, String groupUuid);

    Boolean removeUserFromGroup(String userUuid, String groupUuid);
}
