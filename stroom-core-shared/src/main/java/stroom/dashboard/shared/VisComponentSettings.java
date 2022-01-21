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

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({"tableId", "visualisation", "json", "tableSettings"})
@JsonInclude(Include.NON_NULL)
public class VisComponentSettings implements ComponentSettings {

    @JsonProperty
    private final String tableId;
    @JsonProperty
    private final DocRef visualisation;
    @JsonProperty
    private final String json;
    @JsonProperty
    private final TableComponentSettings tableSettings;

    @JsonCreator
    public VisComponentSettings(@JsonProperty("tableId") final String tableId,
                                @JsonProperty("visualisation") final DocRef visualisation,
                                @JsonProperty("json") final String json,
                                @JsonProperty("tableSettings") TableComponentSettings tableSettings) {
        this.tableId = tableId;
        this.visualisation = visualisation;
        this.json = json;
        this.tableSettings = tableSettings;
    }

    public String getTableId() {
        return tableId;
    }

    public DocRef getVisualisation() {
        return visualisation;
    }

    public String getJson() {
        return json;
    }

    public TableComponentSettings getTableSettings() {
        return tableSettings;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final VisComponentSettings that = (VisComponentSettings) o;
        return Objects.equals(tableId, that.tableId) &&
                Objects.equals(visualisation, that.visualisation) &&
                Objects.equals(json, that.json) &&
                Objects.equals(tableSettings, that.tableSettings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tableId, visualisation, json, tableSettings);
    }

    @Override
    public String toString() {
        return "VisComponentSettings{" +
                "tableId='" + tableId + '\'' +
                ", visualisation=" + visualisation +
                ", json='" + json + '\'' +
                ", tableSettings=" + tableSettings +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private String tableId;
        private DocRef visualisation;
        private String json;
        private TableComponentSettings tableSettings;

        private Builder() {
        }

        private Builder(final VisComponentSettings visComponentSettings) {
            this.tableId = visComponentSettings.tableId;
            this.visualisation = visComponentSettings.visualisation;
            this.json = visComponentSettings.json;
            this.tableSettings = visComponentSettings.tableSettings;
        }

        public Builder tableId(final String tableId) {
            this.tableId = tableId;
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

        public Builder tableSettings(final TableComponentSettings tableSettings) {
            this.tableSettings = tableSettings;
            return this;
        }

        public VisComponentSettings build() {
            return new VisComponentSettings(tableId, visualisation, json, tableSettings);
        }
    }
}
