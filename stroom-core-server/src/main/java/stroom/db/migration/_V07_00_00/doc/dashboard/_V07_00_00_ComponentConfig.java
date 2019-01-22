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

package stroom.db.migration._V07_00_00.doc.dashboard;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.db.migration._V07_00_00.docref._V07_00_00_SharedObject;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@JsonPropertyOrder({"type", "id", "name", "settings"})
@XmlRootElement(name = "component")
@XmlType(name = "ComponentConfig", propOrder = {"type", "id", "name", "settings"})
public class _V07_00_00_ComponentConfig implements _V07_00_00_SharedObject {
    private static final long serialVersionUID = -2530827581046882396L;

    @XmlElement(name = "type")
    private String type;
    @XmlElement(name = "id")
    private String id;
    @XmlElement(name = "name")
    private String name;
    @XmlElements({@XmlElement(name = "query", type = _V07_00_00_QueryComponentSettings.class),
            @XmlElement(name = "table", type = _V07_00_00_TableComponentSettings.class),
            @XmlElement(name = "text", type = _V07_00_00_TextComponentSettings.class),
            @XmlElement(name = "vis", type = _V07_00_00_VisComponentSettings.class)})
    private _V07_00_00_ComponentSettings settings;

    public _V07_00_00_ComponentConfig() {
        // Default constructor necessary for GWT serialisation.
    }

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public _V07_00_00_ComponentSettings getSettings() {
        return settings;
    }

    public void setSettings(final _V07_00_00_ComponentSettings settings) {
        this.settings = settings;
    }
}
