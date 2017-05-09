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

package stroom.widget.tab.client.presenter;

import com.google.gwt.event.logical.shared.HasSelectionHandlers;
import stroom.widget.tab.client.event.HasMaximiseRequestHandlers;
import stroom.widget.tab.client.event.HasRequestCloseTabHandlers;

import java.util.List;

public interface TabBar extends HasSelectionHandlers<TabData>, HasRequestCloseTabHandlers, HasMaximiseRequestHandlers {
    void addTab(TabData tab);

    void removeTab(TabData tab);

    void clear();

    void selectTab(int index);

    void selectTab(TabData tab);

    TabData getSelectedTab();

    List<TabData> getTabs();

    void setTabHidden(TabData tab, boolean hidden);

    boolean isTabHidden(TabData tab);

    void refresh();
}
