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

package stroom.dashboard.client.table;

import stroom.query.api.Column;
import stroom.query.api.ColumnFilter;
import stroom.query.api.IncludeExcludeFilter;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.tab.client.presenter.LinkTabsLayoutView;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.Layer;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

public class TableFilterPresenter
        extends MyPresenterWidget<LinkTabsLayoutView>
        implements Focus {

    private final Map<TabData, Layer> tabViewMap = new HashMap<>();
    private final ColumnFilterPresenter columnFilterPresenter;
    private final IncludeExcludeFilterPresenter includeExcludeFilterPresenter;
    private TabData selectedTab;

    @Inject
    public TableFilterPresenter(final EventBus eventBus,
                                final LinkTabsLayoutView view,
                                final ColumnFilterPresenter columnFilterPresenter,
                                final IncludeExcludeFilterPresenter includeExcludeFilterPresenter) {
        super(eventBus, view);
        this.columnFilterPresenter = columnFilterPresenter;
        this.includeExcludeFilterPresenter = includeExcludeFilterPresenter;
        getWidget().getElement().addClassName("default-min-sizes");

        final TabData tabData = addTab("Filter", columnFilterPresenter);
        addTab("Include Exclude", includeExcludeFilterPresenter);
        changeSelectedTab(tabData);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(getView().getTabBar().addSelectionHandler(event -> {
            final TabData tab = event.getSelectedItem();
            if (tab != null && tab != selectedTab) {
                changeSelectedTab(tab);
            }
        }));
        registerHandler(getView().getTabBar().addShowMenuHandler(e -> getEventBus().fireEvent(e)));
    }

    public void show(final Column column,
                     final BiConsumer<Column, Column> columnChangeConsumer) {
        columnFilterPresenter.setColumnFilter(column.getColumnFilter());
        includeExcludeFilterPresenter.setFilter(column.getFilter());

        final PopupSize popupSize = PopupSize.resizable(1085, 500, 1085, 500);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Filter '" + column.getName() + "'")
                .modal(true)
                .onShow(e -> focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final ColumnFilter columnFilter = columnFilterPresenter.getColumnFilter();
                        final IncludeExcludeFilter includeExcludeFilter = includeExcludeFilterPresenter.getFilter();
                        if ((!Objects.equals(columnFilter, column.getColumnFilter()) ||
                             (!Objects.equals(includeExcludeFilter, column.getFilter())))) {
                            columnChangeConsumer.accept(column, column
                                    .copy()
                                    .columnFilter(columnFilter)
                                    .filter(includeExcludeFilter)
                                    .build());
                        }
                    }
                    e.hide();
                })
                .fire();
    }

    @Override
    public void focus() {
        if (selectedTab != null) {
            final Layer layer = tabViewMap.get(selectedTab);
            if (layer instanceof Focus) {
                ((Focus) layer).focus();
            }
        } else {
            getView().getTabBar().focus();
        }
    }

    public TabData addTab(final String text, final Layer layer) {
        final TabData tab = new TabDataImpl(text, false);
        tabViewMap.put(tab, layer);
        getView().getTabBar().addTab(tab);
        return tab;
    }

    private void changeSelectedTab(final TabData tab) {
        if (selectedTab != tab) {
            selectedTab = tab;
            if (selectedTab != null) {
                final Layer layer = tabViewMap.get(selectedTab);
                if (layer != null) {
                    getView().getTabBar().selectTab(tab);
                    getView().getLayerContainer().show(layer);
                }
            }
        }
    }
}
