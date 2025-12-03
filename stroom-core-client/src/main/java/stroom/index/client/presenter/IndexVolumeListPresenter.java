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

package stroom.index.client.presenter;

import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.index.shared.IndexVolume;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.List;

public class IndexVolumeListPresenter extends MyPresenterWidget<PagerView> {

    private final MyDataGrid<IndexVolume> dataGrid;
    private final MultiSelectionModelImpl<IndexVolume> selectionModel;

    @Inject
    public IndexVolumeListPresenter(final EventBus eventBus,
                                    final PagerView view) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>(this);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);

        initTableColumns();
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        // Path.
        final Column<IndexVolume, String> volumeColumn = new Column<IndexVolume, String>(new TextCell()) {
            @Override
            public String getValue(final IndexVolume row) {
                if (row == null) {
                    return null;
                }
                return row.getPath();
            }
        };
        dataGrid.addResizableColumn(volumeColumn, "Path", 300);

        // Status.
        final Column<IndexVolume, String> streamStatusColumn = new Column<IndexVolume, String>(new TextCell()) {
            @Override
            public String getValue(final IndexVolume row) {
                if (row == null) {
                    return null;
                }
                return row.getState().getDisplayValue();
            }
        };
        dataGrid.addResizableColumn(streamStatusColumn, "Status", 90);

        dataGrid.addEndColumn(new EndColumn<>());
    }

    public void setData(final List<IndexVolume> volumes) {
        dataGrid.setRowData(0, volumes);
        dataGrid.setRowCount(volumes.size());
    }

    public MultiSelectionModel<IndexVolume> getSelectionModel() {
        return selectionModel;
    }
}
