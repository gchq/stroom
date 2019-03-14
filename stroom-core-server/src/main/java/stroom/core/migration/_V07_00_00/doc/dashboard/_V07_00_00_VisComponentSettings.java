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

package stroom.core.migration._V07_00_00.doc.dashboard;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.core.migration._V07_00_00.docref._V07_00_00_DocRef;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@JsonPropertyOrder({"tableId", "visualisation", "json"})
@XmlRootElement(name = "vis")
@XmlType(name = "VisComponentSettings", propOrder = {"tableId", "visualisation", "json"})
public class _V07_00_00_VisComponentSettings extends _V07_00_00_ComponentSettings {
    private static final long serialVersionUID = -2530827581046882396L;

    @XmlElement(name = "tableId")
    private String tableId;
    @XmlElement(name = "visualisation")
    private _V07_00_00_DocRef visualisation;
    @XmlElement(name = "json")
    private String json;
    @XmlTransient
    private _V07_00_00_TableComponentSettings tableSettings;

    public _V07_00_00_VisComponentSettings() {
        // Default constructor necessary for GWT serialisation.
    }

    public String getTableId() {
        return tableId;
    }

    public void setTableId(final String tableId) {
        this.tableId = tableId;
    }

    public _V07_00_00_DocRef getVisualisation() {
        return visualisation;
    }

    public void setVisualisation(final _V07_00_00_DocRef visualisation) {
        this.visualisation = visualisation;
    }

    public String getJSON() {
        return json;
    }

    public void setJSON(final String json) {
        this.json = json;
    }

    public _V07_00_00_TableComponentSettings getTableSettings() {
        return tableSettings;
    }

    public void setTableSettings(final _V07_00_00_TableComponentSettings tableSettings) {
        this.tableSettings = tableSettings;
    }
}
