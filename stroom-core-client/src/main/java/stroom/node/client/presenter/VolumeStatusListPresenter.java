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
import stroom.entity.shared.EntityServiceFindAction;
import stroom.node.shared.FindVolumeCriteria;
import stroom.node.shared.VolumeEntity;
import stroom.streamstore.client.presenter.ActionDataProvider;
import stroom.streamstore.client.presenter.ColumnSizeConstants;
import stroom.util.client.BorderUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.util.client.MultiSelectionModel;

public class VolumeStatusListPresenter extends MyPresenterWidget<DataGridView<VolumeEntity>> {
//    private final SelectionModel<Volume> selectionModel;

    private final ActionDataProvider<VolumeEntity> dataProvider;

    @Inject
    public VolumeStatusListPresenter(final EventBus eventBus, final ClientDispatchAsync dispatcher) {
        super(eventBus, new DataGridViewImpl<>(true, true));
//
//        selectionModel = new MySingleSelectionModel<>();
//        getView().setSelectionModel(selectionModel);

        // Add a border to the list.
        BorderUtil.addBorder(getWidget().getElement());

        initTableColumns();

        final EntityServiceFindAction<FindVolumeCriteria, VolumeEntity> action = new EntityServiceFindAction<>(
                new FindVolumeCriteria());
        dataProvider = new ActionDataProvider<>(dispatcher, action);
        dataProvider.addDataDisplay(getView().getDataDisplay());
        dataProvider.refresh();
    }


    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        // Node.
        final Column<VolumeEntity, String> nameColumn = new Column<VolumeEntity, String>(new TextCell()) {
            @Override
            public String getValue(final VolumeEntity volume) {
                return volume.getNode().getName();
            }
        };
        getView().addResizableColumn(nameColumn, "Node", 150);

        // Path.
        final Column<VolumeEntity, String> volumeColumn = new Column<VolumeEntity, String>(new TextCell()) {
            @Override
            public String getValue(final VolumeEntity volume) {
                return volume.getPath();
            }
        };
        getView().addResizableColumn(volumeColumn, "Path", 300);

        // Volume Type.
        final Column<VolumeEntity, String> volumeTypeColumn = new Column<VolumeEntity, String>(new TextCell()) {
            @Override
            public String getValue(final VolumeEntity volume) {
                return volume.getVolumeType().getDisplayValue();
            }
        };
        getView().addResizableColumn(volumeTypeColumn, "Volume Type", 80);

        // Stream Status.
        final Column<VolumeEntity, String> streamStatusColumn = new Column<VolumeEntity, String>(new TextCell()) {
            @Override
            public String getValue(final VolumeEntity volume) {
                return volume.getStreamStatus().getDisplayValue();
            }
        };
        getView().addResizableColumn(streamStatusColumn, "Stream Status", 90);

        // Index Status.
        final Column<VolumeEntity, String> indexStatusColumn = new Column<VolumeEntity, String>(new TextCell()) {
            @Override
            public String getValue(final VolumeEntity volume) {
                return volume.getIndexStatus().getDisplayValue();
            }
        };
        getView().addResizableColumn(indexStatusColumn, "Index Status", 90);

        // Total.
        final Column<VolumeEntity, String> totalColumn = new Column<VolumeEntity, String>(new TextCell()) {
            @Override
            public String getValue(final VolumeEntity volume) {
                return getSizeString(volume.getVolumeState().getBytesTotal());
            }
        };
        getView().addResizableColumn(totalColumn, "Total", ColumnSizeConstants.SMALL_COL);

        // Limit.
        final Column<VolumeEntity, String> limitColumn = new Column<VolumeEntity, String>(new TextCell()) {
            @Override
            public String getValue(final VolumeEntity volume) {
                if (volume.getBytesLimit() == null) {
                    return "";
                }
                return getSizeString(volume.getBytesLimit());
            }
        };
        getView().addResizableColumn(limitColumn, "Limit", ColumnSizeConstants.SMALL_COL);

        // Used.
        final Column<VolumeEntity, String> usedColumn = new Column<VolumeEntity, String>(new TextCell()) {
            @Override
            public String getValue(final VolumeEntity volume) {
                return getSizeString(volume.getVolumeState().getBytesUsed());
            }
        };
        getView().addResizableColumn(usedColumn, "Used", ColumnSizeConstants.SMALL_COL);

        // Free.
        final Column<VolumeEntity, String> freeColumn = new Column<VolumeEntity, String>(new TextCell()) {
            @Override
            public String getValue(final VolumeEntity volume) {
                return getSizeString(volume.getVolumeState().getBytesFree());
            }
        };
        getView().addResizableColumn(freeColumn, "Free", ColumnSizeConstants.SMALL_COL);

        // Use%.
        final Column<VolumeEntity, String> usePercentColumn = new Column<VolumeEntity, String>(new TextCell()) {
            @Override
            public String getValue(final VolumeEntity volume) {
                return getPercentString(volume.getVolumeState().getPercentUsed());
            }
        };
        getView().addResizableColumn(usePercentColumn, "Use%", ColumnSizeConstants.SMALL_COL);

        // Usage Date.
        final Column<VolumeEntity, String> usageDateColumn = new Column<VolumeEntity, String>(new TextCell()) {
            @Override
            public String getValue(final VolumeEntity volume) {
                return ClientDateUtil.toISOString(volume.getVolumeState().getStatusMs());
            }
        };
        getView().addResizableColumn(usageDateColumn, "Usage Date", ColumnSizeConstants.DATE_COL);

        getView().addEndColumn(new EndColumn<>());
    }

    private String getSizeString(final Long size) {
        String string = "?";
        if (size != null) {
            string = ModelStringUtil.formatIECByteSizeString(size);
        }
        return string;
    }

    private String getPercentString(final Long percent) {
        String string = "?";
        if (percent != null) {
            string = percent + "%";
        }
        return string;
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

    public void refresh() {
        dataProvider.refresh();
    }
}
