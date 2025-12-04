/*
 * Copyright 2016-2025 Crown Copyright
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

import java.util.List;

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
public class DashboardConfig {

    @JsonProperty("parameters")
    private String parameters;
    @JsonProperty("timeRange")
    private TimeRange timeRange;
    @JsonProperty("components")
    private List<ComponentConfig> components;
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
                           @JsonProperty("layoutConstraints") final LayoutConstraints layoutConstraints,
                           @JsonProperty("preferredSize") final Size preferredSize,
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
    public void setParameters(final String parameters) {
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
