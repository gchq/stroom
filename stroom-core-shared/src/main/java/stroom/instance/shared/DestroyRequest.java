package stroom.instance.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class DestroyRequest {

    @JsonProperty
    private final ApplicationInstanceInfo applicationInstanceInfo;
    @JsonProperty
    private final String reason;

    @JsonCreator
    public DestroyRequest(
            @JsonProperty("applicationInstanceInfo") final ApplicationInstanceInfo applicationInstanceInfo,
            @JsonProperty("reason") final String reason) {
        this.applicationInstanceInfo = applicationInstanceInfo;
        this.reason = reason;
    }

    public ApplicationInstanceInfo getApplicationInstanceInfo() {
        return applicationInstanceInfo;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "DestroyRequest{" +
                "applicationInstanceInfo=" + applicationInstanceInfo +
                ", reason='" + reason + '\'' +
                '}';
    }
}
