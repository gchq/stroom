package stroom.security.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class ValidateSessionResponse {
    @JsonProperty
    private final boolean valid;
    @JsonProperty
    private final String userId;
    @JsonProperty
    private final String redirectUri;

    @JsonCreator
    public ValidateSessionResponse(@JsonProperty("valid") final boolean valid,
                                   @JsonProperty("userId") final String userId,
                                   @JsonProperty("redirectUri") final String redirectUri) {
        this.valid = valid;
        this.userId = userId;
        this.redirectUri = redirectUri;
    }

    public boolean isValid() {
        return valid;
    }

    public String getUserId() {
        return userId;
    }

    public String getRedirectUri() {
        return redirectUri;
    }
}
