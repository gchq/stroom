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

package stroom.dashboard.shared;

import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.ResultRequest.Fetch;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({
        "componentId",
        "fetch",
        "visDashboardSettings",
        "requestedRange"})
@JsonInclude(Include.NON_NULL)
public class VisResultRequest extends ComponentResultRequest {
    @JsonProperty
    private final VisComponentSettings visDashboardSettings;
    @JsonProperty
    private final OffsetRange requestedRange;

    @JsonCreator
    public VisResultRequest(@JsonProperty("componentId") final String componentId,
                            @JsonProperty("fetch") final Fetch fetch,
                            @JsonProperty("visDashboardSettings") final VisComponentSettings visDashboardSettings,
                            @JsonProperty("requestedRange") final OffsetRange requestedRange) {
        super(componentId, fetch);
        this.visDashboardSettings = visDashboardSettings;
        this.requestedRange = requestedRange;
    }

    public VisComponentSettings getVisDashboardSettings() {
        return visDashboardSettings;
    }

    public OffsetRange getRequestedRange() {
        return requestedRange;
    }

    @SuppressWarnings("checkstyle:needbraces")
    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final VisResultRequest that = (VisResultRequest) o;
        return Objects.equals(visDashboardSettings, that.visDashboardSettings) &&
                Objects.equals(requestedRange, that.requestedRange);
    }

    @Override
    public int hashCode() {
        return Objects.hash(visDashboardSettings, requestedRange);
    }

    @Override
    public String toString() {
        return "VisResultRequest{" +
                "visDashboardSettings=" + visDashboardSettings +
                ", requestedRange=" + requestedRange +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {
        private String componentId;
        private Fetch fetch;
        private VisComponentSettings visDashboardSettings;
        private OffsetRange requestedRange;

        private Builder() {
        }

        private Builder(final VisResultRequest visResultRequest) {
            this.componentId = visResultRequest.getComponentId();
            this.fetch = visResultRequest.getFetch();
            this.visDashboardSettings = visResultRequest.visDashboardSettings;
            this.requestedRange = visResultRequest.requestedRange;
        }

        public Builder componentId(final String componentId) {
            this.componentId = componentId;
            return this;
        }

        public Builder fetch(final Fetch fetch) {
            this.fetch = fetch;
            return this;
        }

        public Builder visDashboardSettings(final VisComponentSettings visDashboardSettings) {
            this.visDashboardSettings = visDashboardSettings;
            return this;
        }

        public Builder requestedRange(final OffsetRange requestedRange) {
            this.requestedRange = requestedRange;
            return this;
        }

        public VisResultRequest build() {
            return new VisResultRequest(componentId, fetch, visDashboardSettings, requestedRange);
        }
    }
}
