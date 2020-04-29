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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "tab")
@XmlType(name = "tab", propOrder = {"id", "visible"})
@JsonPropertyOrder({"id", "visible", "settings"})
@JsonInclude(Include.NON_NULL)
public class TabConfig {
    @XmlElement(name = "id")
    @JsonProperty("id")
    private String id;

    @XmlElement(name = "visible")
    @JsonProperty("visible")
    private boolean visible;

    @JsonIgnore
    private transient TabLayoutConfig parent;

    public TabConfig() {
        visible = true;
    }

    @JsonCreator
    public TabConfig(@JsonProperty("id") final String id,
                     @JsonProperty("visible") final Boolean visible) {
        this.id = id;
        if (visible != null) {
            this.visible = visible;
        } else {
            this.visible = true;
        }
    }

    public String getId() {
        return id;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(final boolean visible) {
        this.visible = visible;
    }

    @JsonIgnore
    public TabLayoutConfig getParent() {
        return parent;
    }

    @JsonIgnore
    public void setParent(final TabLayoutConfig parent) {
        this.parent = parent;
    }
}
