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

import stroom.util.shared.SharedObject;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "tab", propOrder = { "id", "settings" })
public class TabConfig implements SharedObject {
    private static final long serialVersionUID = -2105048053435792675L;

    @XmlElement(name = "id")
    private String id;

    @XmlElements({ @XmlElement(name = "query", type = QueryComponentSettings.class),
            @XmlElement(name = "table", type = TableComponentSettings.class),
            @XmlElement(name = "vis", type = VisComponentSettings.class),
            @XmlElement(name = "text", type = TextComponentSettings.class)})
    private ComponentSettings settings;

    private transient TabLayoutConfig parent;

    public TabConfig() {
        // Default constructor necessary for GWT serialisation.
    }

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public ComponentSettings getSettings() {
        return settings;
    }

    public void setSettings(final ComponentSettings settings) {
        this.settings = settings;
    }

    public TabLayoutConfig getParent() {
        return parent;
    }

    public void setParent(final TabLayoutConfig parent) {
        this.parent = parent;
    }
}
