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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.docref.SharedObject;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@JsonPropertyOrder({"type", "id", "name", "settings"})
@JsonInclude(Include.NON_EMPTY)
@XmlRootElement(name = "component")
@XmlType(name = "ComponentConfig", propOrder = {"type", "id", "name", "settings"})
public class ComponentConfig implements SharedObject {
    private static final long serialVersionUID = -2530827581046882396L;

    @XmlElement(name = "type")
    @JsonProperty("type")
    private String type;
    @XmlElement(name = "id")
    @JsonProperty("id")
    private String id;
    @XmlElement(name = "name")
    @JsonProperty("name")
    private String name;
    @XmlElements({@XmlElement(name = "query", type = QueryComponentSettings.class),
            @XmlElement(name = "table", type = TableComponentSettings.class),
            @XmlElement(name = "text", type = TextComponentSettings.class),
            @XmlElement(name = "vis", type = VisComponentSettings.class)})
    @JsonProperty("settings")
    private ComponentSettings settings;

    public ComponentConfig() {
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

    public ComponentSettings getSettings() {
        return settings;
    }

    public void setSettings(final ComponentSettings settings) {
        this.settings = settings;
    }
}
