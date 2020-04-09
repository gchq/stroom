package stroom.authentication.authenticate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@JsonInclude(Include.NON_NULL)
public class PasswordValidationResponse {
    @JsonProperty
    private final PasswordValidationFailureType[] failedOn;

    @JsonCreator
    public PasswordValidationResponse(@JsonProperty("failedOn") final PasswordValidationFailureType[] failedOn) {
        this.failedOn = failedOn;
    }

    public PasswordValidationFailureType[] getFailedOn() {
        return failedOn;
    }

    public static final class PasswordValidationResponseBuilder {
        private final List<PasswordValidationFailureType> failedOn = new ArrayList<>();

        public PasswordValidationResponseBuilder withFailedOn(PasswordValidationFailureType... failedOn) {
            this.failedOn.addAll(Arrays.asList(failedOn));
            return this;
        }

        public PasswordValidationResponse build() {
            return new PasswordValidationResponse(failedOn.toArray(new PasswordValidationFailureType[0]));
        }
    }
}
