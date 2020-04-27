package stroom.security.mock;

import stroom.security.api.ProcessingUserIdentityProvider;
import stroom.security.api.UserIdentity;

import java.util.Objects;

public class MockProcessingUserIdentityProvider implements ProcessingUserIdentityProvider {
    private static final UserIdentity USER_IDENTITY = new MockUserIdentity();

    @Override
    public UserIdentity get() {
        return USER_IDENTITY;
    }

    private static class MockUserIdentity implements UserIdentity {
        @Override
        public String getId() {
            return "INTERNAL_PROCESSING_USER";
        }

        @Override
        public String getJws() {
            return null;
        }

        @Override
        public String getSessionId() {
            return null;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (!(o instanceof UserIdentity)) return false;
            final UserIdentity that = (UserIdentity) o;
            return Objects.equals(getId(), that.getId()) &&
                    Objects.equals(getJws(), that.getJws());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getId(), getJws());
        }

        @Override
        public String toString() {
            return getId();
        }
    }
}
