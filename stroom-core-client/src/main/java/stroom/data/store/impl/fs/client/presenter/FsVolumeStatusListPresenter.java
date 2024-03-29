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
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.data.store.impl.fs.shared.FindFsVolumeCriteria;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.data.store.impl.fs.shared.FsVolumeResource;
import stroom.data.store.impl.fs.shared.FsVolumeType;
import stroom.data.store.impl.fs.shared.S3ClientConfig;
import stroom.dispatch.client.RestError;
import stroom.dispatch.client.RestFactory;
import stroom.preferences.client.DateTimeFormatter;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Selection;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

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

public class FsVolumeStatusListPresenter extends MyPresenterWidget<PagerView> {

    private static final FsVolumeResource FS_VOLUME_RESOURCE = GWT.create(FsVolumeResource.class);

    private final MyDataGrid<FsVolume> dataGrid;
    private final MultiSelectionModelImpl<FsVolume> selectionModel;
    private final RestFactory restFactory;
    private final DateTimeFormatter dateTimeFormatter;
    private FindFsVolumeCriteria criteria;
    private RestDataProvider<FsVolume, ResultPage<FsVolume>> dataProvider;

    @Inject
    public FsVolumeStatusListPresenter(final EventBus eventBus,
                                       final PagerView view,
                                       final RestFactory restFactory,
                                       final DateTimeFormatter dateTimeFormatter) {
        super(eventBus, view);
        this.restFactory = restFactory;

        dataGrid = new MyDataGrid<>();
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);

        this.dateTimeFormatter = dateTimeFormatter;
        getWidget().getElement().addClassName("default-min-sizes");

        initTableColumns();
    }

    public void setGroup(final FsVolumeGroup group) {
        criteria = new FindFsVolumeCriteria(
                null,
                group,
                null,
                Selection.selectAll());
        if (dataProvider == null) {
            dataProvider = new RestDataProvider<FsVolume, ResultPage<FsVolume>>(getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ResultPage<FsVolume>> dataConsumer,
                                    final Consumer<RestError> errorConsumer) {
                    CriteriaUtil.setRange(criteria, range);
                    restFactory
                            .create(FS_VOLUME_RESOURCE)
                            .method(res -> res.find(criteria))
                            .onSuccess(dataConsumer)
                            .onFailure(errorConsumer)
                            .exec();
                }
            };
            dataProvider.addDataDisplay(dataGrid);
        } else {
            dataProvider.refresh();
        }
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        // Path.
        final Column<FsVolume, String> volumeColumn = new Column<FsVolume, String>(new TextCell()) {
            @Override
            public String getValue(final FsVolume volume) {
                if (volume == null) {
                    return null;
                }
                if (FsVolumeType.S3.equals(volume.getVolumeType())) {
                    return GwtNullSafe.getOrElse(
                            volume.getS3ClientConfig(),
                            S3ClientConfig::getBucketName,
                            S3ClientConfig.DEFAULT_BUCKET_NAME);
                }

                return volume.getPath();
            }
        };
        dataGrid.addResizableColumn(volumeColumn, "Path", 300);

        // Volume type.
        final Column<FsVolume, String> volumeTypeColumn = new Column<FsVolume, String>(new TextCell()) {
            @Override
            public String getValue(final FsVolume row) {
                if (row == null) {
                    return null;
                }
                return row.getVolumeType().getDisplayValue();
            }
        };
        dataGrid.addResizableColumn(volumeTypeColumn, "Type", 90);

        // Status.
        final Column<FsVolume, String> streamStatusColumn = new Column<FsVolume, String>(new TextCell()) {
            @Override
            public String getValue(final FsVolume volume) {
                return volume.getStatus().getDisplayValue();
            }
        };
        dataGrid.addResizableColumn(streamStatusColumn, "Status", 90);

        // Total.
        final Column<FsVolume, String> totalColumn = new Column<FsVolume, String>(new TextCell()) {
            @Override
            public String getValue(final FsVolume volume) {
                return getSizeString(volume.getCapacityInfo().getTotalCapacityBytes());
            }
        };
        dataGrid.addResizableColumn(totalColumn, "Total", ColumnSizeConstants.SMALL_COL);

        // Limit.
        final Column<FsVolume, String> limitColumn = new Column<FsVolume, String>(
                new TextCell()) {

            @Override
            public String getValue(final FsVolume volume) {
                if (volume.getByteLimit() == null) {
                    return "";
                }
                // Special case for limit as it is user input data so need more precision
                return ModelStringUtil.formatIECByteSizeString(volume.getByteLimit(),
                        true,
                        ModelStringUtil.DEFAULT_SIGNIFICANT_FIGURES);
            }
        };
        dataGrid.addResizableColumn(limitColumn, "Limit", ColumnSizeConstants.SMALL_COL);

        // Used.
        final Column<FsVolume, String> usedColumn = new Column<FsVolume, String>(new TextCell()) {
            @Override
            public String getValue(final FsVolume volume) {
                return getSizeString(volume.getCapacityInfo().getCapacityUsedBytes());
            }
        };
        dataGrid.addResizableColumn(usedColumn, "Used", ColumnSizeConstants.SMALL_COL);

        // Free.
        final Column<FsVolume, String> freeColumn = new Column<FsVolume, String>(new TextCell()) {
            @Override
            public String getValue(final FsVolume volume) {
                return getSizeString(volume.getCapacityInfo().getFreeCapacityBytes());
            }
        };
        dataGrid.addResizableColumn(freeColumn, "Free", ColumnSizeConstants.SMALL_COL);

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
        dataGrid.addResizableColumn(usePercentColumn, "Use%", ColumnSizeConstants.SMALL_COL);

        // Is Full
        final Column<FsVolume, String> isFullColumn = new Column<FsVolume, String>(new TextCell()) {
            @Override
            public String getValue(final FsVolume volume) {
                return volume.getCapacityInfo().isFull()
                        ? "Yes"
                        : "No";
            }
        };
        dataGrid.addResizableColumn(isFullColumn, "Full", ColumnSizeConstants.SMALL_COL);

        // Usage Date.
        final Column<FsVolume, String> usageDateColumn = new Column<FsVolume, String>(new TextCell()) {
            @Override
            public String getValue(final FsVolume volume) {
                return dateTimeFormatter.format(volume.getVolumeState().getUpdateTimeMs());
            }
        };
        dataGrid.addResizableColumn(usageDateColumn, "Usage Date", ColumnSizeConstants.DATE_COL);
        dataGrid.addEndColumn(new EndColumn<>());
    }

    private String getSizeString(final OptionalLong optSizeBytes) {
        return optSizeBytes != null && optSizeBytes.isPresent()
                ? ModelStringUtil.formatIECByteSizeString(optSizeBytes.getAsLong())
                : "?";
    }

    public MultiSelectionModel<FsVolume> getSelectionModel() {
        return selectionModel;
    }

    public void refresh() {
        dataProvider.refresh();
    }
}
