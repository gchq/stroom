package stroom.authentication.resources.authentication.v1;

import javax.validation.constraints.NotNull;

public class ResetPasswordRequest {

    @NotNull
    private String newPassword;

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
