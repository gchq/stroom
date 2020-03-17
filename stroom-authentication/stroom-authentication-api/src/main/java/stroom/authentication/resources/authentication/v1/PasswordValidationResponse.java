package stroom.authentication.resources.authentication.v1;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PasswordValidationResponse {

    @NotNull
    private PasswordValidationFailureType[] failedOn;

    public PasswordValidationFailureType[] getFailedOn() {
        return failedOn;
    }

    public static final class PasswordValidationResponseBuilder {
        List<PasswordValidationFailureType> failedOn = new ArrayList<>();

        private PasswordValidationResponseBuilder() {
        }

        public static PasswordValidationResponseBuilder aPasswordValidationResponse() {
            return new PasswordValidationResponseBuilder();
        }

        public PasswordValidationResponseBuilder withFailedOn(PasswordValidationFailureType... failedOn) {
            this.failedOn.addAll(Arrays.asList(failedOn));
            return this;
        }

        public PasswordValidationResponse build() {
            PasswordValidationResponse passwordValidationResponse = new PasswordValidationResponse();
            passwordValidationResponse.failedOn = this.failedOn.toArray(new PasswordValidationFailureType[this.failedOn.size()]);
            return passwordValidationResponse;
        }
    }
}
