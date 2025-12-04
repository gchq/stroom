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
import stroom.util.shared.time.SimpleDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@Deprecated
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public final class ScheduledQueryAnalyticProcessConfig extends AnalyticProcessConfig {

    @JsonProperty
    private final boolean enabled;
    @JsonProperty
    private final String node;
    @JsonProperty
    private final DocRef errorFeed;
    @JsonProperty
    private final Long minEventTimeMs;
    @JsonProperty
    private final Long maxEventTimeMs;
    @JsonProperty
    private final SimpleDuration timeToWaitForData;
    @JsonProperty
    private final SimpleDuration queryFrequency;

    @SuppressWarnings({"unused", "checkstyle:LineLength"})
    @JsonCreator
    public ScheduledQueryAnalyticProcessConfig(@JsonProperty("enabled") final boolean enabled,
                                               @JsonProperty("node") final String node,
                                               @JsonProperty("errorFeed") final DocRef errorFeed,
                                               @JsonProperty("minEventTimeMs") final Long minEventTimeMs,
                                               @JsonProperty("maxEventTimeMs") final Long maxEventTimeMs,
                                               @JsonProperty("timeToWaitForData") final SimpleDuration timeToWaitForData,
                                               @JsonProperty("queryFrequency") final SimpleDuration queryFrequency) {
        this.enabled = enabled;
        this.node = node;
        this.errorFeed = errorFeed;
        this.minEventTimeMs = minEventTimeMs;
        this.maxEventTimeMs = maxEventTimeMs;
        this.timeToWaitForData = timeToWaitForData;
        this.queryFrequency = queryFrequency;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getNode() {
        return node;
    }

    @Deprecated
    public DocRef getErrorFeed() {
        return errorFeed;
    }

    public Long getMinEventTimeMs() {
        return minEventTimeMs;
    }

    public Long getMaxEventTimeMs() {
        return maxEventTimeMs;
    }

    public SimpleDuration getTimeToWaitForData() {
        return timeToWaitForData;
    }

    public SimpleDuration getQueryFrequency() {
        return queryFrequency;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final ScheduledQueryAnalyticProcessConfig that = (ScheduledQueryAnalyticProcessConfig) o;
        return enabled == that.enabled &&
                Objects.equals(node, that.node) &&
                Objects.equals(errorFeed, that.errorFeed) &&
                Objects.equals(minEventTimeMs, that.minEventTimeMs) &&
                Objects.equals(maxEventTimeMs, that.maxEventTimeMs) &&
                Objects.equals(timeToWaitForData, that.timeToWaitForData) &&
                Objects.equals(queryFrequency, that.queryFrequency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                enabled,
                node,
                errorFeed,
                minEventTimeMs,
                maxEventTimeMs,
                timeToWaitForData,
                queryFrequency);
    }

    @Override
    public String toString() {
        return "ScheduledQueryAnalyticProcessConfig{" +
                "enabled=" + enabled +
                ", node=" + node +
                ", errorFeed=" + errorFeed +
                ", minEventTimeMs=" + minEventTimeMs +
                ", maxEventTimeMs=" + maxEventTimeMs +
                ", timeToWaitForData=" + timeToWaitForData +
                ", queryFrequency=" + queryFrequency +
                '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private boolean enabled;
        private String node;
        private DocRef errorFeed;
        private Long minEventTimeMs;
        private Long maxEventTimeMs;
        private SimpleDuration timeToWaitForData;
        private SimpleDuration queryFrequency;

        private Builder() {
        }

        private Builder(final ScheduledQueryAnalyticProcessConfig scheduledQueryAnalyticProcessConfig) {
            this.enabled = scheduledQueryAnalyticProcessConfig.enabled;
            this.node = scheduledQueryAnalyticProcessConfig.node;
            this.errorFeed = scheduledQueryAnalyticProcessConfig.errorFeed;
            this.minEventTimeMs = scheduledQueryAnalyticProcessConfig.minEventTimeMs;
            this.maxEventTimeMs = scheduledQueryAnalyticProcessConfig.maxEventTimeMs;
            this.timeToWaitForData = scheduledQueryAnalyticProcessConfig.timeToWaitForData;
            this.queryFrequency = scheduledQueryAnalyticProcessConfig.queryFrequency;
        }

        public Builder enabled(final boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder node(final String node) {
            this.node = node;
            return this;
        }

        public Builder errorFeed(final DocRef errorFeed) {
            this.errorFeed = errorFeed;
            return this;
        }

        public Builder minEventTimeMs(final Long minEventTimeMs) {
            this.minEventTimeMs = minEventTimeMs;
            return this;
        }

        public Builder maxEventTimeMs(final Long maxEventTimeMs) {
            this.maxEventTimeMs = maxEventTimeMs;
            return this;
        }

        public Builder timeToWaitForData(final SimpleDuration timeToWaitForData) {
            this.timeToWaitForData = timeToWaitForData;
            return this;
        }

        public Builder queryFrequency(final SimpleDuration queryFrequency) {
            this.queryFrequency = queryFrequency;
            return this;
        }

        public ScheduledQueryAnalyticProcessConfig build() {
            return new ScheduledQueryAnalyticProcessConfig(
                    enabled,
                    node,
                    errorFeed,
                    minEventTimeMs,
                    maxEventTimeMs,
                    timeToWaitForData,
                    queryFrequency);
        }
    }
}
