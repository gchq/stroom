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

package stroom.dashboard.client.flexlayout;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class MutableTabLayoutConfig extends MutableLayoutConfig {

    private MutableSize preferredSize;
    private final List<MutableTabConfig> tabs = new ArrayList<>();
    private Integer selected;

    public MutableTabLayoutConfig() {
        this(new MutableSize(), null);
    }

    public MutableTabLayoutConfig(final MutableSize preferredSize,
                                  final Integer selected) {
        this.preferredSize = preferredSize;
        this.selected = selected;
    }

    @Override
    public MutableSize getPreferredSize() {
        return preferredSize;
    }

    public void setPreferredSize(final MutableSize preferredSize) {
        this.preferredSize = preferredSize;
    }

    private List<MutableTabConfig> getVisibleTabs() {
        return tabs.stream().filter(MutableTabConfig::isVisible).collect(Collectors.toList());
    }

    public MutableTabConfig get(final int index) {
        return tabs.get(index);
    }

    public void add(final MutableTabConfig tab) {
        tabs.add(tab);
        tab.setParent(this);
    }

    public void add(final int index, final MutableTabConfig tab) {
        if (index >= tabs.size()) {
            tabs.add(tab);
        } else {
            tabs.add(index, tab);
        }
        tab.setParent(this);
    }

    public void remove(final MutableTabConfig tab) {
        tabs.remove(tab);
        tab.setParent(null);
    }

    public int indexOf(final MutableTabConfig tab) {
        return tabs.indexOf(tab);
    }

    public int getVisibleTabCount() {
        return getVisibleTabs().size();
    }

    public int getAllTabCount() {
        return tabs.size();
    }

    public List<MutableTabConfig> getTabs() {
        return tabs;
    }

    public Integer getSelected() {
        return selected;
    }

    public void setSelected(final Integer selected) {
        this.selected = selected;
    }
}
