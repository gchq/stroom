package stroom.authentication.resources.authentication.v1;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotNull;

@ApiModel(description = "A request to validate a user's password.")
public class PasswordValidationRequest {

    @ApiModelProperty(value = "THe user's old password", required = false)
    private String oldPassword;

    @NotNull
    @ApiModelProperty(value = "The user's email address.", required = true)
    private String email;

    @NotNull
    @ApiModelProperty(value = "The new password for the user.", required = true)
    private String newPassword;

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getOldPassword() {
        return oldPassword;
    }

    public void setOldPassword(String oldPassword) {
        this.oldPassword = oldPassword;
    }
}
