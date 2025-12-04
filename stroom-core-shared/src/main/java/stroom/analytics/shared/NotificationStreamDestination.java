package stroom.analytics.shared;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public final class NotificationStreamDestination extends NotificationDestination {

    @JsonProperty
    private final DocRef destinationFeed;
    @JsonProperty
    private final boolean useSourceFeedIfPossible;
    @JsonProperty
    private final boolean includeRuleDocumentation;

    @JsonCreator
    public NotificationStreamDestination(
            @JsonProperty("destinationFeed") final DocRef destinationFeed,
            @JsonProperty("useSourceFeedIfPossible") final boolean useSourceFeedIfPossible,
            @JsonProperty("includeRuleDocumentation") final boolean includeRuleDocumentation) {

        this.destinationFeed = destinationFeed;
        this.useSourceFeedIfPossible = useSourceFeedIfPossible;
        this.includeRuleDocumentation = includeRuleDocumentation;

    }

    public DocRef getDestinationFeed() {
        return destinationFeed;
    }

    public boolean isUseSourceFeedIfPossible() {
        return useSourceFeedIfPossible;
    }

    public boolean isIncludeRuleDocumentation() {
        return includeRuleDocumentation;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final NotificationStreamDestination that = (NotificationStreamDestination) o;
        return Objects.equals(destinationFeed, that.destinationFeed) &&
               (useSourceFeedIfPossible == that.useSourceFeedIfPossible) &&
               (includeRuleDocumentation == that.includeRuleDocumentation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(destinationFeed, useSourceFeedIfPossible, includeRuleDocumentation);
    }

    @Override
    public String toString() {
        return "AnalyticNotificationStreamDestination{" +
                ", destinationFeed=" + destinationFeed +
                ", useSourceFeedIfPossible=" + useSourceFeedIfPossible +
                ", includeRuleDocumentation=" + includeRuleDocumentation +
                '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }


    // --------------------------------------------------------------------------------


    public static class Builder {

        private DocRef destinationFeed;
        private boolean useSourceFeedIfPossible;
        private boolean includeRuleDocumentation;

        private Builder() {
        }

        private Builder(final NotificationStreamDestination config) {
            this.destinationFeed = config.destinationFeed;
            this.useSourceFeedIfPossible = config.useSourceFeedIfPossible;
            this.includeRuleDocumentation = config.includeRuleDocumentation;
        }

        public Builder destinationFeed(final DocRef destinationFeed) {
            this.destinationFeed = destinationFeed;
            return this;
        }

        public Builder useSourceFeedIfPossible(final boolean useSourceFeedIfPossible) {
            this.useSourceFeedIfPossible = useSourceFeedIfPossible;
            return this;
        }

        public Builder includeRuleDocumentation(final boolean includeRuleDocumentation) {
            this.includeRuleDocumentation = includeRuleDocumentation;
            return this;
        }

        public NotificationStreamDestination build() {
            return new NotificationStreamDestination(
                    destinationFeed,
                    useSourceFeedIfPossible,
                    includeRuleDocumentation);
        }
    }
}
