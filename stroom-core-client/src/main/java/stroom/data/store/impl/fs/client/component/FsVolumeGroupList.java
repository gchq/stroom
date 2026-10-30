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

package stroom.data.store.impl.fs.client.component;

import stroom.data.store.impl.fs.shared.FsVolumeGroupRow;
import stroom.data.store.impl.fs.shared.FsVolumeType;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.task.client.TaskMonitorFactory;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.util.stream.Collectors;

/// The data volume groups, a row each (its name, the types of its volumes and how many it has);
/// several can be selected.
public class FsVolumeGroupList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<FsVolumeGroupRow> dataGrid;

    private final MultiSelectionModelImpl<FsVolumeGroupRow> selectionModel;

    /// Makes an empty list.
    public FsVolumeGroupList() {
        initWidget(BINDER.createAndBindUi(this));
        // Rows keyed by id, so a row whose values change is still the same row
        dataGrid.setKeyProvider(row -> row.getGroup().getId());
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        addColumns();
    }

    /// @param dataProvider Gives the list its rows, a page at a time.
    public void setDataProvider(final AbstractDataProvider<FsVolumeGroupRow> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return The selection.
    public MultiSelectionModel<FsVolumeGroupRow> getSelectionModel() {
        return selectionModel;
    }

    /// @return The toolbar beside the pager, for the screen's own buttons.
    public Toolbar getToolbar() {
        return pager.getToolbar();
    }

    private void addColumns() {
        // Name.
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(
                                (FsVolumeGroupRow row1) -> row1.getGroup().getName())
                        .build(),
                DataGridUtil.headingBuilder("Group Name")
                        .withToolTip("The name of the Volume Group.")
                        .build(),
                400);

        // Types
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(this::getVolumeTypesStr)
                        .build(),
                DataGridUtil.headingBuilder("Volume Type(s)")
                        .withToolTip("The types of Volumes inside this Volume Group.")
                        .build(),
                250);

        // Count
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(
                                (FsVolumeGroupRow row) -> Integer.toString(row.getVolumeCount()))
                        .rightAligned()
                        .build(),
                DataGridUtil.headingBuilder("Volume Count")
                        .rightAligned()
                        .withToolTip("The number of Volumes inside this Volume Group.")
                        .build(),
                150);
    }

    private String getVolumeTypesStr(final FsVolumeGroupRow row) {
        return NullSafe.getOrElse(
                row,
                FsVolumeGroupRow::getVolumeTypes,
                types -> types.stream()
                        .map(FsVolumeType::getDisplayValue)
                        .collect(Collectors.joining(", ")),
                "");
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, FsVolumeGroupList> {

    }
}
