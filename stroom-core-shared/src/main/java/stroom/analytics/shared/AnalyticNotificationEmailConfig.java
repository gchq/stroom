package stroom.analytics.shared;

import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class AnalyticNotificationEmailConfig extends AnalyticNotificationConfig {

    @JsonProperty
    private final SimpleDuration timeToWaitForData;
    @JsonProperty
    private final String emailAddress;

    @JsonCreator
    public AnalyticNotificationEmailConfig(@JsonProperty("timeToWaitForData") final SimpleDuration timeToWaitForData,
                                           @JsonProperty("emailAddress") final String emailAddress) {
        this.timeToWaitForData = timeToWaitForData;
        this.emailAddress = emailAddress;
    }

    public SimpleDuration getTimeToWaitForData() {
        return timeToWaitForData;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AnalyticNotificationEmailConfig that = (AnalyticNotificationEmailConfig) o;
        return Objects.equals(timeToWaitForData, that.timeToWaitForData) &&
                Objects.equals(emailAddress, that.emailAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timeToWaitForData,
                emailAddress);
    }

    @Override
    public String toString() {
        return "AnalyticNotification{" +
                "timeToWaitForData=" + timeToWaitForData +
                ", emailAddress=" + emailAddress +
                '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private SimpleDuration timeToWaitForData = new SimpleDuration(1, TimeUnit.HOURS);
        private String emailAddress;

        private Builder() {
        }

        private Builder(final AnalyticNotificationEmailConfig config) {
            this.timeToWaitForData = config.timeToWaitForData;
            this.emailAddress = config.emailAddress;
        }

        public Builder timeToWaitForData(final SimpleDuration timeToWaitForData) {
            this.timeToWaitForData = timeToWaitForData;
            return this;
        }

        public Builder emailAddress(final String emailAddress) {
            this.emailAddress = emailAddress;
            return this;
        }

        public AnalyticNotificationEmailConfig build() {
            return new AnalyticNotificationEmailConfig(
                    timeToWaitForData,
                    emailAddress);
        }
    }
}
