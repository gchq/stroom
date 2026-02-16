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
import java.util.Objects;

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
    private final String parameters;
    @JsonProperty("timeRange")
    private final TimeRange timeRange;
    @JsonProperty("components")
    private final List<ComponentConfig> components;
    @JsonProperty("layout")
    private final LayoutConfig layout;
    @JsonProperty("layoutConstraints")
    private final LayoutConstraints layoutConstraints;
    @JsonProperty("preferredSize")
    private final Size preferredSize;
    @JsonProperty("designMode")
    private final Boolean designMode;
    @JsonProperty("modelVersion")
    private final String modelVersion;

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

    public TimeRange getTimeRange() {
        return timeRange;
    }

    public List<ComponentConfig> getComponents() {
        return components;
    }

    public LayoutConfig getLayout() {
        return layout;
    }

    public LayoutConstraints getLayoutConstraints() {
        return layoutConstraints;
    }

    public Size getPreferredSize() {
        return preferredSize;
    }

    public Boolean getDesignMode() {
        return designMode;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DashboardConfig that = (DashboardConfig) o;

//        // TODO : REMOVE - GWT DEBUG
//        final boolean b1 =  Objects.equals(parameters, that.parameters);
//        final boolean b2 = Objects.equals(timeRange, that.timeRange);
//        final boolean b3 = Objects.equals(components, that.components);
//        final boolean b4 = Objects.equals(layout, that.layout);
//        final boolean b5 = Objects.equals(layoutConstraints, that.layoutConstraints);
//        final boolean b6 = Objects.equals(preferredSize, that.preferredSize);
//        final boolean b7 = Objects.equals(designMode, that.designMode);
//        final boolean b8 = Objects.equals(modelVersion, that.modelVersion);

        return Objects.equals(parameters, that.parameters) &&
               Objects.equals(timeRange, that.timeRange) &&
               Objects.equals(components, that.components) &&
               Objects.equals(layout, that.layout) &&
               Objects.equals(layoutConstraints, that.layoutConstraints) &&
               Objects.equals(preferredSize, that.preferredSize) &&
               Objects.equals(designMode, that.designMode) &&
               Objects.equals(modelVersion, that.modelVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameters,
                timeRange,
                components,
                layout,
                layoutConstraints,
                preferredSize,
                designMode,
                modelVersion);
    }

    @Override
    public String toString() {
        return "DashboardConfig{" +
               "parameters='" + parameters + '\'' +
               ", timeRange=" + timeRange +
               ", components=" + components +
               ", layout=" + layout +
               ", layoutConstraints=" + layoutConstraints +
               ", preferredSize=" + preferredSize +
               ", designMode=" + designMode +
               ", modelVersion='" + modelVersion + '\'' +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private String parameters;
        private TimeRange timeRange;
        private List<ComponentConfig> components;
        private LayoutConfig layout;
        private LayoutConstraints layoutConstraints;
        private Size preferredSize;
        private Boolean designMode;
        private String modelVersion;

        private Builder() {
        }

        private Builder(final DashboardConfig dashboardConfig) {
            this.parameters = dashboardConfig.parameters;
            this.timeRange = dashboardConfig.timeRange;
            this.components = dashboardConfig.components;
            this.layout = dashboardConfig.layout;
            this.layoutConstraints = dashboardConfig.layoutConstraints;
            this.preferredSize = dashboardConfig.preferredSize;
            this.designMode = dashboardConfig.designMode;
            this.modelVersion = dashboardConfig.modelVersion;
        }

        public Builder parameters(final String parameters) {
            this.parameters = parameters;
            return this;
        }

        public Builder timeRange(final TimeRange timeRange) {
            this.timeRange = timeRange;
            return this;
        }

        public Builder components(final List<ComponentConfig> components) {
            this.components = components;
            return this;
        }

        public Builder layout(final LayoutConfig layout) {
            this.layout = layout;
            return this;
        }

        public Builder layoutConstraints(final LayoutConstraints layoutConstraints) {
            this.layoutConstraints = layoutConstraints;
            return this;
        }

        public Builder preferredSize(final Size preferredSize) {
            this.preferredSize = preferredSize;
            return this;
        }

        public Builder designMode(final Boolean designMode) {
            this.designMode = designMode;
            return this;
        }

        public Builder modelVersion(final String modelVersion) {
            this.modelVersion = modelVersion;
            return this;
        }

        public DashboardConfig build() {
            return new DashboardConfig(
                    parameters,
                    timeRange,
                    components,
                    layout,
                    layoutConstraints,
                    preferredSize,
                    designMode,
                    modelVersion);
        }
    }
}
