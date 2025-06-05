package stroom.annotation.shared;

import stroom.util.shared.UserRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public final class UserRefEntryValue implements EntryValue {

    @JsonProperty
    private final UserRef userRef;

    @JsonCreator
    public UserRefEntryValue(@JsonProperty("userRef") final UserRef userRef) {
        this.userRef = userRef;
    }

    public static UserRefEntryValue of(final UserRef userRef) {
        return new UserRefEntryValue(userRef);
    }

    public UserRef getUserRef() {
        return userRef;
    }

    @Override
    public String asUiValue() {
        return userRef != null
                ? userRef.toDisplayString()
                : null;
    }

    @Override
    public String asPersistedValue() {
        return userRef != null
                ? userRef.getUuid()
                : null;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final UserRefEntryValue that = (UserRefEntryValue) o;
        return Objects.equals(userRef, that.userRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userRef);
    }

    @Override
    public String toString() {
        return "UserRefEntryValue{" +
                "userRef=" + userRef +
                '}';
    }
}
