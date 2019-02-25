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
import stroom.data.store.impl.fs.shared.FSVolume;
import stroom.data.store.impl.fs.shared.FindFSVolumeAction;
import stroom.data.store.impl.fs.shared.FindFSVolumeCriteria;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.streamstore.client.presenter.ActionDataProvider;
import stroom.streamstore.client.presenter.ColumnSizeConstants;
import stroom.util.client.BorderUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.util.client.MultiSelectionModel;

public class FSVolumeStatusListPresenter extends MyPresenterWidget<DataGridView<FSVolume>> {
    private final ActionDataProvider<FSVolume> dataProvider;

    @Inject
    public FSVolumeStatusListPresenter(final EventBus eventBus, final ClientDispatchAsync dispatcher) {
        super(eventBus, new DataGridViewImpl<>(true, true));

        // Add a border to the list.
        BorderUtil.addBorder(getWidget().getElement());

        initTableColumns();

        final FindFSVolumeAction action = new FindFSVolumeAction(new FindFSVolumeCriteria());
        dataProvider = new ActionDataProvider<>(dispatcher, action);
        dataProvider.addDataDisplay(getView().getDataDisplay());
        dataProvider.refresh();
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        // Path.
        final Column<FSVolume, String> volumeColumn = new Column<FSVolume, String>(new TextCell()) {
            @Override
            public String getValue(final FSVolume volume) {
                return volume.getPath();
            }
        };
        getView().addResizableColumn(volumeColumn, "Path", 300);

        // Status.
        final Column<FSVolume, String> streamStatusColumn = new Column<FSVolume, String>(new TextCell()) {
            @Override
            public String getValue(final FSVolume volume) {
                return volume.getStatus().getDisplayValue();
            }
        };
        getView().addResizableColumn(streamStatusColumn, "Status", 90);

        // Total.
        final Column<FSVolume, String> totalColumn = new Column<FSVolume, String>(new TextCell()) {
            @Override
            public String getValue(final FSVolume volume) {
                return getSizeString(volume.getVolumeState().getBytesTotal());
            }
        };
        getView().addResizableColumn(totalColumn, "Total", ColumnSizeConstants.SMALL_COL);

        // Limit.
        final Column<FSVolume, String> limitColumn = new Column<FSVolume, String>(new TextCell()) {
            @Override
            public String getValue(final FSVolume volume) {
                if (volume.getByteLimit() == null) {
                    return "";
                }
                return getSizeString(volume.getByteLimit());
            }
        };
        getView().addResizableColumn(limitColumn, "Limit", ColumnSizeConstants.SMALL_COL);

        // Used.
        final Column<FSVolume, String> usedColumn = new Column<FSVolume, String>(new TextCell()) {
            @Override
            public String getValue(final FSVolume volume) {
                return getSizeString(volume.getVolumeState().getBytesUsed());
            }
        };
        getView().addResizableColumn(usedColumn, "Used", ColumnSizeConstants.SMALL_COL);

        // Free.
        final Column<FSVolume, String> freeColumn = new Column<FSVolume, String>(new TextCell()) {
            @Override
            public String getValue(final FSVolume volume) {
                return getSizeString(volume.getVolumeState().getBytesFree());
            }
        };
        getView().addResizableColumn(freeColumn, "Free", ColumnSizeConstants.SMALL_COL);

        // Use%.
        final Column<FSVolume, String> usePercentColumn = new Column<FSVolume, String>(new TextCell()) {
            @Override
            public String getValue(final FSVolume volume) {
                return getPercentString(volume.getVolumeState().getPercentUsed());
            }
        };
        getView().addResizableColumn(usePercentColumn, "Use%", ColumnSizeConstants.SMALL_COL);

        // Usage Date.
        final Column<FSVolume, String> usageDateColumn = new Column<FSVolume, String>(new TextCell()) {
            @Override
            public String getValue(final FSVolume volume) {
                return ClientDateUtil.toISOString(volume.getVolumeState().getUpdateTimeMs());
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

    public MultiSelectionModel<FSVolume> getSelectionModel() {
        return getView().getSelectionModel();
    }

    public void refresh() {
        dataProvider.refresh();
    }
}
