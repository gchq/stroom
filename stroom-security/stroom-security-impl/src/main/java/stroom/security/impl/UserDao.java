package stroom.security.impl;

import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.User;
import stroom.util.filter.FilterFieldMapper;
import stroom.util.filter.FilterFieldMappers;

import java.util.List;
import java.util.Set;

public interface UserDao {
    FilterFieldMappers<User> FILTER_FIELD_MAPPERS = FilterFieldMappers.of(
            FilterFieldMapper.of(FindUserCriteria.FIELD_DEF_NAME, User::getName));

    User create(User user);

//    User createUser(String name);
//
//    User createUserGroup(String name);

    User getById(int id);

    User getByUuid(String uuid);

    User getByName(String name);

    User update(User user);

    void delete(String uuid);

    List<User> find(String quickFilter, Boolean userGroup);

    List<User> findUsersInGroup(String groupUuid, String quickFilterInput);

    List<User> findGroupsForUser(String userUuid, String quickFilterInput);

    Set<String> findGroupUuidsForUser(String userUuid);

    List<User> findGroupsForUserName(String userName);

    void addUserToGroup(String userUuid, String groupUuid);

    void removeUserFromGroup(String userUuid, String groupUuid);
}
