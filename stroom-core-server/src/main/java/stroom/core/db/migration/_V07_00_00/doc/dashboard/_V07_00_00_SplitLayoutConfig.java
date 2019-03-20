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

package stroom.core.db.migration._V07_00_00.doc.dashboard;

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
public class _V07_00_00_SplitLayoutConfig extends _V07_00_00_LayoutConfig {
    private static final long serialVersionUID = 8201392610412513780L;

    /**
     * The preferred size of this layout in width, height.
     */
    @XmlElement(name = "preferredSize")
    private _V07_00_00_Size preferredSize = new _V07_00_00_Size();
    @XmlElement(name = "dimension")
    private int dimension;
    @XmlElementWrapper(name = "children")
    @XmlElements({@XmlElement(name = "splitLayout", type = _V07_00_00_SplitLayoutConfig.class),
            @XmlElement(name = "tabLayout", type = _V07_00_00_TabLayoutConfig.class)})
    private List<_V07_00_00_LayoutConfig> children;

    public _V07_00_00_SplitLayoutConfig() {
        // Default constructor necessary for GWT serialisation.
    }

    public _V07_00_00_SplitLayoutConfig(final int dimension, final _V07_00_00_LayoutConfig... children) {
        this.dimension = dimension;
        if (children != null) {
            for (final _V07_00_00_LayoutConfig child : children) {
                add(child);
            }
        }
    }

    @Override
    public _V07_00_00_Size getPreferredSize() {
        return preferredSize;
    }

    @Override
    public void setPreferredSize(final _V07_00_00_Size preferredSize) {
        this.preferredSize = preferredSize;
    }

    public int getDimension() {
        return dimension;
    }

    public void setDimension(final int dimension) {
        this.dimension = dimension;
    }

    public _V07_00_00_LayoutConfig get(final int index) {
        if (children != null) {
            final _V07_00_00_LayoutConfig child = children.get(index);
            if (child != null) {
                child.setParent(this);
                return child;
            }
        }
        return null;
    }

    public void add(final _V07_00_00_LayoutConfig child) {
        if (children == null) {
            children = new ArrayList<>();
        }
        children.add(child);
        child.setParent(this);
    }

    public void add(final int index, final _V07_00_00_LayoutConfig child) {
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

    public void remove(final _V07_00_00_LayoutConfig child) {
        if (children != null) {
            children.remove(child);
            child.setParent(null);
        }
    }

    public int indexOf(final _V07_00_00_LayoutConfig child) {
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

    public enum _V07_00_00_Direction {
        ACROSS(0), DOWN(1);

        private final int dimension;

        _V07_00_00_Direction(final int dimension) {
            this.dimension = dimension;
        }

        public int getDimension() {
            return dimension;
        }
    }
}
