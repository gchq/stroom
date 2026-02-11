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

package stroom.processor.shared;

import stroom.util.shared.time.SimpleDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class FeedDependencies {

    @JsonProperty
    private final List<FeedDependency> feedDependencies;
    @JsonProperty
    private final SimpleDuration minProcessingDelay;
    @JsonProperty
    private final SimpleDuration maxProcessingDelay;

    @JsonCreator
    public FeedDependencies(@JsonProperty("feedDependencies") final List<FeedDependency> feedDependencies,
                            @JsonProperty("minProcessingDelay") final SimpleDuration minProcessingDelay,
                            @JsonProperty("maxProcessingDelay") final SimpleDuration maxProcessingDelay) {
        this.feedDependencies = feedDependencies;
        this.minProcessingDelay = minProcessingDelay;
        this.maxProcessingDelay = maxProcessingDelay;
    }

    public List<FeedDependency> getFeedDependencies() {
        return feedDependencies;
    }

    public SimpleDuration getMinProcessingDelay() {
        return minProcessingDelay;
    }

    public SimpleDuration getMaxProcessingDelay() {
        return maxProcessingDelay;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FeedDependencies that = (FeedDependencies) o;
        return Objects.equals(feedDependencies, that.feedDependencies) &&
               Objects.equals(minProcessingDelay, that.minProcessingDelay) &&
               Objects.equals(maxProcessingDelay, that.maxProcessingDelay);
    }

    @Override
    public int hashCode() {
        return Objects.hash(feedDependencies, minProcessingDelay, maxProcessingDelay);
    }

    @Override
    public String toString() {
        return "FeedDependencies{" +
               "feedDependencies=" + feedDependencies +
               ", minProcessingDelay=" + minProcessingDelay +
               ", maxProcessingDelay=" + maxProcessingDelay +
               '}';
    }

    public static final class Builder {

        private List<FeedDependency> feedDependencies;
        private SimpleDuration minProcessingDelay;
        private SimpleDuration maxProcessingDelay;

        private Builder() {
        }

        private Builder(final FeedDependencies feedDependencies) {
            this.feedDependencies = feedDependencies.feedDependencies == null
                    ? null
                    : new ArrayList<>(feedDependencies.feedDependencies);
            this.minProcessingDelay = feedDependencies.minProcessingDelay;
            this.maxProcessingDelay = feedDependencies.maxProcessingDelay;
        }

        public Builder feedDependencies(final List<FeedDependency> feedDependencies) {
            this.feedDependencies = feedDependencies;
            return this;
        }

        public Builder minProcessingDelay(final SimpleDuration minProcessingDelay) {
            this.minProcessingDelay = minProcessingDelay;
            return this;
        }

        public Builder maxProcessingDelay(final SimpleDuration maxProcessingDelay) {
            this.maxProcessingDelay = maxProcessingDelay;
            return this;
        }

        public FeedDependencies build() {
            return new FeedDependencies(feedDependencies, minProcessingDelay, maxProcessingDelay);
        }
    }
}
