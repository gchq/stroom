package stroom.authentication.authenticate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class LoginResponse {
    @JsonProperty
    private final boolean loginSuccessful;
    @JsonProperty
    private final String redirectUri;
    @JsonProperty
    private final String message;

    @JsonCreator
    public LoginResponse(@JsonProperty("loginSuccessful") final boolean loginSuccessful,
                         @JsonProperty("redirectUri") final String message,
                         @JsonProperty("message") final String redirectUri) {
        this.loginSuccessful = loginSuccessful;
        this.redirectUri = redirectUri;
        this.message = message;
    }

    public boolean isLoginSuccessful() {
        return loginSuccessful;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public String getMessage() {
        return message;
    }
}
