package stroom.receive.common;

import stroom.security.api.UserIdentity;

import java.util.Objects;

/**
 * {@link UserIdentity} obtained when authenticated by {@link DataFeedKey}
 */
public class DataFeedKeyUserIdentity implements UserIdentity {

    private final String subjectId;
    private final String displayName;

    public DataFeedKeyUserIdentity(final DataFeedKey dataFeedKey) {
        Objects.requireNonNull(dataFeedKey);
        this.subjectId = dataFeedKey.getSubjectId();
        this.displayName = dataFeedKey.getDisplayName();
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
