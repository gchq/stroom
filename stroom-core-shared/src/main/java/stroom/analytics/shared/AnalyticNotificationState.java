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
    private final Long lastTimeFilterTo;
    @JsonProperty
    private final String message;

    @JsonCreator
    public AnalyticNotificationState(@JsonProperty("notificationUuid") final String notificationUuid,
                                     @JsonProperty("lastTimeFilterTo") final Long lastTimeFilterTo,
                                     @JsonProperty("message") final String message) {
        this.notificationUuid = notificationUuid;
        this.lastTimeFilterTo = lastTimeFilterTo;
        this.message = message;
    }

    public String getNotificationUuid() {
        return notificationUuid;
    }

    public Long getLastTimeFilterTo() {
        return lastTimeFilterTo;
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
        private Long lastTimeFilterTo;
        private String message;

        private Builder() {
        }

        private Builder(final AnalyticNotificationState analyticNotificationState) {
            this.notificationUuid = analyticNotificationState.notificationUuid;
            this.lastTimeFilterTo = analyticNotificationState.lastTimeFilterTo;
            this.message = analyticNotificationState.message;
        }

        public Builder notificationUuid(final String notificationUuid) {
            this.notificationUuid = notificationUuid;
            return this;
        }


        public Builder lastTimeFilterTo(final Long lastTimeFilterTo) {
            this.lastTimeFilterTo = lastTimeFilterTo;
            return this;
        }

        public Builder message(final String message) {
            this.message = message;
            return this;
        }

        public AnalyticNotificationState build() {
            return new AnalyticNotificationState(
                    notificationUuid,
                    lastTimeFilterTo,
                    message);
        }
    }
}
