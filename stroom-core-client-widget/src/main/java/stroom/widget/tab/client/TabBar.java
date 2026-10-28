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

package stroom.widget.tab.client;

import stroom.widget.menu.client.presenter.ShowMenuEvent.HasShowMenuHandlers;
import stroom.widget.tab.client.event.HasRequestCloseTabHandlers;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.logical.shared.HasSelectionHandlers;
import com.google.gwt.user.client.ui.Focus;

import java.util.List;
import java.util.function.Consumer;

public interface TabBar extends
        HasSelectionHandlers<TabData>,
        HasRequestCloseTabHandlers,
        HasShowMenuHandlers,
        Focus {

    void addTab(TabData tab);

    void removeTab(TabData tab);

    void removeTab(TabData tab, boolean resize);

    void moveTab(TabData tabData, int tabPos);

    void clear();

    void selectTab(TabData tab);

    TabData getSelectedTab();

    List<TabData> getTabs();

    void setTabHidden(TabData tab, boolean hidden);

    boolean isTabHidden(TabData tab);

    void refresh();

    AbstractTab getTab(TabData tabData);

    /// Sets the element that shows the selected tab's content, which screen readers are told the
    /// tabs control (it becomes a `tabpanel`, named by the selected tab).
    ///
    /// @param panel The panel, or null for none.
    void setTabPanel(Element panel);

    /// @param onSelect Told the tab the user selects (a click, Enter or Space, or the All Tabs menu),
    ///                 beside the selection event, or null for nothing.
    void setOnSelect(Consumer<TabData> onSelect);

    /// @param onClose Told the tab the user asks to close (its cross, or Delete), beside the close
    ///                request event, or null for nothing.
    void setOnClose(Consumer<TabData> onClose);
}
