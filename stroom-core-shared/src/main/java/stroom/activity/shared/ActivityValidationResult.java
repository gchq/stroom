package stroom.activity.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class ActivityValidationResult {
    @JsonProperty
    private final boolean valid;
    @JsonProperty
    private final String messages;

    @JsonCreator
    public ActivityValidationResult(@JsonProperty("valid") final boolean valid,
                                    @JsonProperty("messages") final String messages) {
        this.valid = valid;
        this.messages = messages;
    }

    public boolean isValid() {
        return valid;
    }

    public String getMessages() {
        return messages;
    }
}
