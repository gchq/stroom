package stroom.security.api;

import stroom.util.shared.UserRef;

import java.util.Set;

public interface UserGroupsService {

    Set<UserRef> getGroups(UserRef userRef);
}
