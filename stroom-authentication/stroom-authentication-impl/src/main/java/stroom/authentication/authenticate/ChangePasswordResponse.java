package stroom.authentication.authenticate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class ChangePasswordResponse {
    @JsonProperty
    private final boolean changeSucceeded;
    @JsonProperty
    private final String[] failedOn;
    @JsonProperty
    private final String redirectUri;

    @JsonCreator
    public ChangePasswordResponse(@JsonProperty("changeSucceeded") final boolean changeSucceeded,
                                  @JsonProperty("failedOn") final String[] failedOn,
                                  @JsonProperty("redirectUri")final String redirectUri) {
        this.changeSucceeded = changeSucceeded;
        this.failedOn = failedOn;
        this.redirectUri = redirectUri;
    }

    public boolean isChangeSucceeded() {
        return changeSucceeded;
    }

    public String[] getFailedOn() {
        return failedOn;
    }

    public String getRedirectUri() {
        return redirectUri;
    }
}
