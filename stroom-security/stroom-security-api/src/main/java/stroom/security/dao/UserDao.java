package stroom.security.dao;

import stroom.security.shared.UserJooq;

import java.util.List;

public interface UserDao {
    List<UserJooq> find(Boolean isGroup, String name);

    UserJooq getById(long id);

    UserJooq getByUuid(String uuid);

    UserJooq getUserByName(String name);

    List<UserJooq> findUsersInGroup(String groupUuid);

    List<UserJooq> findGroupsForUser(String userUuid);

    UserJooq createUser(String name);

    UserJooq createUserGroup(String name);

    Boolean deleteUser(String uuid);

    Boolean addUserToGroup(String userUuid, String groupUuid);

    Boolean removeUserFromGroup(String userUuid, String groupUuid);
}
