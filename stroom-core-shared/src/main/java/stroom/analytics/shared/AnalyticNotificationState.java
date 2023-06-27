package stroom.analytics.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class AnalyticNotificationState {

    @JsonProperty
    private final String notificationUuid;
    @JsonProperty
    private final Long lastExecutionTime;
    @JsonProperty
    private final String message;

    @JsonCreator
    public AnalyticNotificationState(@JsonProperty("notificationUuid") final String notificationUuid,
                                     @JsonProperty("lastExecutionTime") final Long lastExecutionTime,
                                     @JsonProperty("message") final String message) {
        this.notificationUuid = notificationUuid;
        this.lastExecutionTime = lastExecutionTime;
        this.message = message;
    }

    public String getNotificationUuid() {
        return notificationUuid;
    }

    public Long getLastExecutionTime() {
        return lastExecutionTime;
    }

    public String getMessage() {
        return message;
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String notificationUuid;
        private Long lastExecutionTime;
        private String message;

        private Builder() {
        }

        private Builder(final AnalyticNotificationState analyticNotificationState) {
            this.notificationUuid = analyticNotificationState.notificationUuid;
            this.lastExecutionTime = analyticNotificationState.lastExecutionTime;
            this.message = analyticNotificationState.message;
        }

        public Builder notificationUuid(final String notificationUuid) {
            this.notificationUuid = notificationUuid;
            return this;
        }


        public Builder lastExecutionTime(final Long lastExecutionTime) {
            this.lastExecutionTime = lastExecutionTime;
            return this;
        }

        public Builder message(final String message) {
            this.message = message;
            return this;
        }

        public AnalyticNotificationState build() {
            return new AnalyticNotificationState(
                    notificationUuid,
                    lastExecutionTime,
                    message);
        }
    }
}
