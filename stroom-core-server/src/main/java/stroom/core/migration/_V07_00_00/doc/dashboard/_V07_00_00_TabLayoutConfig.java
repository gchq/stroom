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

package stroom.core.migration._V07_00_00.doc.dashboard;

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
@JsonPropertyOrder({"preferredSize", "tabs", "selected"})
@XmlRootElement(name = "tabLayout")
@XmlType(name = "TabLayoutConfig", propOrder = {"preferredSize", "tabs", "selected"})
public class _V07_00_00_TabLayoutConfig extends _V07_00_00_LayoutConfig {
    private static final long serialVersionUID = -2105048053435792675L;

    /**
     * The preferred size of this layout in width, height.
     */
    @XmlElement(name = "preferredSize")
    private _V07_00_00_Size preferredSize = new _V07_00_00_Size();
    @XmlElementWrapper(name = "tabs")
    @XmlElements({@XmlElement(name = "tab", type = _V07_00_00_TabConfig.class)})
    private List<_V07_00_00_TabConfig> tabs;
    @XmlElement(name = "selected")
    private Integer selected;

    public _V07_00_00_TabLayoutConfig() {
        // Default constructor necessary for GWT serialisation.
    }

    public _V07_00_00_TabLayoutConfig(final _V07_00_00_TabConfig... tabs) {
        if (tabs != null) {
            for (final _V07_00_00_TabConfig tab : tabs) {
                add(tab);
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

    public _V07_00_00_TabConfig get(final int index) {
        if (tabs != null) {
            final _V07_00_00_TabConfig tab = tabs.get(index);
            if (tab != null) {
                tab.setParent(this);
                return tab;
            }
        }
        return null;
    }

    public void add(final _V07_00_00_TabConfig tab) {
        if (tabs == null) {
            tabs = new ArrayList<>();
        }
        tabs.add(tab);
        tab.setParent(this);
    }

    public void add(final int index, final _V07_00_00_TabConfig tab) {
        if (tabs == null) {
            tabs = new ArrayList<>();
        }
        if (index <= tabs.size()) {
            tabs.add(index, tab);
        } else {
            tabs.add(tab);
        }
        tab.setParent(this);
    }

    public void remove(final _V07_00_00_TabConfig tab) {
        if (tabs != null) {
            tabs.remove(tab);
            tab.setParent(null);
        }
    }

    public int indexOf(final _V07_00_00_TabConfig tab) {
        if (tabs == null) {
            return -1;
        }
        return tabs.indexOf(tab);
    }

    public int count() {
        if (tabs == null) {
            return 0;
        }
        return tabs.size();
    }

    public Integer getSelected() {
        return selected;
    }

    public void setSelected(final Integer selected) {
        this.selected = selected;
    }
}
