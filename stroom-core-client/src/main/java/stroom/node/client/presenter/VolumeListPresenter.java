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

package stroom.node.client.presenter;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.node.shared.Volume;
import stroom.util.client.BorderUtil;
import stroom.widget.util.client.MySingleSelectionModel;

import java.util.List;

public class VolumeListPresenter extends MyPresenterWidget<DataGridView<Volume>> {
    private final MySingleSelectionModel<Volume> selectionModel;

    @Inject
    public VolumeListPresenter(final EventBus eventBus, final ClientDispatchAsync dispatcher) {
        super(eventBus, new DataGridViewImpl<Volume>(true));

        selectionModel = new MySingleSelectionModel<Volume>();
        getView().setSelectionModel(selectionModel);

        // Add a border to the list.
        BorderUtil.addBorder(getWidget().getElement());

        initTableColumns();
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        // Name.
        final Column<Volume, String> nameColumn = new Column<Volume, String>(new TextCell()) {
            @Override
            public String getValue(final Volume row) {
                if (row == null) {
                    return null;
                }
                return row.getNode().getName();
            }
        };
        getView().addResizableColumn(nameColumn, "Node", 150);

        // Volume.
        final Column<Volume, String> volumeColumn = new Column<Volume, String>(new TextCell()) {
            @Override
            public String getValue(final Volume row) {
                if (row == null) {
                    return null;
                }
                return row.getPath();
            }
        };
        getView().addResizableColumn(volumeColumn, "Path", 250);

        // Volume Type.
        final Column<Volume, String> volumeTypeColumn = new Column<Volume, String>(new TextCell()) {
            @Override
            public String getValue(final Volume row) {
                if (row == null) {
                    return null;
                }
                return row.getVolumeType().getDisplayValue();
            }
        };
        getView().addResizableColumn(volumeTypeColumn, "Volume Type", 100);

        // Stream Status.
        final Column<Volume, String> streamStatusColumn = new Column<Volume, String>(new TextCell()) {
            @Override
            public String getValue(final Volume row) {
                if (row == null) {
                    return null;
                }
                return row.getStreamStatus().getDisplayValue();
            }
        };
        getView().addResizableColumn(streamStatusColumn, "Stream Status", 100);

        // Index Status.
        final Column<Volume, String> indexStatusColumn = new Column<Volume, String>(new TextCell()) {
            @Override
            public String getValue(final Volume row) {
                if (row == null) {
                    return null;
                }
                return row.getIndexStatus().getDisplayValue();
            }
        };
        getView().addResizableColumn(indexStatusColumn, "Index Status", 100);

        getView().addEndColumn(new EndColumn<Volume>());
    }

    public void setData(final List<Volume> volumes) {
        getView().setRowData(0, volumes);
        getView().setRowCount(volumes.size());
    }

    public MySingleSelectionModel<Volume> getSelectionModel() {
        return selectionModel;
    }
}
