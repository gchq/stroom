package stroom.authentication.resources.authentication.v1;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

public class ChangePasswordResponse {
    @NotNull
    private boolean changeSucceeded = true;

    @NotNull
    private PasswordValidationFailureType[] failedOn;

    public boolean isChangeSucceeded() {
        return changeSucceeded;
    }

    public PasswordValidationFailureType[] getFailedOn() {
        return failedOn;
    }

    public static final class ChangePasswordResponseBuilder {
        public List<PasswordValidationFailureType> failedOn = new ArrayList<>();
        private boolean changeSucceeded;

        private ChangePasswordResponseBuilder() {
        }

        public static ChangePasswordResponseBuilder aChangePasswordResponse() {
            return new ChangePasswordResponseBuilder();
        }

        public ChangePasswordResponseBuilder withSuccess() {
            this.changeSucceeded = true;
            return this;
        }

        public ChangePasswordResponseBuilder withFailedOn(List<PasswordValidationFailureType> failedOn) {
            this.failedOn.addAll(failedOn);
            this.changeSucceeded = false;
            return this;
        }

        public ChangePasswordResponse build() {
            ChangePasswordResponse changePasswordResponse = new ChangePasswordResponse();
            changePasswordResponse.failedOn = this.failedOn.toArray(new PasswordValidationFailureType[this.failedOn.size()]);
            changePasswordResponse.changeSucceeded = this.changeSucceeded;
            return changePasswordResponse;
        }
    }
}
