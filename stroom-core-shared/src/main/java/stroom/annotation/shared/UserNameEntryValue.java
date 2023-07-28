package stroom.annotation.shared;

import stroom.util.shared.UserName;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class UserNameEntryValue implements EntryValue {

    @JsonProperty
    private final UserName userName;

    @JsonCreator
    public UserNameEntryValue(@JsonProperty("userName") final UserName userName) {
        this.userName = userName;
    }

    public static UserNameEntryValue of(final UserName userName) {
        return new UserNameEntryValue(userName);
    }

    public UserName getUserName() {
        return userName;
    }

    @Override
    public String asUiValue() {
        return userName != null
                ? userName.getUserIdentityForAudit()
                : null;
    }

    @Override
    public String asPersistedValue() {
        return userName != null
                ? userName.getUuid()
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
        final UserNameEntryValue that = (UserNameEntryValue) o;
        return Objects.equals(userName, that.userName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userName);
    }

    @Override
    public String toString() {
        return "UserNameEntryValue{" +
                "userName=" + userName +
                '}';
    }
}
