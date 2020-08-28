package stroom.security.identity.authenticate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class AuthenticationState {
    @JsonProperty
    private final String userId;
    @JsonProperty
    private final boolean allowPasswordResets;

    @JsonCreator
    public AuthenticationState(@JsonProperty("userId") final String userId,
                               @JsonProperty("allowPasswordResets") final boolean allowPasswordResets) {
        this.userId = userId;
        this.allowPasswordResets = allowPasswordResets;
    }

    public String getUserId() {
        return userId;
    }

    public boolean isAllowPasswordResets() {
        return allowPasswordResets;
    }
}
