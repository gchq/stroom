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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"preferredSize", "tabs", "selected"})
@JsonInclude(Include.NON_NULL)
public final class TabLayoutConfig extends LayoutConfig {

    /**
     * The preferred size of this layout in width, height.
     */
    @JsonProperty("preferredSize")
    private final Size preferredSize;
    @JsonProperty("tabs")
    private final List<TabConfig> tabs;
    @JsonProperty("selected")
    private final Integer selected;

    @JsonCreator
    public TabLayoutConfig(@JsonProperty("preferredSize") final Size preferredSize,
                           @JsonProperty("tabs") final List<TabConfig> tabs,
                           @JsonProperty("selected") final Integer selected) {
        this.preferredSize = preferredSize;
        this.tabs = tabs;
        this.selected = selected;
    }

    @Override
    public Size getPreferredSize() {
        return preferredSize;
    }

    public List<TabConfig> getTabs() {
        return tabs;
    }

    public Integer getSelected() {
        return selected;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TabLayoutConfig that = (TabLayoutConfig) o;
        return Objects.equals(preferredSize, that.preferredSize) &&
               Objects.equals(tabs, that.tabs) &&
               Objects.equals(selected, that.selected);
    }

    @Override
    public int hashCode() {
        return Objects.hash(preferredSize, tabs, selected);
    }

    @Override
    public String toString() {
        return "TabLayoutConfig{" +
               "preferredSize=" + preferredSize +
               ", tabs=" + tabs +
               ", selected=" + selected +
               '}';
    }

    @Override
    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractBuilder<TabLayoutConfig, Builder> {

        private List<TabConfig> tabs;
        private Integer selected;

        private Builder() {
        }

        private Builder(final TabLayoutConfig tabLayoutConfig) {
            this.preferredSize = tabLayoutConfig.preferredSize;
            this.tabs = new ArrayList<>(tabLayoutConfig.tabs);
            this.selected = tabLayoutConfig.selected;
        }

        public Builder tabs(final List<TabConfig> tabs) {
            this.tabs = tabs;
            return self();
        }

        public Builder selected(final Integer selected) {
            this.selected = selected;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public TabLayoutConfig build() {
            return new TabLayoutConfig(preferredSize, tabs, selected);
        }
    }
}
