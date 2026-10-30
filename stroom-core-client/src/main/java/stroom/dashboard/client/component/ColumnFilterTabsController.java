/*
 * Copyright 2024 Crown Copyright
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

package stroom.dashboard.client.component;

import stroom.query.api.Column;
import stroom.query.api.ColumnFilter;
import stroom.query.api.IncludeExcludeFilter;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.ControllerLayer;
import stroom.widget.component.client.Layer;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.tab.client.TabData;
import stroom.widget.tab.client.TabDataImpl;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/// A table column's filter dialog ([ColumnFilterTabs]): the column's filter and its include and
/// exclude filter, each on a tab. The table keeps it and shows it again for each column.
public class ColumnFilterTabsController extends Controller implements Focus {

    private final ColumnFilterTabs view;
    private final Map<TabData, Layer> tabViewMap = new HashMap<>();
    private final ColumnFilterEditorController columnFilterEditor;
    private final IncludeExcludeFilterEditorController includeExcludeFilterEditor;
    private TabData selectedTab;

    /// Made by GIN, with its content and its tabs.
    ///
    /// @param eventBus                   The application's event bus.
    /// @param view                       The content.
    /// @param columnFilterEditor         The Filter tab.
    /// @param includeExcludeFilterEditor The Include Exclude tab.
    @Inject
    public ColumnFilterTabsController(final EventBus eventBus,
                                        final ColumnFilterTabs view,
                                        final ColumnFilterEditorController columnFilterEditor,
                                        final IncludeExcludeFilterEditorController includeExcludeFilterEditor) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        own(columnFilterEditor);
        own(includeExcludeFilterEditor);
        this.columnFilterEditor = columnFilterEditor;
        this.includeExcludeFilterEditor = includeExcludeFilterEditor;

        final TabData tabData = addTab("Filter", new ControllerLayer(columnFilterEditor));
        addTab("Include Exclude", new ControllerLayer(includeExcludeFilterEditor));
        changeSelectedTab(tabData);

        view.setOnTabSelected(tab -> {
            if (tab != null && tab != selectedTab) {
                changeSelectedTab(tab);
            }
        });
        view.setOnShowMenu(event -> getEventBus().fireEvent(event));
    }

    /// Shows a column's filters; OK gives the column with the filters changed, if they were.
    ///
    /// @param column               The column.
    /// @param columnChangeConsumer Given the column and its replacement, when OK changes it.
    public void show(final Column column,
                     final BiConsumer<Column, Column> columnChangeConsumer) {
        columnFilterEditor.setColumnFilter(column.getColumnFilter());
        includeExcludeFilterEditor.setFilter(column.getFilter());

        final PopupSize popupSize = PopupSize.resizable(1085, 500, 1085, 500);
        ControllerDialog.show(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Filter '" + column.getName() + "'")
                .onShow(e -> focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final ColumnFilter columnFilter = columnFilterEditor.getColumnFilter(column);
                        final IncludeExcludeFilter includeExcludeFilter = includeExcludeFilterEditor.getFilter();
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
            if (layer instanceof final Focus focus) {
                focus.focus();
            }
        } else {
            view.focusTabBar();
        }
    }

    private TabData addTab(final String text, final Layer layer) {
        final TabData tab = new TabDataImpl(text, false);
        tabViewMap.put(tab, layer);
        view.addTab(tab);
        return tab;
    }

    private void changeSelectedTab(final TabData tab) {
        if (selectedTab != tab) {
            selectedTab = tab;
            if (selectedTab != null) {
                final Layer layer = tabViewMap.get(selectedTab);
                if (layer != null) {
                    view.showTab(tab, layer);
                }
            }
        }
    }
}
