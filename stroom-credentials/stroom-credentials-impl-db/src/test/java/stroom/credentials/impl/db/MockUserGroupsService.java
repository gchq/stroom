package stroom.credentials.impl.db;

import stroom.security.api.UserGroupsService;
import stroom.util.shared.UserRef;

import java.util.Set;

/**
 * Mock for the tests.
 */
public class MockUserGroupsService implements UserGroupsService {

    /** Always returns an empty set */
    @Override
    public Set<UserRef> getGroups(final UserRef userRef) {
        return Set.of();
    }
}
