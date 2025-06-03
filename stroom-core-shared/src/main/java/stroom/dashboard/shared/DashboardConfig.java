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

import stroom.query.api.TimeRange;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlElements;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
@JsonPropertyOrder({
        "parameters",
        "timeRange",
        "components",
        "layout",
        "layoutConstraints",
        "preferredSize",
        "designMode",
        "modelVersion"
})
@JsonInclude(Include.NON_NULL)
@XmlRootElement(name = "dashboard")
@XmlType(name = "DashboardConfig", propOrder = {
        "parameters",
        "timeRange",
        "components",
        "layout",
        "layoutConstraints",
        "preferredSize",
        "designMode",
        "modelVersion"
})
public class DashboardConfig {

    @XmlElement(name = "parameters")
    @JsonProperty("parameters")
    private String parameters;
    @XmlElement(name = "timeRange")
    @JsonProperty("timeRange")
    private TimeRange timeRange;
    @XmlElementWrapper(name = "components")
    @XmlElements({@XmlElement(name = "component", type = ComponentConfig.class)})
    @JsonProperty("components")
    private List<ComponentConfig> components;
    @XmlElements({
            @XmlElement(name = "splitLayout", type = SplitLayoutConfig.class),
            @XmlElement(name = "tabLayout", type = TabLayoutConfig.class)})
    @JsonProperty("layout")
    private LayoutConfig layout;
    @JsonProperty("layoutConstraints")
    private LayoutConstraints layoutConstraints;
    @JsonProperty("preferredSize")
    private Size preferredSize;
    @JsonProperty("designMode")
    private Boolean designMode;
    @JsonProperty("modelVersion")
    private String modelVersion;

    public DashboardConfig() {
    }

    @JsonCreator
    public DashboardConfig(@JsonProperty("parameters") final String parameters,
                           @JsonProperty("timeRange") final TimeRange timeRange,
                           @JsonProperty("components") final List<ComponentConfig> components,
                           @JsonProperty("layout") final LayoutConfig layout,
                           @JsonProperty("layoutConstraints") LayoutConstraints layoutConstraints,
                           @JsonProperty("preferredSize") Size preferredSize,
                           @JsonProperty("designMode") final Boolean designMode,
                           @JsonProperty("modelVersion") final String modelVersion) {
        this.parameters = parameters;
        this.timeRange = timeRange;
        this.components = components;
        this.layout = layout;
        this.layoutConstraints = layoutConstraints;
        this.preferredSize = preferredSize;
        this.designMode = designMode;
        this.modelVersion = modelVersion;
    }

    @Deprecated
    public String getParameters() {
        return parameters;
    }

    @Deprecated
    public void setParameters(String parameters) {
        this.parameters = parameters;
    }

    public TimeRange getTimeRange() {
        return timeRange;
    }

    public void setTimeRange(final TimeRange timeRange) {
        this.timeRange = timeRange;
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

    public LayoutConstraints getLayoutConstraints() {
        return layoutConstraints;
    }

    public void setLayoutConstraints(final LayoutConstraints layoutConstraints) {
        this.layoutConstraints = layoutConstraints;
    }

    public Size getPreferredSize() {
        return preferredSize;
    }

    public void setPreferredSize(final Size preferredSize) {
        this.preferredSize = preferredSize;
    }

    public Boolean getDesignMode() {
        return designMode;
    }

    public void setDesignMode(final Boolean designMode) {
        this.designMode = designMode;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(final String modelVersion) {
        this.modelVersion = modelVersion;
    }
}
