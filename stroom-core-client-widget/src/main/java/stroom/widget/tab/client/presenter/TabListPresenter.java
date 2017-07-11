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

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.logical.shared.HasSelectionHandlers;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.event.shared.LegacyHandlerWrapper;
import com.google.gwt.event.shared.SimpleEventBus;
import stroom.svg.client.Icon;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuItem;
import stroom.widget.menu.client.presenter.MenuPresenter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class TabListPresenter extends MenuPresenter implements HasSelectionHandlers<TabData> {
    private static class TabDataIconMenuItem extends IconMenuItem {
        private final TabData tabData;

        public TabDataIconMenuItem(final int priority, final TabData tabData, final Icon enabledIcon, final String text) {
            super(priority, enabledIcon, null, text, null, true, null);
            this.tabData = tabData;
        }

        public TabData getTabData() {
            return tabData;
        }
    }

    private static class TabItemComparator implements Comparator<TabData> {
        @Override
        public int compare(final TabData o1, final TabData o2) {
            return o1.getLabel().compareTo(o2.getLabel());
        }
    }

    public TabListPresenter() {
        super(new SimpleEventBus());
    }

    public void setData(final List<TabData> tabList, final int nonVisibleCount) {
        final List<TabData> tabsNotShown = new ArrayList<>();
        final List<TabData> tabsShown = new ArrayList<>();

        for (int i = tabList.size() - nonVisibleCount; i < tabList.size(); i++) {
            tabsNotShown.add(tabList.get(i));
        }
        for (int i = 0; i < tabList.size() - nonVisibleCount; i++) {
            tabsShown.add(tabList.get(i));
        }

        final TabItemComparator comparator = new TabItemComparator();
        Collections.sort(tabsNotShown, comparator);
        Collections.sort(tabsShown, comparator);

        final List<Item> menuItems = new ArrayList<>();

        for (final TabData tabData : tabsNotShown) {
            menuItems.add(new TabDataIconMenuItem(0, tabData, tabData.getIcon(), "<b>" + tabData.getLabel() + "</b>"));
        }
        for (final TabData tabData : tabsShown) {
            menuItems.add(new TabDataIconMenuItem(0, tabData, tabData.getIcon(), tabData.getLabel()));
        }

        setData(menuItems);
    }

    @Override
    protected void onClick(final MenuItem menuItem, final Element event) {
        final TabDataIconMenuItem tabDataIconMenuItem = (TabDataIconMenuItem) menuItem;
        SelectionEvent.fire(this, tabDataIconMenuItem.getTabData());
        hide(false, true, false);
    }

    @Override
    public HandlerRegistration addSelectionHandler(final SelectionHandler<TabData> handler) {
        return new LegacyHandlerWrapper(addHandler(SelectionEvent.getType(), handler));
    }
}
