package stroom.job.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "allowSecond",
        "allowMinute",
        "allowHour"
})
@JsonInclude(Include.NON_NULL)
public class ScheduleRestriction {

    @JsonProperty
    private final boolean allowSecond;
    @JsonProperty
    private final boolean allowMinute;
    @JsonProperty
    private final boolean allowHour;

    @JsonCreator
    public ScheduleRestriction(@JsonProperty("allowSecond") final boolean allowSecond,
                               @JsonProperty("allowMinute") final boolean allowMinute,
                               @JsonProperty("allowHour") final boolean allowHour) {
        this.allowSecond = allowSecond;
        this.allowMinute = allowMinute;
        this.allowHour = allowHour;
    }

    public boolean isAllowSecond() {
        return allowSecond;
    }

    public boolean isAllowMinute() {
        return allowMinute;
    }

    public boolean isAllowHour() {
        return allowHour;
    }
}
