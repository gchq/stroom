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

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.util.client.BorderUtil;
import stroom.widget.util.client.MultiSelectionModel;

import java.util.List;

public class FSVolumeListPresenter extends MyPresenterWidget<DataGridView<FsVolume>> {
    @Inject
    public FSVolumeListPresenter(final EventBus eventBus) {
        super(eventBus, new DataGridViewImpl<>(true, true));

        // Add a border to the list.
        BorderUtil.addBorder(getWidget().getElement());

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
        getView().addResizableColumn(volumeColumn, "Path", 300);

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
        getView().addResizableColumn(streamStatusColumn, "Status", 90);

        getView().addEndColumn(new EndColumn<>());
    }

    public void setData(final List<FsVolume> volumes) {
        getView().setRowData(0, volumes);
        getView().setRowCount(volumes.size());
    }

    public MultiSelectionModel<FsVolume> getSelectionModel() {
        return getView().getSelectionModel();
    }
}
