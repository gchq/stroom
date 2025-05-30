package stroom.security.mock;

import stroom.security.api.ServiceUserFactory;
import stroom.security.api.UserIdentity;

import java.util.Objects;

public class MockServiceUserFactory implements ServiceUserFactory {

    private static final UserIdentity USER_IDENTITY = new MockProcessingUserIdentity();

    @Override
    public UserIdentity createServiceUserIdentity() {
        return USER_IDENTITY;
    }

    @Override
    public boolean isServiceUser(final UserIdentity userIdentity,
                                 final UserIdentity serviceUserIdentity) {
        return Objects.equals(
                userIdentity.subjectId(),
                MockProcessingUserIdentity.PROCESSING_USER_ID);
    }


    // --------------------------------------------------------------------------------


    private static class MockProcessingUserIdentity implements UserIdentity {

        protected static final String PROCESSING_USER_ID = "MOCK_PROCESSING_USER";

        @Override
        public String subjectId() {
            return PROCESSING_USER_ID;
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
            return Objects.equals(subjectId(), that.subjectId());
        }

        @Override
        public int hashCode() {
            return Objects.hash(subjectId());
        }

        @Override
        public String toString() {
            return subjectId();
        }
    }
}
