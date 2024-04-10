package stroom.job.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class ScheduleReferenceTime {

    @JsonProperty
    private final Long scheduleReferenceTime;
    @JsonProperty
    private final Long lastExecutedTime;

    @SuppressWarnings("checkstyle:lineLength")
    @JsonCreator
    public ScheduleReferenceTime(@JsonProperty("scheduleReferenceTime") final Long scheduleReferenceTime,
                                 @JsonProperty("lastExecutedTime") final Long lastExecutedTime) {
        this.scheduleReferenceTime = scheduleReferenceTime;
        this.lastExecutedTime = lastExecutedTime;
    }

    public Long getScheduleReferenceTime() {
        return scheduleReferenceTime;
    }

    public Long getLastExecutedTime() {
        return lastExecutedTime;
    }
}
