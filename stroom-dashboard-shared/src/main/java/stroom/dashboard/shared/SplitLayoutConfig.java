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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
@JsonPropertyOrder({"preferredSize", "dimension", "children"})
@XmlRootElement(name = "splitLayout")
@XmlType(name = "SplitLayoutConfig", propOrder = {"preferredSize", "dimension", "children"})
public class SplitLayoutConfig extends LayoutConfig {
    private static final long serialVersionUID = 8201392610412513780L;

    /**
     * The preferred size of this layout in width, height.
     */
    @XmlElement(name = "preferredSize")
    @JsonProperty("preferredSize")
    private Size preferredSize = new Size();
    @XmlElement(name = "dimension")
    @JsonProperty("dimension")
    private int dimension;
    @XmlElementWrapper(name = "children")
    @XmlElements({@XmlElement(name = "splitLayout", type = SplitLayoutConfig.class),
            @XmlElement(name = "tabLayout", type = TabLayoutConfig.class)})
    @JsonProperty("children")
    private List<LayoutConfig> children;

    public SplitLayoutConfig() {
        // Default constructor necessary for GWT serialisation.
    }

    public SplitLayoutConfig(final int dimension, final LayoutConfig... children) {
        this.dimension = dimension;
        if (children != null) {
            for (final LayoutConfig child : children) {
                add(child);
            }
        }
    }

    @Override
    public Size getPreferredSize() {
        return preferredSize;
    }

    @Override
    public void setPreferredSize(final Size preferredSize) {
        this.preferredSize = preferredSize;
    }

    public int getDimension() {
        return dimension;
    }

    public void setDimension(final int dimension) {
        this.dimension = dimension;
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

    public enum Direction {
        ACROSS(0), DOWN(1);

        private final int dimension;

        Direction(final int dimension) {
            this.dimension = dimension;
        }

        public int getDimension() {
            return dimension;
        }
    }
}
