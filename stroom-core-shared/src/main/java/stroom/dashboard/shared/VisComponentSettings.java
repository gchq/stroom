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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.docref.DocRef;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@JsonPropertyOrder({"tableId", "visualisation", "json"})
@JsonInclude(Include.NON_DEFAULT)
@XmlRootElement(name = "vis")
@XmlType(name = "VisComponentSettings", propOrder = {"tableId", "visualisation", "json"})
public class VisComponentSettings extends ComponentSettings {
    @XmlElement(name = "tableId")
    @JsonProperty("tableId")
    private String tableId;
    @XmlElement(name = "visualisation")
    @JsonProperty("visualisation")
    private DocRef visualisation;
    @XmlElement(name = "json")
    @JsonProperty("json")
    private String json;
    @XmlTransient
    @JsonIgnore
    private TableComponentSettings tableSettings;

    public VisComponentSettings() {
    }

    @JsonCreator
    public VisComponentSettings(@JsonProperty("tableId") final String tableId,
                                @JsonProperty("visualisation") final DocRef visualisation,
                                @JsonProperty("json") final String json) {
        this.tableId = tableId;
        this.visualisation = visualisation;
        this.json = json;
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
