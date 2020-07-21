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
    private final String message;
    @JsonProperty
    private final boolean forceSignIn;

    @JsonCreator
    public ChangePasswordResponse(@JsonProperty("changeSucceeded") final boolean changeSucceeded,
                                  @JsonProperty("message") final String message,
                                  @JsonProperty("forceSignIn") final boolean forceSignIn) {
        this.changeSucceeded = changeSucceeded;
        this.message = message;
        this.forceSignIn = forceSignIn;
    }

    public boolean isChangeSucceeded() {
        return changeSucceeded;
    }

    public String getMessage() {
        return message;
    }

    public boolean isForceSignIn() {
        return forceSignIn;
    }
}
