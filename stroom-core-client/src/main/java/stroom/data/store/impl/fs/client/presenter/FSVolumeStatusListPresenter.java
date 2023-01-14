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

import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.data.store.impl.fs.shared.FindFsVolumeCriteria;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolumeResource;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.preferences.client.DateTimeFormatter;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.ResultPage;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.function.Consumer;

public class FSVolumeStatusListPresenter extends MyPresenterWidget<DataGridView<FsVolume>> {

    private static final FsVolumeResource FS_VOLUME_RESOURCE = GWT.create(FsVolumeResource.class);

    private final RestDataProvider<FsVolume, ResultPage<FsVolume>> dataProvider;
    private final DateTimeFormatter dateTimeFormatter;

    @Inject
    public FSVolumeStatusListPresenter(final EventBus eventBus,
                                       final RestFactory restFactory,
                                       final DateTimeFormatter dateTimeFormatter) {
        super(eventBus, new DataGridViewImpl<>(true, true));
        this.dateTimeFormatter = dateTimeFormatter;

        // Add a border to the list.
        getWidget().getElement().addClassName("stroom-border");
        getWidget().getElement().addClassName("default-min-sizes");

        initTableColumns();

        final FindFsVolumeCriteria criteria = FindFsVolumeCriteria.matchAll();
        dataProvider = new RestDataProvider<FsVolume, ResultPage<FsVolume>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<FsVolume>> dataConsumer,
                                final Consumer<Throwable> throwableConsumer) {
                CriteriaUtil.setRange(criteria, range);
                final Rest<ResultPage<FsVolume>> rest = restFactory.create();
                rest
                        .onSuccess(dataConsumer)
                        .onFailure(throwableConsumer)
                        .call(FS_VOLUME_RESOURCE)
                        .find(criteria);
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
                return getSizeString(volume.getCapacityInfo().getTotalCapacityBytes());
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
                return getSizeString(volume.getCapacityInfo().getCapacityLimitBytes());
            }
        };
        getView().addResizableColumn(limitColumn, "Limit", ColumnSizeConstants.SMALL_COL);

        // Used.
        final Column<FsVolume, String> usedColumn = new Column<FsVolume, String>(new TextCell()) {
            @Override
            public String getValue(final FsVolume volume) {
                return getSizeString(volume.getCapacityInfo().getCapacityUsedBytes());
            }
        };
        getView().addResizableColumn(usedColumn, "Used", ColumnSizeConstants.SMALL_COL);

        // Free.
        final Column<FsVolume, String> freeColumn = new Column<FsVolume, String>(new TextCell()) {
            @Override
            public String getValue(final FsVolume volume) {
                return getSizeString(volume.getCapacityInfo().getFreeCapacityBytes());
            }
        };
        getView().addResizableColumn(freeColumn, "Free", ColumnSizeConstants.SMALL_COL);

        // Use%.
        final Column<FsVolume, String> usePercentColumn = new Column<FsVolume, String>(new TextCell()) {
            @Override
            public String getValue(final FsVolume volume) {
                final OptionalDouble optUsedPercent = volume.getCapacityInfo().getUsedCapacityPercent();
                return optUsedPercent.isPresent()
                        ? ((long) optUsedPercent.getAsDouble()) + "%"
                        : "?";
            }
        };
        getView().addResizableColumn(usePercentColumn, "Use%", ColumnSizeConstants.SMALL_COL);

        // Is Full
        final Column<FsVolume, String> isFullColumn = new Column<FsVolume, String>(new TextCell()) {
            @Override
            public String getValue(final FsVolume volume) {
                return volume.getCapacityInfo().isFull()
                        ? "Yes"
                        : "No";
            }
        };
        getView().addResizableColumn(isFullColumn, "Full", ColumnSizeConstants.SMALL_COL);

        // Usage Date.
        final Column<FsVolume, String> usageDateColumn = new Column<FsVolume, String>(new TextCell()) {
            @Override
            public String getValue(final FsVolume volume) {
                return dateTimeFormatter.format(volume.getVolumeState().getUpdateTimeMs());
            }
        };
        getView().addResizableColumn(usageDateColumn, "Usage Date", ColumnSizeConstants.DATE_COL);

        getView().addEndColumn(new EndColumn<>());
    }

    private String getSizeString(final OptionalLong optSizeBytes) {
        return optSizeBytes != null && optSizeBytes.isPresent()
                ? ModelStringUtil.formatIECByteSizeString(optSizeBytes.getAsLong())
                : "?";
    }

    public MultiSelectionModel<FsVolume> getSelectionModel() {
        return getView().getSelectionModel();
    }

    public void refresh() {
        dataProvider.refresh();
    }
}
