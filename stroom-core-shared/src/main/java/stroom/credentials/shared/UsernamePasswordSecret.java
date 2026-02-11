package stroom.credentials.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({
        "username",
        "password"
})
@JsonInclude(Include.NON_NULL)
public final class UsernamePasswordSecret implements Secret {

    @JsonProperty
    private final String username;
    @JsonProperty
    private final String password;

    @JsonCreator
    public UsernamePasswordSecret(
            @JsonProperty("username") final String username,
            @JsonProperty("password") final String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final UsernamePasswordSecret that = (UsernamePasswordSecret) o;
        return Objects.equals(username, that.username)
               && Objects.equals(password, that.password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                username,
                password);
    }
}
