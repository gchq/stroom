package stroom.security.identity.authenticate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class ResetPasswordRequest {
    @JsonProperty
    private final String newPassword;
    @JsonProperty
    private final String confirmNewPassword;

    @JsonCreator
    public ResetPasswordRequest(@JsonProperty("newPassword") final String newPassword,
                                @JsonProperty("confirmNewPassword") final String confirmNewPassword) {
        this.newPassword = newPassword;
        this.confirmNewPassword = confirmNewPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public String getConfirmNewPassword() {
        return confirmNewPassword;
    }
}
