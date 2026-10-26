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

package stroom.index.client.component;

import stroom.index.shared.IndexVolumeGroup;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

/// The index volume groups, a row each (its name); several can be selected.
public class IndexVolumeGroupList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<IndexVolumeGroup> dataGrid;

    private final MultiSelectionModelImpl<IndexVolumeGroup> selectionModel;

    /// Makes an empty list.
    public IndexVolumeGroupList() {
        initWidget(BINDER.createAndBindUi(this));
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        // Name.
        final Column<IndexVolumeGroup, String> volumeColumn = new Column<IndexVolumeGroup, String>(new TextCell()) {
            @Override
            public String getValue(final IndexVolumeGroup volume) {
                return volume.getName();
            }
        };
        dataGrid.addResizableColumn(volumeColumn, "Name", 400);
    }

    /// @param dataProvider Gives the list its rows, a page at a time.
    public void setDataProvider(final AbstractDataProvider<IndexVolumeGroup> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return The selection.
    public MultiSelectionModel<IndexVolumeGroup> getSelectionModel() {
        return selectionModel;
    }

    /// @return The toolbar beside the pager, for the screen's own buttons.
    public Toolbar getToolbar() {
        return pager.getToolbar();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, IndexVolumeGroupList> {

    }
}
