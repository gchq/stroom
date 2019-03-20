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

package stroom.core.db.migration._V07_00_00.doc.dashboard;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.core.db.migration._V07_00_00.docref._V07_00_00_SharedObject;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@JsonPropertyOrder({"id", "settings"})
@XmlRootElement(name = "tab")
@XmlType(name = "TabConfig", propOrder = {"id", "settings"})
public class _V07_00_00_TabConfig implements _V07_00_00_SharedObject {
    private static final long serialVersionUID = -2105048053435792675L;

    @XmlElement(name = "id")
    private String id;

    @XmlElements({@XmlElement(name = "query", type = _V07_00_00_QueryComponentSettings.class),
            @XmlElement(name = "table", type = _V07_00_00_TableComponentSettings.class),
            @XmlElement(name = "vis", type = _V07_00_00_VisComponentSettings.class),
            @XmlElement(name = "text", type = _V07_00_00_TextComponentSettings.class)})
    private _V07_00_00_ComponentSettings settings;

    private transient _V07_00_00_TabLayoutConfig parent;

    public _V07_00_00_TabConfig() {
        // Default constructor necessary for GWT serialisation.
    }

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public _V07_00_00_ComponentSettings getSettings() {
        return settings;
    }

    public void setSettings(final _V07_00_00_ComponentSettings settings) {
        this.settings = settings;
    }

    public _V07_00_00_TabLayoutConfig getParent() {
        return parent;
    }

    public void setParent(final _V07_00_00_TabLayoutConfig parent) {
        this.parent = parent;
    }
}
