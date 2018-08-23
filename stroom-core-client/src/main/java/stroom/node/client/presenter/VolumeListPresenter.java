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
import stroom.node.shared.VolumeEntity;
import stroom.util.client.BorderUtil;
import stroom.widget.util.client.MultiSelectionModel;

import java.util.List;

public class VolumeListPresenter extends MyPresenterWidget<DataGridView<VolumeEntity>> {
//    private final SelectionModel<Volume> selectionModel;

    @Inject
    public VolumeListPresenter(final EventBus eventBus, final ClientDispatchAsync dispatcher) {
        super(eventBus, new DataGridViewImpl<>(true, true));

//        selectionModel = new MySingleSelectionModel<>();
//        getView().setSelectionModel(selectionModel);

        // Add a border to the list.
        BorderUtil.addBorder(getWidget().getElement());

        initTableColumns();
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        // Node.
        final Column<VolumeEntity, String> nameColumn = new Column<VolumeEntity, String>(new TextCell()) {
            @Override
            public String getValue(final VolumeEntity row) {
                if (row == null) {
                    return null;
                }
                return row.getNode().getName();
            }
        };
        getView().addResizableColumn(nameColumn, "Node", 150);

        // Path.
        final Column<VolumeEntity, String> volumeColumn = new Column<VolumeEntity, String>(new TextCell()) {
            @Override
            public String getValue(final VolumeEntity row) {
                if (row == null) {
                    return null;
                }
                return row.getPath();
            }
        };
        getView().addResizableColumn(volumeColumn, "Path", 300);

        // Volume Type.
        final Column<VolumeEntity, String> volumeTypeColumn = new Column<VolumeEntity, String>(new TextCell()) {
            @Override
            public String getValue(final VolumeEntity row) {
                if (row == null) {
                    return null;
                }
                return row.getVolumeType().getDisplayValue();
            }
        };
        getView().addResizableColumn(volumeTypeColumn, "Volume Type", 80);

        // Stream Status.
        final Column<VolumeEntity, String> streamStatusColumn = new Column<VolumeEntity, String>(new TextCell()) {
            @Override
            public String getValue(final VolumeEntity row) {
                if (row == null) {
                    return null;
                }
                return row.getStreamStatus().getDisplayValue();
            }
        };
        getView().addResizableColumn(streamStatusColumn, "Stream Status", 90);

        // Index Status.
        final Column<VolumeEntity, String> indexStatusColumn = new Column<VolumeEntity, String>(new TextCell()) {
            @Override
            public String getValue(final VolumeEntity row) {
                if (row == null) {
                    return null;
                }
                return row.getIndexStatus().getDisplayValue();
            }
        };
        getView().addResizableColumn(indexStatusColumn, "Index Status", 90);

        getView().addEndColumn(new EndColumn<>());
    }

    public void setData(final List<VolumeEntity> volumes) {
        getView().setRowData(0, volumes);
        getView().setRowCount(volumes.size());
    }

//    public HandlerRegistration addSelectionHandler(DataGridSelectEvent.Handler handler) {
//        return getView().addSelectionHandler(handler);
//    }

    public MultiSelectionModel<VolumeEntity> getSelectionModel() {
        return getView().getSelectionModel();
    }
//
//    public void setSelectionModel(final SelectionModel<Volume> selectionModel) {
//        getView().setSelectionModel(selectionModel);
//    }
}
