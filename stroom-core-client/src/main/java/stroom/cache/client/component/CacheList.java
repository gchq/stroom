/*
 * Copyright 2026 Crown Copyright
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

package stroom.cache.client.component;

import stroom.cell.client.ActionCell;
import stroom.cell.client.CellIcon;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.PropertyPath;
import stroom.util.shared.cache.CacheIdentity;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.task.client.TaskMonitorFactory;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.util.function.Consumer;

/// The caches, a row each (its name and the property path its settings are under), one of which can
/// be selected, each with a button to clear it.
public class CacheList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final int ICON_COL = 18;

    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<CacheIdentity> dataGrid;

    private final MultiSelectionModelImpl<CacheIdentity> selectionModel;
    private Consumer<String> onClear = cacheName -> {
    };

    /// Makes an empty list.
    public CacheList() {
        initWidget(BINDER.createAndBindUi(this));
        // Rows keyed by name, so a row whose values change is still the same row
        dataGrid.setKeyProvider(CacheIdentity::getCacheName);
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        addColumns();
    }

    /// @param dataProvider Gives the list its rows, a page at a time.
    public void setDataProvider(final AbstractDataProvider<CacheIdentity> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return The selection, one cache at most.
    public MultiSelectionModel<CacheIdentity> getSelectionModel() {
        return selectionModel;
    }

    /// @param onClear Given a cache's name when its Clear and rebuild button is pressed.
    public void setOnClear(final Consumer<String> onClear) {
        this.onClear = onClear;
    }

    private void addColumns() {
        final ActionCell<String> clearCell = new ActionCell<>(
                CellIcon.of(SvgImage.DELETE, "Clear and rebuild cache"),
                (cacheName, nativeEvent) -> onClear.accept(cacheName));
        dataGrid.addColumn(new Column<CacheIdentity, String>(clearCell) {
            @Override
            public String getValue(final CacheIdentity row) {
                return row.getCacheName();
            }
        }, "", ICON_COL);
        dataGrid.addResizableColumn(new Column<CacheIdentity, String>(new TextCell()) {
            @Override
            public String getValue(final CacheIdentity cacheIdentity) {
                return cacheIdentity.getCacheName();
            }
        }, "Name", 250);
        dataGrid.addResizableColumn(new Column<CacheIdentity, String>(new TextCell()) {
            @Override
            public String getValue(final CacheIdentity cacheIdentity) {
                final PropertyPath basePropertyPath = cacheIdentity.getBasePropertyPath();
                return basePropertyPath == null || basePropertyPath.isBlank()
                        ? "Cache not configurable"
                        : basePropertyPath.toString();
            }
        }, "Property Path Base", 500);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, CacheList> {

    }
}
