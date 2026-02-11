/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

    @JsonCreator
    public NotificationStreamDestination(
            @JsonProperty("destinationFeed") final DocRef destinationFeed,
            @JsonProperty("useSourceFeedIfPossible") final boolean useSourceFeedIfPossible) {

        this.destinationFeed = destinationFeed;
        this.useSourceFeedIfPossible = useSourceFeedIfPossible;

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
        final NotificationStreamDestination that = (NotificationStreamDestination) o;
        return Objects.equals(destinationFeed, that.destinationFeed)
               && useSourceFeedIfPossible == that.useSourceFeedIfPossible;
    }

    @Override
    public int hashCode() {
        return Objects.hash(destinationFeed, useSourceFeedIfPossible);
    }

    @Override
    public String toString() {
        return "AnalyticNotificationStreamDestination{" +
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


    // --------------------------------------------------------------------------------


    public static class Builder {

        private DocRef destinationFeed;
        private boolean useSourceFeedIfPossible;

        private Builder() {
        }

        private Builder(final NotificationStreamDestination config) {
            this.destinationFeed = config.destinationFeed;
            this.useSourceFeedIfPossible = config.useSourceFeedIfPossible;
        }

        public Builder destinationFeed(final DocRef destinationFeed) {
            this.destinationFeed = destinationFeed;
            return this;
        }

        public Builder useSourceFeedIfPossible(final boolean useSourceFeedIfPossible) {
            this.useSourceFeedIfPossible = useSourceFeedIfPossible;
            return this;
        }

        public NotificationStreamDestination build() {
            return new NotificationStreamDestination(
                    destinationFeed,
                    useSourceFeedIfPossible);
        }
    }
}
