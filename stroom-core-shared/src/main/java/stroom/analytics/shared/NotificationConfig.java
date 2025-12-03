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

import stroom.util.shared.time.SimpleDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class NotificationConfig {

    @JsonProperty
    private final String uuid;
    @JsonProperty
    private final Boolean enabled;
    @JsonProperty
    private final boolean limitNotifications;
    @JsonProperty
    private final int maxNotifications;
    @JsonProperty
    private final SimpleDuration resumeAfter;
    @JsonProperty
    private final NotificationDestinationType destinationType;
    @JsonProperty
    private final NotificationDestination destination;

    @SuppressWarnings("checkstyle:lineLength")
    @JsonCreator
    public NotificationConfig(@JsonProperty("uuid") final String uuid,
                              @JsonProperty("enabled") final Boolean enabled,
                              @JsonProperty("limitNotifications") final boolean limitNotifications,
                              @JsonProperty("maxNotifications") final int maxNotifications,
                              @JsonProperty("resumeAfter") final SimpleDuration resumeAfter,
                              @JsonProperty("destinationType") final NotificationDestinationType destinationType,
                              @JsonProperty("destination") final NotificationDestination destination) {
        this.uuid = uuid;
        this.enabled = enabled;
        this.limitNotifications = limitNotifications;
        this.maxNotifications = maxNotifications;
        this.resumeAfter = resumeAfter;
        this.destinationType = destinationType;
        this.destination = destination;
    }

    public String getUuid() {
        return uuid;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    @JsonIgnore
    public boolean isEnabled() {
        return enabled != Boolean.FALSE;
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

    public NotificationDestinationType getDestinationType() {
        return destinationType;
    }

    public NotificationDestination getDestination() {
        return destination;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final NotificationConfig that = (NotificationConfig) o;
        return Objects.equals(uuid, that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }

    @Override
    public String toString() {
        return "AnalyticNotificationConfig{" +
               "uuid='" + uuid + '\'' +
               ", enabled=" + enabled +
               ", limitNotifications=" + limitNotifications +
               ", maxNotifications=" + maxNotifications +
               ", resumeAfter=" + resumeAfter +
               ", destinationType=" + destinationType +
               ", destination=" + destination +
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

        private String uuid;
        private Boolean enabled = true;
        private boolean limitNotifications;
        private int maxNotifications = 1;
        private SimpleDuration resumeAfter;
        private NotificationDestinationType destinationType;
        private NotificationDestination destination;

        public Builder() {
        }

        public Builder(final NotificationConfig doc) {
            this.uuid = doc.uuid;
            this.enabled = doc.enabled;
            this.limitNotifications = doc.limitNotifications;
            this.maxNotifications = doc.maxNotifications;
            this.resumeAfter = doc.resumeAfter;
            this.destinationType = doc.destinationType;
            this.destination = doc.destination;
        }

        public Builder uuid(final String uuid) {
            this.uuid = uuid;
            return this;
        }

        public Builder enabled(final Boolean enabled) {
            this.enabled = enabled;
            return this;
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

        public Builder destinationType(final NotificationDestinationType destinationType) {
            this.destinationType = destinationType;
            return this;
        }

        public Builder destination(final NotificationDestination destination) {
            this.destination = destination;
            return this;
        }

        public NotificationConfig build() {
            return new NotificationConfig(
                    uuid,
                    enabled,
                    limitNotifications,
                    maxNotifications,
                    resumeAfter,
                    destinationType,
                    destination);
        }
    }
}
