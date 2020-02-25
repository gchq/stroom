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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.docref.DocRef;

@JsonPropertyOrder({"tableId", "visualisation", "json", "tableSettings"})
@JsonInclude(Include.NON_DEFAULT)
public class VisComponentSettings extends ComponentSettings {
    @JsonProperty("tableId")
    private String tableId;
    @JsonProperty("visualisation")
    private DocRef visualisation;
    @JsonProperty("json")
    private String json;
    @JsonProperty
    private TableComponentSettings tableSettings;

    public VisComponentSettings() {
    }

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

    public void setTableId(final String tableId) {
        this.tableId = tableId;
    }

    public DocRef getVisualisation() {
        return visualisation;
    }

    public void setVisualisation(final DocRef visualisation) {
        this.visualisation = visualisation;
    }

    public String getJson() {
        return json;
    }

    public void setJson(final String json) {
        this.json = json;
    }

    public TableComponentSettings getTableSettings() {
        return tableSettings;
    }

    public void setTableSettings(final TableComponentSettings tableSettings) {
        this.tableSettings = tableSettings;
    }
}
