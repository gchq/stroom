package stroom.security.impl.db;

import java.util.List;

public interface UserDao {
    String ADMIN_USER_NAME = "admin";
    String STROOM_SERVICE_USER_NAME = "stroomServiceUser";

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
