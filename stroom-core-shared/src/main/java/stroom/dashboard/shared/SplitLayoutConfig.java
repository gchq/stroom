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

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"preferredSize", "dimension", "children"})
@JsonInclude(Include.NON_NULL)
public final class SplitLayoutConfig extends LayoutConfig {

    /**
     * The preferred size of this layout in width, height.
     */
    @JsonProperty("preferredSize")
    private final Size preferredSize;
    @JsonProperty("dimension")
    private final int dimension;
    @JsonProperty("children")
    private final List<LayoutConfig> children;

    public SplitLayoutConfig(final int dimension) {
        this(new Size(0, 0), dimension, null);
    }

    @JsonCreator
    public SplitLayoutConfig(@JsonProperty("preferredSize") final Size preferredSize,
                             @JsonProperty("dimension") final int dimension,
                             @JsonProperty("children") final List<LayoutConfig> children) {
        this.preferredSize = preferredSize;
        this.dimension = dimension;
        this.children = children;
    }

    @Override
    public Size getPreferredSize() {
        return preferredSize;
    }

    public int getDimension() {
        return dimension;
    }

    public List<LayoutConfig> getChildren() {
        return children;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SplitLayoutConfig that = (SplitLayoutConfig) o;
        return dimension == that.dimension &&
               Objects.equals(preferredSize, that.preferredSize) &&
               Objects.equals(children, that.children);
    }

    @Override
    public int hashCode() {
        return Objects.hash(preferredSize, dimension, children);
    }

    @Override
    public String toString() {
        return "SplitLayoutConfig{" +
               "preferredSize=" + preferredSize +
               ", dimension=" + dimension +
               ", children=" + children +
               '}';
    }

    @Override
    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractBuilder<SplitLayoutConfig, Builder> {

        private int dimension;
        private List<LayoutConfig> children;

        private Builder() {
        }

        private Builder(final SplitLayoutConfig splitLayoutConfig) {
            super(splitLayoutConfig);
            this.dimension = splitLayoutConfig.dimension;
            this.children = splitLayoutConfig.children;
        }

        public Builder dimension(final int dimension) {
            this.dimension = dimension;
            return self();
        }

        public Builder children(final List<LayoutConfig> children) {
            this.children = children;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public SplitLayoutConfig build() {
            return new SplitLayoutConfig(preferredSize, dimension, children);
        }
    }
}
