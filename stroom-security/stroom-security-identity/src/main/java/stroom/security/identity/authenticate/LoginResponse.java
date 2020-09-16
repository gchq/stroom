package stroom.security.identity.authenticate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class LoginResponse {
    @JsonProperty
    private final boolean loginSuccessful;
    @JsonProperty
    private final String message;
    @JsonProperty
    private final boolean requirePasswordChange;

    @JsonCreator
    public LoginResponse(@JsonProperty("loginSuccessful") final boolean loginSuccessful,
                         @JsonProperty("message") final String message,
                         @JsonProperty("requirePasswordChange") final boolean requirePasswordChange) {
        this.loginSuccessful = loginSuccessful;
        this.message = message;
        this.requirePasswordChange = requirePasswordChange;
    }

    public boolean isLoginSuccessful() {
        return loginSuccessful;
    }

    public String getMessage() {
        return message;
    }

    public boolean isRequirePasswordChange() {
        return requirePasswordChange;
    }
}
