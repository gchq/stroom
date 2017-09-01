/*
 * Copyright 2017 Crown Copyright
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

package stroom.widget.menu.client.presenter;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.data.table.client.CellTableView;
import stroom.data.table.client.CellTableViewImpl.MenuResources;
import stroom.data.table.client.ScrollableCellTableViewImpl;
import stroom.widget.popup.client.event.HidePopupEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public abstract class MenuPresenter extends MyPresenterWidget<CellTableView<Item>> {
    private static final MenuResources MENU_RESOURCES = GWT.create(MenuResources.class);

    private final Map<Item, Element> hoverItems = new HashMap<>();
    private Set<Item> highlightItems;

    @Inject
    public MenuPresenter(final EventBus eventBus) {
        super(eventBus, new ScrollableCellTableViewImpl<>(false, MENU_RESOURCES));
        final Column<Item, Item> iconColumn = new Column<Item, Item>(new MenuItemCell(this)) {
            @Override
            public Item getValue(final Item item) {
                return item;
            }
        };
        getView().addColumn(iconColumn);
        getView().setSkipRowHoverCheck(true);
    }

    public void setData(final List<Item> items) {
        getView().setRowData(0, items);
        getView().setRowCount(items.size());
    }

    protected void onClick(final MenuItem menuItem, final Element element) {
    }

    protected void onMouseOver(final MenuItem menuItem, final Element element) {
        hoverItems.put(menuItem, element);
    }

    protected void onMouseOut(final MenuItem menuItem, final Element element) {
        hoverItems.remove(menuItem);
    }

    protected boolean isHover(final MenuItem menuItem) {
        return hoverItems.containsKey(menuItem);
    }

    protected void removeHover(final MenuItem menuItem) {
        final Element tr = hoverItems.remove(menuItem);
        if (tr != null) {
            tr.removeClassName(MENU_RESOURCES.cellTableStyle().cellTableHoveredRow());
        }
    }

    protected void removeAllHovers() {
        final Iterator<Entry<Item, Element>> iter = hoverItems.entrySet().iterator();
        while (iter.hasNext()) {
            final Entry<Item, Element> entry = iter.next();

            final Element tr = entry.getValue();
            if (tr != null) {
                tr.removeClassName(MENU_RESOURCES.cellTableStyle().cellTableHoveredRow());
            }

            iter.remove();
        }
    }

    protected boolean isHighlighted(final MenuItem menuItem) {
        if (highlightItems == null) {
            return false;
        }
        return highlightItems.contains(menuItem);
    }

    public Set<Item> getHighlightItems() {
        return highlightItems;
    }

    public void setHighlightItems(final Set<Item> highlightItems) {
        this.highlightItems = highlightItems;
    }

    protected void setEnabled(final MenuItemPresenter menuItemPresenter, final boolean enabled) {
        if (menuItemPresenter != null) {
            menuItemPresenter.setEnabled(enabled);
        }
    }

    public void hide(final boolean autoClose, final boolean ok, final boolean hideParent) {
        // Hide this menu.
        HidePopupEvent.fire(this, this);
    }
}
