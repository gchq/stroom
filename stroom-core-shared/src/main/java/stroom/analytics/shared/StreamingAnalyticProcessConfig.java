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

@Deprecated
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public final class StreamingAnalyticProcessConfig extends AnalyticProcessConfig {

    @JsonProperty
    private final DocRef errorFeed;

    @JsonCreator
    public StreamingAnalyticProcessConfig(@JsonProperty("errorFeed") final DocRef errorFeed) {
        this.errorFeed = errorFeed;
    }

    @Deprecated
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
        if (!super.equals(o)) {
            return false;
        }
        final StreamingAnalyticProcessConfig that = (StreamingAnalyticProcessConfig) o;
        return Objects.equals(errorFeed, that.errorFeed);
    }

    @Override
    public int hashCode() {
        return Objects.hash(errorFeed);
    }

    @Override
    public String toString() {
        return "StreamingAnalyticProcessConfig{" +
                "errorFeed=" + errorFeed +
                '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private DocRef errorFeed;

        private Builder() {
            super();
        }

        private Builder(final StreamingAnalyticProcessConfig streamingAnalyticProcessConfig) {
            this.errorFeed = streamingAnalyticProcessConfig.errorFeed;
        }

        public Builder errorFeed(final DocRef errorFeed) {
            this.errorFeed = errorFeed;
            return this;
        }

        public StreamingAnalyticProcessConfig build() {
            return new StreamingAnalyticProcessConfig(errorFeed);
        }
    }
}
