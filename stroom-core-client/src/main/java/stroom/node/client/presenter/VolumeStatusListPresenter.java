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
import com.google.gwt.event.shared.HandlerRegistration;
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
import stroom.node.shared.Volume;
import stroom.streamstore.client.presenter.ActionDataProvider;
import stroom.streamstore.client.presenter.ColumnSizeConstants;
import stroom.util.client.BorderUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.util.client.MultiSelectionModel;

public class VolumeStatusListPresenter extends MyPresenterWidget<DataGridView<Volume>> {
//    private final SelectionModel<Volume> selectionModel;

    private final ActionDataProvider<Volume> dataProvider;

    @Inject
    public VolumeStatusListPresenter(final EventBus eventBus, final ClientDispatchAsync dispatcher) {
        super(eventBus, new DataGridViewImpl<Volume>(true, true));
//
//        selectionModel = new MySingleSelectionModel<>();
//        getView().setSelectionModel(selectionModel);

        // Add a border to the list.
        BorderUtil.addBorder(getWidget().getElement());

        initTableColumns();

        final EntityServiceFindAction<FindVolumeCriteria, Volume> action = new EntityServiceFindAction<FindVolumeCriteria, Volume>(
                new FindVolumeCriteria());
        dataProvider = new ActionDataProvider<Volume>(dispatcher, action);
        dataProvider.addDataDisplay(getView().getDataDisplay());
        dataProvider.refresh();
    }


    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        // Name.
        final Column<Volume, String> nameColumn = new Column<Volume, String>(new TextCell()) {
            @Override
            public String getValue(final Volume volume) {
                return volume.getNode().getName();
            }
        };
        getView().addResizableColumn(nameColumn, "Node", 150);

        // Volume.
        final Column<Volume, String> volumeColumn = new Column<Volume, String>(new TextCell()) {
            @Override
            public String getValue(final Volume volume) {
                return volume.getPath();
            }
        };
        getView().addResizableColumn(volumeColumn, "Path", 250);

        // Volume Type.
        final Column<Volume, String> volumeTypeColumn = new Column<Volume, String>(new TextCell()) {
            @Override
            public String getValue(final Volume volume) {
                return volume.getVolumeType().getDisplayValue();
            }
        };
        getView().addResizableColumn(volumeTypeColumn, "Volume Type", ColumnSizeConstants.MEDIUM_COL);

        // Stream Status.
        final Column<Volume, String> streamStatusColumn = new Column<Volume, String>(new TextCell()) {
            @Override
            public String getValue(final Volume volume) {
                return volume.getStreamStatus().getDisplayValue();
            }
        };
        getView().addResizableColumn(streamStatusColumn, "Stream Status", ColumnSizeConstants.MEDIUM_COL);

        // Index Status.
        final Column<Volume, String> indexStatusColumn = new Column<Volume, String>(new TextCell()) {
            @Override
            public String getValue(final Volume volume) {
                return volume.getIndexStatus().getDisplayValue();
            }
        };
        getView().addResizableColumn(indexStatusColumn, "Index Status", ColumnSizeConstants.MEDIUM_COL);

        // Usage Date.
        final Column<Volume, String> usageDateColumn = new Column<Volume, String>(new TextCell()) {
            @Override
            public String getValue(final Volume volume) {
                return ClientDateUtil.createDateTimeString(volume.getVolumeState().getStatusMs());
            }
        };
        getView().addResizableColumn(usageDateColumn, "Usage Date", ColumnSizeConstants.DATE_COL);

        // Bytes limit.
        final Column<Volume, String> limitColumn = new Column<Volume, String>(new TextCell()) {
            @Override
            public String getValue(final Volume volume) {
                if (volume.getBytesLimit() == null) {
                    return "";
                }
                return getSizeString(volume.getBytesLimit());
            }
        };
        getView().addResizableColumn(limitColumn, "Limit", 100);

        // Bytes used.
        final Column<Volume, String> usedColumn = new Column<Volume, String>(new TextCell()) {
            @Override
            public String getValue(final Volume volume) {
                return getSizeString(volume.getVolumeState().getBytesUsed());
            }
        };
        getView().addResizableColumn(usedColumn, "Used", 100);

        // Bytes free.
        final Column<Volume, String> freeColumn = new Column<Volume, String>(new TextCell()) {
            @Override
            public String getValue(final Volume volume) {
                return getSizeString(volume.getVolumeState().getBytesFree());
            }
        };
        getView().addResizableColumn(freeColumn, "Free", 100);

        // Bytes total.
        final Column<Volume, String> totalColumn = new Column<Volume, String>(new TextCell()) {
            @Override
            public String getValue(final Volume volume) {
                return getSizeString(volume.getVolumeState().getBytesTotal());
            }
        };
        getView().addResizableColumn(totalColumn, "Total", 100);

        // Use%.
        final Column<Volume, String> usePercentColumn = new Column<Volume, String>(new TextCell()) {
            @Override
            public String getValue(final Volume volume) {
                return getPercentString(volume.getVolumeState().getPercentUsed());
            }
        };
        getView().addResizableColumn(usePercentColumn, "Use%", 100);
        getView().addEndColumn(new EndColumn<Volume>());
    }

    private String getSizeString(final Long size) {
        String string = "?";
        if (size != null) {
            string = ModelStringUtil.formatByteSizeString(size);
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

    public MultiSelectionModel<Volume> getSelectionModel() {
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
