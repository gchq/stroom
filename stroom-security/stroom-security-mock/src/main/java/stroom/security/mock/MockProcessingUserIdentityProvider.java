package stroom.security.mock;

import stroom.security.api.ProcessingUserIdentityProvider;
import stroom.security.api.UserIdentity;

import java.util.Objects;

public class MockProcessingUserIdentityProvider implements ProcessingUserIdentityProvider {

    private static final UserIdentity USER_IDENTITY = new MockProcessingUserIdentity();

    @Override
    public UserIdentity get() {
        return USER_IDENTITY;
    }

    @Override
    public boolean isProcessingUser(final UserIdentity userIdentity) {
        return UserIdentity.IDENTITY_COMPARATOR.compare(USER_IDENTITY, userIdentity) == 0;
    }

    private static class MockProcessingUserIdentity implements UserIdentity {

        @Override
        public String getId() {
            return "INTERNAL_PROCESSING_USER";
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final MockProcessingUserIdentity that = (MockProcessingUserIdentity) o;
            return Objects.equals(getId(), that.getId());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getId());
        }

        @Override
        public String toString() {
            return getId();
        }
    }
}
