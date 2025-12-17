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

package stroom.dashboard.shared;

import stroom.docref.DocRef;
import stroom.query.api.OffsetRange;
import stroom.query.api.ResultRequest.Fetch;
import stroom.query.api.TableSettings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({
        "componentId",
        "fetch",
        "visualisation",
        "json",
        "tableSettings",
        "requestedRange"})
@JsonInclude(Include.NON_NULL)
public final class VisResultRequest extends ComponentResultRequest {

    @JsonProperty
    private final DocRef visualisation;
    @JsonProperty
    private final String json;
    @JsonProperty
    private final TableSettings tableSettings;
    @JsonProperty
    private final OffsetRange requestedRange;

    @JsonCreator
    public VisResultRequest(@JsonProperty("componentId") final String componentId,
                            @JsonProperty("fetch") final Fetch fetch,
                            @JsonProperty("visualisation") final DocRef visualisation,
                            @JsonProperty("json") final String json,
                            @JsonProperty("tableSettings") final TableSettings tableSettings,
                            @JsonProperty("requestedRange") final OffsetRange requestedRange) {
        super(componentId, fetch);
        this.visualisation = visualisation;
        this.json = json;
        this.tableSettings = tableSettings;
        this.requestedRange = requestedRange;
    }

    public DocRef getVisualisation() {
        return visualisation;
    }

    public String getJson() {
        return json;
    }

    public TableSettings getTableSettings() {
        return tableSettings;
    }

    public OffsetRange getRequestedRange() {
        return requestedRange;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final VisResultRequest that = (VisResultRequest) o;
        return Objects.equals(visualisation, that.visualisation) &&
               Objects.equals(json, that.json) &&
               Objects.equals(tableSettings, that.tableSettings) &&
               Objects.equals(requestedRange, that.requestedRange);
    }

    @Override
    public int hashCode() {
        return Objects.hash(visualisation, json, tableSettings, requestedRange);
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
        private DocRef visualisation;
        private String json;
        private TableSettings tableSettings;
        private OffsetRange requestedRange;

        private Builder() {
        }

        private Builder(final VisResultRequest visResultRequest) {
            this.componentId = visResultRequest.getComponentId();
            this.fetch = visResultRequest.getFetch();
            this.visualisation = visResultRequest.visualisation;
            this.json = visResultRequest.json;
            this.tableSettings = visResultRequest.tableSettings;
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

        public Builder visualisation(final DocRef visualisation) {
            this.visualisation = visualisation;
            return this;
        }

        public Builder json(final String json) {
            this.json = json;
            return this;
        }

        public Builder tableSettings(final TableSettings tableSettings) {
            this.tableSettings = tableSettings;
            return this;
        }

        public Builder requestedRange(final OffsetRange requestedRange) {
            this.requestedRange = requestedRange;
            return this;
        }

        public VisResultRequest build() {
            return new VisResultRequest(
                    componentId,
                    fetch,
                    visualisation,
                    json,
                    tableSettings,
                    requestedRange);
        }
    }
}
