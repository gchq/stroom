package stroom.analytics.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class AnalyticNotificationEmailDestination extends AnalyticNotificationDestination {

    @JsonProperty
    private final String emailAddress;

    @JsonCreator
    public AnalyticNotificationEmailDestination(@JsonProperty("emailAddress") final String emailAddress) {
        this.emailAddress = emailAddress;
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
        final AnalyticNotificationEmailDestination that = (AnalyticNotificationEmailDestination) o;
        return Objects.equals(emailAddress, that.emailAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(emailAddress);
    }

    @Override
    public String toString() {
        return "AnalyticNotificationEmailDestination{" +
                "emailAddress=" + emailAddress +
                '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }


    public static class Builder {

        private String emailAddress;

        private Builder() {
        }

        private Builder(final AnalyticNotificationEmailDestination config) {
            this.emailAddress = config.emailAddress;
        }

        public Builder emailAddress(final String emailAddress) {
            this.emailAddress = emailAddress;
            return this;
        }

        public AnalyticNotificationEmailDestination build() {
            return new AnalyticNotificationEmailDestination(emailAddress);
        }
    }
}
