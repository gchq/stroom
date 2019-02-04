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

package stroom.db.migration._V07_00_00.doc.dashboard;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.db.migration._V07_00_00.docref._V07_00_00_HasDisplayValue;
import stroom.db.migration._V07_00_00.docref._V07_00_00_SharedObject;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
@JsonPropertyOrder({"parameters", "components", "layout", "tabVisibility"})
@XmlRootElement(name = "dashboard")
@XmlType(name = "DashboardConfig", propOrder = {"parameters", "components", "layout", "tabVisibility"})
public class _V07_00_00_DashboardConfig implements _V07_00_00_SharedObject {
    private static final long serialVersionUID = -2530827581046882396L;

    @XmlElement(name = "parameters")
    private String parameters;
    @XmlElementWrapper(name = "components")
    @XmlElements({@XmlElement(name = "component", type = _V07_00_00_ComponentConfig.class)})
    private List<_V07_00_00_ComponentConfig> components;
    @XmlElements({@XmlElement(name = "splitLayout", type = _V07_00_00_SplitLayoutConfig.class),
            @XmlElement(name = "tabLayout", type = _V07_00_00_TabLayoutConfig.class)})
    private _V07_00_00_LayoutConfig layout;
    @XmlElement(name = "tabVisibility")
    private _V07_00_00_TabVisibility tabVisibility = _V07_00_00_TabVisibility.SHOW_ALL;

    public String getParameters() {
        return parameters;
    }

    public void setParameters(String parameters) {
        this.parameters = parameters;
    }

    public List<_V07_00_00_ComponentConfig> getComponents() {
        return components;
    }

    public void setComponents(final List<_V07_00_00_ComponentConfig> components) {
        this.components = components;
    }

    public _V07_00_00_LayoutConfig getLayout() {
        return layout;
    }

    public void setLayout(final _V07_00_00_LayoutConfig layout) {
        this.layout = layout;
    }

    public _V07_00_00_TabVisibility getTabVisibility() {
        return tabVisibility;
    }

    public void setTabVisibility(final _V07_00_00_TabVisibility tabVisibility) {
        this.tabVisibility = tabVisibility;
    }

    public enum _V07_00_00_TabVisibility implements _V07_00_00_HasDisplayValue {
        SHOW_ALL("Show All"), HIDE_SINGLE("Hide Single Tabs"), HIDE_ALL("Hide All");

        private final String displayValue;

        _V07_00_00_TabVisibility(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }
}
