package stroom.analytics.shared;

import stroom.docref.DocRef;
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
public class AnalyticNotificationStreamConfig extends AnalyticNotificationConfig {

    @JsonProperty
    private final SimpleDuration timeToWaitForData;
    @JsonProperty
    private final DocRef destinationFeed;
    @JsonProperty
    private final boolean useSourceFeedIfPossible;

    @JsonCreator
    public AnalyticNotificationStreamConfig(@JsonProperty("timeToWaitForData") final SimpleDuration timeToWaitForData,
                                            @JsonProperty("destinationFeed") final DocRef destinationFeed,
                                            @JsonProperty("useSourceFeedIfPossible") final boolean useSourceFeedIfPossible) {
        this.timeToWaitForData = timeToWaitForData;
        this.destinationFeed = destinationFeed;
        this.useSourceFeedIfPossible = useSourceFeedIfPossible;
    }

    public SimpleDuration getTimeToWaitForData() {
        return timeToWaitForData;
    }

    public DocRef getDestinationFeed() {
        return destinationFeed;
    }

    public boolean isUseSourceFeedIfPossible() {
        return useSourceFeedIfPossible;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AnalyticNotificationStreamConfig that = (AnalyticNotificationStreamConfig) o;
        return Objects.equals(timeToWaitForData, that.timeToWaitForData) &&
                Objects.equals(destinationFeed, that.destinationFeed) &&
                useSourceFeedIfPossible == that.useSourceFeedIfPossible;
    }

    @Override
    public int hashCode() {
        return Objects.hash(timeToWaitForData,
                destinationFeed,
                useSourceFeedIfPossible);
    }

    @Override
    public String toString() {
        return "AnalyticNotification{" +
                "timeToWaitForData=" + timeToWaitForData +
                ", destinationFeed=" + destinationFeed +
                ", useSourceFeedIfPossible=" + useSourceFeedIfPossible +
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
        private DocRef destinationFeed;
        private boolean useSourceFeedIfPossible;

        private Builder() {
        }

        private Builder(final AnalyticNotificationStreamConfig config) {
            this.timeToWaitForData = config.timeToWaitForData;
            this.destinationFeed = config.destinationFeed;
            this.useSourceFeedIfPossible = config.useSourceFeedIfPossible;
        }

        public Builder timeToWaitForData(final SimpleDuration timeToWaitForData) {
            this.timeToWaitForData = timeToWaitForData;
            return this;
        }

        public Builder destinationFeed(final DocRef destinationFeed) {
            this.destinationFeed = destinationFeed;
            return this;
        }

        public Builder useSourceFeedIfPossible(final boolean useSourceFeedIfPossible) {
            this.useSourceFeedIfPossible = useSourceFeedIfPossible;
            return this;
        }

        public AnalyticNotificationStreamConfig build() {
            return new AnalyticNotificationStreamConfig(
                    timeToWaitForData,
                    destinationFeed,
                    useSourceFeedIfPossible);
        }
    }
}
