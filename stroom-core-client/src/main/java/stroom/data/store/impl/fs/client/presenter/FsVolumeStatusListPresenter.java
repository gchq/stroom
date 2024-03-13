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
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.data.store.impl.fs.shared.FindFsVolumeCriteria;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolume.VolumeUseStatus;
import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.data.store.impl.fs.shared.FsVolumeResource;
import stroom.data.store.impl.fs.shared.FsVolumeType;
import stroom.data.store.impl.fs.shared.S3ClientConfig;
import stroom.dispatch.client.RestFactory;
import stroom.preferences.client.DateTimeFormatter;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Selection;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

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
                                    final Consumer<Throwable> throwableConsumer) {
                    CriteriaUtil.setRange(criteria, range);

                    restFactory.builder()
                            .forResultPageOf(FsVolume.class)
                            .onSuccess(dataConsumer)
                            .onFailure(throwableConsumer)
                            .call(FS_VOLUME_RESOURCE)
                            .find(criteria);
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
        dataGrid.addAutoResizableColumn(
                DataGridUtil.copyTextColumnBuilder(FsVolumeStatusListPresenter::getVolumePath)
                        .build(), "Path", 400);

        // Volume type.
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(FsVolume::getVolumeType, FsVolumeType::getDisplayValue)
                        .build(), "Type", 90);
        // Status.
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(FsVolume::getStatus, VolumeUseStatus::getDisplayValue)
                        .build(), "Status", 90);

        // Total.
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((FsVolume vol) ->
                                        vol.getCapacityInfo().getTotalCapacityBytes(),
                                DataGridUtil::formatAsIECByteString)
                        .build(), "Total", ColumnSizeConstants.SMALL_COL);
        // Limit.
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(FsVolume::getByteLimit, limit ->
                                ModelStringUtil.formatIECByteSizeString(
                                        limit,
                                        true,
                                        ModelStringUtil.DEFAULT_SIGNIFICANT_FIGURES))
                        .build(), "Limit", ColumnSizeConstants.SMALL_COL);
        // Used.
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((FsVolume vol) ->
                                        vol.getCapacityInfo().getCapacityUsedBytes(),
                                DataGridUtil::formatAsIECByteString)
                        .build(), "Used", ColumnSizeConstants.SMALL_COL);
        // Free.
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((FsVolume vol) ->
                                        vol.getCapacityInfo().getFreeCapacityBytes(),
                                DataGridUtil::formatAsIECByteString)
                        .build(), "Free", ColumnSizeConstants.SMALL_COL);
        // Use%.
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(
                                (FsVolume vol) ->
                                        vol.getCapacityInfo().getUsedCapacityPercent(),
                                DataGridUtil::formatPercentage)
                        .build(), "Use %", ColumnSizeConstants.SMALL_COL);
        // Is Full
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((FsVolume vol) ->
                                vol.getCapacityInfo().isFull(), DataGridUtil::formatAsYesNo)
                        .build(), "Full", ColumnSizeConstants.SMALL_COL);

        // Usage Date.
        dataGrid.addResizableColumn(
                DataGridUtil.copyTextColumnBuilder(
                                (FsVolume vol) ->
                                        vol.getVolumeState().getUpdateTimeMs(),
                                dateTimeFormatter::format)
                        .build(), "Usage Date", ColumnSizeConstants.DATE_COL);

        DataGridUtil.addEndColumn(dataGrid);
    }

    private static String getVolumePath(final FsVolume volume) {
        if (FsVolumeType.S3 == volume.getVolumeType()) {
            return GwtNullSafe.getOrElse(
                    volume.getS3ClientConfig(),
                    S3ClientConfig::getBucketName,
                    S3ClientConfig.DEFAULT_BUCKET_NAME);
        } else {
            return volume.getPath();
        }
    }

//    private String getSizeString(final OptionalLong optSizeBytes) {
//        return optSizeBytes != null && optSizeBytes.isPresent()
//                ? ModelStringUtil.formatIECByteSizeString(optSizeBytes.getAsLong())
//                : "?";
//    }

    public MultiSelectionModel<FsVolume> getSelectionModel() {
        return selectionModel;
    }

    public void refresh() {
        dataProvider.refresh();
    }
}
