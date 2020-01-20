package stroom.auth.resources.authentication.v1;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@ApiModel(description = "A response to a to validate user's password.")
public class PasswordValidationResponse {

    @NotNull
    @ApiModelProperty(value = "If the request has failed, how has it failed?", required = true)
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

        public PasswordValidationResponseBuilder withFailedOn(PasswordValidationFailureType ...failedOn) {
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
