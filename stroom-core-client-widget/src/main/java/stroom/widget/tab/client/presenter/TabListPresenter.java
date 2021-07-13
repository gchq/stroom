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

import stroom.data.table.client.CellTableView;
import stroom.data.table.client.ScrollableCellTableViewImpl;
import stroom.svg.client.Icon;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuItem;
import stroom.widget.menu.client.presenter.MenuItemCell;
import stroom.widget.menu.client.presenter.MenuItemCellUiHandler;
import stroom.widget.menu.client.presenter.MenuItemPresenter;
import stroom.widget.popup.client.event.HidePopupEvent;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.logical.shared.HasSelectionHandlers;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.event.shared.LegacyHandlerWrapper;
import com.google.gwt.event.shared.SimpleEventBus;
import com.google.gwt.user.cellview.client.Column;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class TabListPresenter
        extends MyPresenterWidget<CellTableView<Item>>
        implements HasSelectionHandlers<TabData>,
        MenuItemCellUiHandler {

    private final Map<Item, Element> hoverItems = new HashMap<>();
    private Set<Item> highlightItems;

    public TabListPresenter() {
        super(new SimpleEventBus(), new ScrollableCellTableViewImpl<>(
                false,
                "menuCellTable",
                50,
                600,
                600));

        final Column<Item, Item> iconColumn = new Column<Item, Item>(new MenuItemCell(this)) {
            @Override
            public Item getValue(final Item item) {
                return item;
            }
        };
        getView().addColumn(iconColumn);
        getView().setSkipRowHoverCheck(true);
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

        getView().setRowData(0, menuItems);
        getView().setRowCount(menuItems.size());

        removeAllHovers();
    }


    @Override
    public HandlerRegistration addSelectionHandler(final SelectionHandler<TabData> handler) {
        return new LegacyHandlerWrapper(addHandler(SelectionEvent.getType(), handler));
    }


    @Override
    public void onClick(final MenuItem menuItem, final Element element) {
        final TabDataIconMenuItem tabDataIconMenuItem = (TabDataIconMenuItem) menuItem;
        SelectionEvent.fire(this, tabDataIconMenuItem.getTabData());
        HidePopupEvent.fire(this, this);
    }

    @Override
    public void onMouseOver(final MenuItem menuItem, final Element element) {
        hoverItems.put(menuItem, element);
    }

    @Override
    public void onMouseOut(final MenuItem menuItem, final Element element) {
        hoverItems.remove(menuItem);
    }

    @Override
    public boolean isHover(final MenuItem menuItem) {
        return hoverItems.containsKey(menuItem);
    }

    public void removeHover(final MenuItem menuItem) {
        final Element tr = hoverItems.remove(menuItem);
        if (tr != null) {
            tr.removeClassName("cellTableHoveredRow");
        }
    }

    protected void removeAllHovers() {
        final Iterator<Entry<Item, Element>> iter = hoverItems.entrySet().iterator();
        while (iter.hasNext()) {
            final Entry<Item, Element> entry = iter.next();

            final Element tr = entry.getValue();
            if (tr != null) {
                tr.removeClassName("cellTableHoveredRow");
            }

            iter.remove();
        }
    }

    @Override
    public boolean isHighlighted(final MenuItem menuItem) {
        if (highlightItems == null) {
            return false;
        }
        return highlightItems.contains(menuItem);
    }
//
//    public Set<Item> getHighlightItems() {
//        return highlightItems;
//    }
//
//    public void setHighlightItems(final Set<Item> highlightItems) {
//        this.highlightItems = highlightItems;
//    }

    protected void setEnabled(final MenuItemPresenter menuItemPresenter, final boolean enabled) {
        if (menuItemPresenter != null) {
            menuItemPresenter.setEnabled(enabled);
        }
    }

    public void hide(final boolean autoClose, final boolean ok, final boolean hideParent) {
        // Hide this menu.
        HidePopupEvent.fire(this, this);
    }


    private static class TabDataIconMenuItem extends IconMenuItem {

        private final TabData tabData;

        public TabDataIconMenuItem(final int priority,
                                   final TabData tabData,
                                   final Icon enabledIcon,
                                   final String text) {
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
}
