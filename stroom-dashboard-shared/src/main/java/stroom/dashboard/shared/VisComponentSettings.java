/*
 * Copyright 2016 Crown Copyright
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

import stroom.query.api.DocRef;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "vis", propOrder = { "tableId", "visualisation", "json" })
public class VisComponentSettings extends ComponentSettings {
    private static final long serialVersionUID = -2530827581046882396L;

    @XmlElement(name = "tableId")
    private String tableId;
    @XmlElement(name = "visualisation")
    private DocRef visualisation;
    @XmlElement(name = "json")
    private String json;
    @XmlTransient
    private TableComponentSettings tableSettings;

    public VisComponentSettings() {
        // Default constructor necessary for GWT serialisation.
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

    public String getJSON() {
        return json;
    }

    public void setJSON(final String json) {
        this.json = json;
    }

    public TableComponentSettings getTableSettings() {
        return tableSettings;
    }

    public void setTableSettings(final TableComponentSettings tableSettings) {
        this.tableSettings = tableSettings;
    }
}
