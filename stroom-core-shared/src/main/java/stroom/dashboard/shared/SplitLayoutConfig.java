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

import stroom.util.shared.RandomId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlElements;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

import java.util.ArrayList;
import java.util.List;

@JsonPropertyOrder({"preferredSize", "dimension", "children"})
@JsonInclude(Include.NON_NULL)
@XmlRootElement(name = "splitLayout")
@XmlType(name = "SplitLayoutConfig", propOrder = {"preferredSize", "dimension", "children"})
public class SplitLayoutConfig extends LayoutConfig {

    @JsonIgnore
    private final String id;
    /**
     * The preferred size of this layout in width, height.
     */
    @XmlElement(name = "preferredSize")
    @JsonProperty("preferredSize")
    private Size preferredSize;
    @XmlElement(name = "dimension")
    @JsonProperty("dimension")
    private final int dimension;
    @XmlElementWrapper(name = "children")
    @XmlElements({
            @XmlElement(name = "splitLayout", type = SplitLayoutConfig.class),
            @XmlElement(name = "tabLayout", type = TabLayoutConfig.class)})
    @JsonProperty("children")
    private List<LayoutConfig> children;

    public SplitLayoutConfig(final int dimension) {
        this(new Size(), dimension, null);
    }

    public SplitLayoutConfig(final Size preferredSize, final int dimension) {
        this(preferredSize, dimension, null);
    }

    @JsonCreator
    public SplitLayoutConfig(@JsonProperty("preferredSize") final Size preferredSize,
                             @JsonProperty("dimension") final int dimension,
                             @JsonProperty("children") final List<LayoutConfig> children) {
        id = "SplitLayoutConfig_" + RandomId.createId(10);
        this.preferredSize = preferredSize;
        this.dimension = dimension;
        this.children = children;
    }

    @Override
    public Size getPreferredSize() {
        return preferredSize;
    }

    public void setPreferredSize(final Size preferredSize) {
        this.preferredSize = preferredSize;
    }

    public int getDimension() {
        return dimension;
    }

    public List<LayoutConfig> getChildren() {
        return children;
    }

    public LayoutConfig get(final int index) {
        if (children != null) {
            final LayoutConfig child = children.get(index);
            if (child != null) {
                child.setParent(this);
                return child;
            }
        }
        return null;
    }

    public void add(final LayoutConfig child) {
        if (children == null) {
            children = new ArrayList<>();
        }
        children.add(child);
        child.setParent(this);
    }

    public void add(final int index, final LayoutConfig child) {
        if (children == null) {
            children = new ArrayList<>();
        }
        if (index <= children.size()) {
            children.add(index, child);
        } else {
            children.add(child);
        }
        child.setParent(this);
    }

    public void remove(final LayoutConfig child) {
        if (children != null) {
            children.remove(child);
            child.setParent(null);
        }
    }

    public int indexOf(final LayoutConfig child) {
        if (children != null) {
            return children.indexOf(child);
        }
        return -1;
    }

    public int count() {
        if (children == null) {
            return 0;
        }
        return children.size();
    }

    @Override
    public String toString() {
        return id;
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
            this.preferredSize = splitLayoutConfig.preferredSize;
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
