package stroom.authentication.authenticate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class ResetPasswordRequest {
    @JsonProperty
    private final String newPassword;

    @JsonCreator
    public ResetPasswordRequest(@JsonProperty("newPassword") final String newPassword) {
        this.newPassword = newPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }
}
