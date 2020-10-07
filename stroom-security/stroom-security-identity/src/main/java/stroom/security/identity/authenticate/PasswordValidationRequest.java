package stroom.security.identity.authenticate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class PasswordValidationRequest {
    @JsonProperty
    private final String email;
    @JsonProperty
    private final String oldPassword;
    @JsonProperty
    private final String newPassword;

    @JsonCreator
    public PasswordValidationRequest(@JsonProperty("email") final String email,
                                     @JsonProperty("oldPassword") final String oldPassword,
                                     @JsonProperty("newPassword") final String newPassword) {
        this.email = email;
        this.oldPassword = oldPassword;
        this.newPassword = newPassword;
    }

    public String getEmail() {
        return email;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public String getOldPassword() {
        return oldPassword;
    }
}
