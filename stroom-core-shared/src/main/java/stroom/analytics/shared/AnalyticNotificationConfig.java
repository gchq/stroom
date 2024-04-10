/*
 * Copyright 2017 Crown Copyright
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
import stroom.util.shared.time.SimpleDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class AnalyticNotificationConfig {

    @JsonProperty
    private final boolean limitNotifications;
    @JsonProperty
    private final int maxNotifications;
    @JsonProperty
    private final SimpleDuration resumeAfter;
    @JsonProperty
    private final AnalyticNotificationDestinationType destinationType;
    @JsonProperty
    private final AnalyticNotificationDestination destination;
    @JsonProperty
    private final DocRef errorFeed;

    @SuppressWarnings("checkstyle:lineLength")
    @JsonCreator
    public AnalyticNotificationConfig(@JsonProperty("limitNotifications") final boolean limitNotifications,
                                      @JsonProperty("maxNotifications") final int maxNotifications,
                                      @JsonProperty("resumeAfter") final SimpleDuration resumeAfter,
                                      @JsonProperty("destinationType") final AnalyticNotificationDestinationType destinationType,
                                      @JsonProperty("destination") final AnalyticNotificationDestination destination,
                                      @JsonProperty("errorFeed") final DocRef errorFeed) {
        this.limitNotifications = limitNotifications;
        this.maxNotifications = maxNotifications;
        this.resumeAfter = resumeAfter;
        this.destinationType = destinationType;
        this.destination = destination;
        this.errorFeed = errorFeed;
    }

    public boolean isLimitNotifications() {
        return limitNotifications;
    }

    public int getMaxNotifications() {
        return maxNotifications;
    }

    public SimpleDuration getResumeAfter() {
        return resumeAfter;
    }

    public AnalyticNotificationDestinationType getDestinationType() {
        return destinationType;
    }

    public AnalyticNotificationDestination getDestination() {
        return destination;
    }

    public DocRef getErrorFeed() {
        return errorFeed;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AnalyticNotificationConfig config = (AnalyticNotificationConfig) o;
        return limitNotifications == config.limitNotifications &&
                maxNotifications == config.maxNotifications &&
                Objects.equals(resumeAfter, config.resumeAfter) &&
                Objects.equals(destinationType, config.destinationType) &&
                Objects.equals(destination, config.destination) &&
                Objects.equals(errorFeed, config.errorFeed);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                limitNotifications,
                maxNotifications,
                resumeAfter,
                destinationType,
                destination,
                errorFeed);
    }

    @Override
    public String toString() {
        return "AnalyticNotificationConfig{" +
                "limitNotifications=" + limitNotifications +
                ", maxNotifications=" + maxNotifications +
                ", resumeAfter=" + resumeAfter +
                ", destinationType=" + destinationType +
                ", destination=" + destination +
                ", errorFeed=" + errorFeed +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }


    // --------------------------------------------------------------------------------


    public static class Builder {

        private boolean limitNotifications;
        private int maxNotifications = 1;
        private SimpleDuration resumeAfter;
        private AnalyticNotificationDestinationType destinationType;
        private AnalyticNotificationDestination destination;
        private DocRef errorFeed;

        public Builder() {
        }

        public Builder(final AnalyticNotificationConfig doc) {
            this.limitNotifications = doc.limitNotifications;
            this.maxNotifications = doc.maxNotifications;
            this.resumeAfter = doc.resumeAfter;
            this.destinationType = doc.destinationType;
            this.destination = doc.destination;
            this.errorFeed = doc.errorFeed;
        }

        public Builder limitNotifications(final boolean limitNotifications) {
            this.limitNotifications = limitNotifications;
            return this;
        }

        public Builder maxNotifications(final int maxNotifications) {
            this.maxNotifications = maxNotifications;
            return this;
        }

        public Builder resumeAfter(final SimpleDuration resumeAfter) {
            this.resumeAfter = resumeAfter;
            return this;
        }

        public Builder destinationType(final AnalyticNotificationDestinationType destinationType) {
            this.destinationType = destinationType;
            return this;
        }

        public Builder destination(final AnalyticNotificationDestination destination) {
            this.destination = destination;
            return this;
        }

        public Builder errorFeed(final DocRef errorFeed) {
            this.errorFeed = errorFeed;
            return this;
        }

        public AnalyticNotificationConfig build() {
            return new AnalyticNotificationConfig(
                    limitNotifications,
                    maxNotifications,
                    resumeAfter,
                    destinationType,
                    destination,
                    errorFeed);
        }
    }
}
