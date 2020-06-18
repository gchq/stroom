package stroom.authentication.authenticate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class ConfirmPasswordResponse {
    @JsonProperty
    private final boolean valid;
    @JsonProperty
    private final String message;
    @JsonProperty
    private final String redirectUri;

    @JsonCreator
    public ConfirmPasswordResponse(@JsonProperty("valid") final boolean valid,
                                   @JsonProperty("message") final String message,
                                   @JsonProperty("redirectUri") final String redirectUri) {
        this.valid = valid;
        this.message = message;
        this.redirectUri = redirectUri;
    }

    public boolean isValid() {
        return valid;
    }

    public String getMessage() {
        return message;
    }

    public String getRedirectUri() {
        return redirectUri;
    }
}
