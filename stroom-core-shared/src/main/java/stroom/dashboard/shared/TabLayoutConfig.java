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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
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
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "tabLayout")
@XmlType(name = "TabLayoutConfig", propOrder = {"preferredSize", "tabs", "selected"})
@JsonPropertyOrder({"preferredSize", "tabs", "selected"})
@JsonInclude(Include.NON_DEFAULT)
public class TabLayoutConfig extends LayoutConfig {
    /**
     * The preferred size of this layout in width, height.
     */
    @XmlElement(name = "preferredSize")
    @JsonProperty("preferredSize")
    private Size preferredSize;
    @XmlElementWrapper(name = "tabs")
    @XmlElements({@XmlElement(name = "tab", type = TabConfig.class)})
    @JsonProperty("tabs")
    private List<TabConfig> tabs;
    @XmlElement(name = "selected")
    @JsonProperty("selected")
    private Integer selected;

    public TabLayoutConfig() {
        preferredSize = new Size();
    }

    public TabLayoutConfig(final TabConfig... tabs) {
        preferredSize = new Size();

        if (tabs != null) {
            for (final TabConfig tab : tabs) {
                add(tab);
            }
        }
    }

    @JsonCreator
    public TabLayoutConfig(@JsonProperty("preferredSize")final Size preferredSize,
                           @JsonProperty("tabs")final List<TabConfig> tabs,
                           @JsonProperty("selected")final Integer selected) {
        if (preferredSize != null) {
            this.preferredSize = preferredSize;
        } else {
            this.preferredSize = new Size();
        }
        this.tabs = tabs;
        this.selected = selected;
    }

    @Override
    public Size getPreferredSize() {
        return preferredSize;
    }

    @Override
    public void setPreferredSize(final Size preferredSize) {
        this.preferredSize = preferredSize;
    }

    private List<TabConfig> getVisibleTabs() {
        if (tabs == null) {
            return Collections.emptyList();
        }
        return tabs.stream().filter(TabConfig::isVisible).collect(Collectors.toList());
    }

    public TabConfig get(final int index) {
        if (tabs != null && tabs.size() > 0) {
            final TabConfig tab = tabs.get(index);
            if (tab != null) {
                tab.setParent(this);
                return tab;
            }
        }
        return null;
    }

    public void add(final TabConfig tab) {
        if (tabs == null) {
            tabs = new ArrayList<>();
        }
        tabs.add(tab);
        tab.setParent(this);
    }

    public void add(final int index, final TabConfig tab) {
        if (tabs == null) {
            tabs = new ArrayList<>();
        }
        if (index >= tabs.size()) {
            tabs.add(tab);
        } else {
            tabs.add(index, tab);
        }
        tab.setParent(this);
    }

    public void remove(final TabConfig tab) {
        if (tabs != null) {
            tabs.remove(tab);
            tab.setParent(null);
        }
    }

    public int indexOf(final TabConfig tab) {
        return tabs.indexOf(tab);
    }

    @JsonIgnore
    public int getVisibleTabCount() {
        return getVisibleTabs().size();
    }

    @JsonIgnore
    public int getAllTabCount() {
        if (tabs == null) {
            return 0;
        }
        return tabs.size();
    }

    public List<TabConfig> getTabs() {
        if (tabs == null) {
            tabs = new ArrayList<>();
        }
        for (final TabConfig tabConfig : tabs) {
            tabConfig.setParent(this);
        }
        return tabs;
    }

    public void setTabs(final List<TabConfig> tabs) {
        this.tabs = tabs;
    }

    public Integer getSelected() {
        return selected;
    }

    public void setSelected(final Integer selected) {
        this.selected = selected;
    }
}
