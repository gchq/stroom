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
import jakarta.xml.bind.annotation.XmlType;

import java.util.ArrayList;
import java.util.List;

@XmlType(name = "splitLayout", propOrder = {"dimension", "children"})
@Deprecated
public class SplitLayoutConfig extends LayoutConfig {

    private static final long serialVersionUID = 8201392610412513780L;
    @XmlElement(name = "dimension")
    private int dimension;
    @XmlElementWrapper(name = "children")
    @XmlElements({
            @XmlElement(name = "splitLayout", type = SplitLayoutConfig.class),
            @XmlElement(name = "tabLayout", type = TabLayoutConfig.class)})
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

    public List<LayoutConfig> getChildren() {
        return children;
    }

    public enum Direction {
        ACROSS(0),
        DOWN(1);

        private final int dimension;

        Direction(final int dimension) {
            this.dimension = dimension;
        }

        public int getDimension() {
            return dimension;
        }
    }
}
