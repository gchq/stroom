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

package stroom.legacy.model_6_1;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlElements;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

import java.util.List;

@XmlType(name = "dashboard", propOrder = {"parameters", "components", "layout", "tabVisibility"})
@XmlRootElement(name = "dashboard")
@Deprecated
public class DashboardConfig implements SharedObject {

    private static final long serialVersionUID = -2530827581046882396L;
    @XmlElement(name = "parameters")
    private String parameters;
    @XmlElementWrapper(name = "components")
    @XmlElements({@XmlElement(name = "component", type = ComponentConfig.class)})
    private List<ComponentConfig> components;
    @XmlElements({
            @XmlElement(name = "splitLayout", type = SplitLayoutConfig.class),
            @XmlElement(name = "tabLayout", type = TabLayoutConfig.class)})
    private LayoutConfig layout;
    @XmlElement(name = "tabVisibility")
    private TabVisibility tabVisibility = TabVisibility.SHOW_ALL;

    public String getParameters() {
        return parameters;
    }

    public void setParameters(String parameters) {
        this.parameters = parameters;
    }

    public List<ComponentConfig> getComponents() {
        return components;
    }

    public void setComponents(final List<ComponentConfig> components) {
        this.components = components;
    }

    public LayoutConfig getLayout() {
        return layout;
    }

    public void setLayout(final LayoutConfig layout) {
        this.layout = layout;
    }

    public TabVisibility getTabVisibility() {
        return tabVisibility;
    }

    public void setTabVisibility(final TabVisibility tabVisibility) {
        this.tabVisibility = tabVisibility;
    }

    public enum TabVisibility implements HasDisplayValue {
        SHOW_ALL("Show All"),
        HIDE_SINGLE("Hide Single Tabs"),
        HIDE_ALL("Hide All");

        private final String displayValue;

        TabVisibility(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }
}
