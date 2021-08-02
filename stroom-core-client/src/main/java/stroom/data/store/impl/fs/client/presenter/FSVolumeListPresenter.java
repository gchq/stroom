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

package stroom.data.store.impl.fs.client.presenter;

import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.List;

public class FSVolumeListPresenter extends MyPresenterWidget<PagerView> {

    private final MyDataGrid<FsVolume> dataGrid;
    private final MultiSelectionModelImpl<FsVolume> selectionModel;

    @Inject
    public FSVolumeListPresenter(final EventBus eventBus,
                                 final PagerView view) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>();
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);

        // Add a border to the list.
        getWidget().getElement().addClassName("stroom-border");

        initTableColumns();
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        // Path.
        final Column<FsVolume, String> volumeColumn = new Column<FsVolume, String>(new TextCell()) {
            @Override
            public String getValue(final FsVolume row) {
                if (row == null) {
                    return null;
                }
                return row.getPath();
            }
        };
        dataGrid.addResizableColumn(volumeColumn, "Path", 300);

        // Status.
        final Column<FsVolume, String> streamStatusColumn = new Column<FsVolume, String>(new TextCell()) {
            @Override
            public String getValue(final FsVolume row) {
                if (row == null) {
                    return null;
                }
                return row.getStatus().getDisplayValue();
            }
        };
        dataGrid.addResizableColumn(streamStatusColumn, "Status", 90);

        dataGrid.addEndColumn(new EndColumn<>());
    }

    public void setData(final List<FsVolume> volumes) {
        dataGrid.setRowData(0, volumes);
        dataGrid.setRowCount(volumes.size());
    }

    public MultiSelectionModel<FsVolume> getSelectionModel() {
        return selectionModel;
    }
}
