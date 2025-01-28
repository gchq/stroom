package stroom.receive.common;

import stroom.security.api.UserIdentity;

import java.util.Objects;

/**
 * {@link UserIdentity} obtained when authenticated by {@link HashedDataFeedKey}
 */
public class DataFeedKeyUserIdentity implements UserIdentity {

    public static final String SUBJECT_ID_PREFIX = "data-feed-key-";

    private final String subjectId;
    private final String displayName;

    public DataFeedKeyUserIdentity(final HashedDataFeedKey hashedDataFeedKey) {
        Objects.requireNonNull(hashedDataFeedKey);
        this.subjectId = SUBJECT_ID_PREFIX + hashedDataFeedKey.getAccountId();
        this.displayName = subjectId;
    }

    @Override
    public String getSubjectId() {
        return subjectId;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return "DataFeedKeyUserIdentity{" +
               "subjectId='" + subjectId + '\'' +
               ", displayName='" + displayName + '\'' +
               '}';
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final DataFeedKeyUserIdentity that = (DataFeedKeyUserIdentity) object;
        return Objects.equals(subjectId, that.subjectId) && Objects.equals(displayName,
                that.displayName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subjectId, displayName);
    }
}
