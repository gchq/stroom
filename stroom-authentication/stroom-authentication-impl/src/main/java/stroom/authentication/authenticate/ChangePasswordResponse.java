package stroom.authentication.authenticate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(Include.NON_NULL)
public class ChangePasswordResponse {
    @JsonProperty
    private final boolean changeSucceeded;
    @JsonProperty
    private final PasswordValidationFailureType[] failedOn;

    @JsonCreator
    public ChangePasswordResponse(@JsonProperty("changeSucceeded") final boolean changeSucceeded,
                                  @JsonProperty("failedOn") final PasswordValidationFailureType[] failedOn) {
        this.changeSucceeded = changeSucceeded;
        this.failedOn = failedOn;
    }

    public boolean isChangeSucceeded() {
        return changeSucceeded;
    }

    public PasswordValidationFailureType[] getFailedOn() {
        return failedOn;
    }

    public static final class ChangePasswordResponseBuilder {
        public List<PasswordValidationFailureType> failedOn = new ArrayList<>();
        private boolean changeSucceeded = true;

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
            return new ChangePasswordResponse(changeSucceeded, failedOn.toArray(new PasswordValidationFailureType[0]));
        }
    }
}
