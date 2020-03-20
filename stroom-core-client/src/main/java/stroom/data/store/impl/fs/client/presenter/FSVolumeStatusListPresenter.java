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
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.data.store.impl.fs.shared.FindFsVolumeCriteria;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolumeResource;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.util.client.BorderUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.ResultPage;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.util.client.MultiSelectionModel;

import java.util.function.Consumer;

public class FSVolumeStatusListPresenter extends MyPresenterWidget<DataGridView<FsVolume>> {
    private static final FsVolumeResource FS_VOLUME_RESOURCE = GWT.create(FsVolumeResource.class);

    private final RestDataProvider<FsVolume, ResultPage<FsVolume>> dataProvider;

    @Inject
    public FSVolumeStatusListPresenter(final EventBus eventBus, final RestFactory restFactory) {
        super(eventBus, new DataGridViewImpl<>(true, true));

        // Add a border to the list.
        BorderUtil.addBorder(getWidget().getElement());

        initTableColumns();

        final FindFsVolumeCriteria criteria = new FindFsVolumeCriteria();
        dataProvider = new RestDataProvider<FsVolume, ResultPage<FsVolume>>(eventBus, criteria.obtainPageRequest()) {
            @Override
            protected void exec(final Consumer<ResultPage<FsVolume>> dataConsumer, final Consumer<Throwable> throwableConsumer) {
                final Rest<ResultPage<FsVolume>> rest = restFactory.create();
                rest.onSuccess(dataConsumer).onFailure(throwableConsumer).call(FS_VOLUME_RESOURCE).find(criteria);
            }
        };
        dataProvider.addDataDisplay(getView().getDataDisplay());
        dataProvider.refresh();
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        // Path.
        final Column<FsVolume, String> volumeColumn = new Column<FsVolume, String>(new TextCell()) {
            @Override
            public String getValue(final FsVolume volume) {
                return volume.getPath();
            }
        };
        getView().addResizableColumn(volumeColumn, "Path", 300);

        // Status.
        final Column<FsVolume, String> streamStatusColumn = new Column<FsVolume, String>(new TextCell()) {
            @Override
            public String getValue(final FsVolume volume) {
                return volume.getStatus().getDisplayValue();
            }
        };
        getView().addResizableColumn(streamStatusColumn, "Status", 90);

        // Total.
        final Column<FsVolume, String> totalColumn = new Column<FsVolume, String>(new TextCell()) {
            @Override
            public String getValue(final FsVolume volume) {
                return getSizeString(volume.getVolumeState().getBytesTotal());
            }
        };
        getView().addResizableColumn(totalColumn, "Total", ColumnSizeConstants.SMALL_COL);

        // Limit.
        final Column<FsVolume, String> limitColumn = new Column<FsVolume, String>(new TextCell()) {
            @Override
            public String getValue(final FsVolume volume) {
                if (volume.getByteLimit() == null) {
                    return "";
                }
                return getSizeString(volume.getByteLimit());
            }
        };
        getView().addResizableColumn(limitColumn, "Limit", ColumnSizeConstants.SMALL_COL);

        // Used.
        final Column<FsVolume, String> usedColumn = new Column<FsVolume, String>(new TextCell()) {
            @Override
            public String getValue(final FsVolume volume) {
                return getSizeString(volume.getVolumeState().getBytesUsed());
            }
        };
        getView().addResizableColumn(usedColumn, "Used", ColumnSizeConstants.SMALL_COL);

        // Free.
        final Column<FsVolume, String> freeColumn = new Column<FsVolume, String>(new TextCell()) {
            @Override
            public String getValue(final FsVolume volume) {
                return getSizeString(volume.getVolumeState().getBytesFree());
            }
        };
        getView().addResizableColumn(freeColumn, "Free", ColumnSizeConstants.SMALL_COL);

        // Use%.
        final Column<FsVolume, String> usePercentColumn = new Column<FsVolume, String>(new TextCell()) {
            @Override
            public String getValue(final FsVolume volume) {
                return getPercentString(volume.getVolumeState().getPercentUsed());
            }
        };
        getView().addResizableColumn(usePercentColumn, "Use%", ColumnSizeConstants.SMALL_COL);

        // Usage Date.
        final Column<FsVolume, String> usageDateColumn = new Column<FsVolume, String>(new TextCell()) {
            @Override
            public String getValue(final FsVolume volume) {
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

    public MultiSelectionModel<FsVolume> getSelectionModel() {
        return getView().getSelectionModel();
    }

    public void refresh() {
        dataProvider.refresh();
    }
}
