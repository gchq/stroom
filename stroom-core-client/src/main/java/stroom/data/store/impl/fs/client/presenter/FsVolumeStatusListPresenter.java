/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.aws.s3.shared.S3ClientConfig;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.data.store.impl.fs.shared.FindFsVolumeCriteria;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolume.VolumeUseStatus;
import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.data.store.impl.fs.shared.FsVolumeResource;
import stroom.data.store.impl.fs.shared.FsVolumeType;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.preferences.client.DateTimeFormatter;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Selection;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.Function;

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

        dataGrid = new MyDataGrid<>(this);
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
                                    final RestErrorHandler errorHandler) {
                    CriteriaUtil.setRange(criteria, range);
                    restFactory
                            .create(FS_VOLUME_RESOURCE)
                            .method(resource -> resource.find(criteria))
                            .onSuccess(dataConsumer)
                            .onFailure(errorHandler)
                            .taskMonitorFactory(getView())
                            .exec();
                }
            };
            dataProvider.addDataDisplay(dataGrid);
        } else {
            dataProvider.refresh();
        }
    }

    private String getPath(final FsVolume volume) {
        if (volume == null) {
            return null;
        }
        if (FsVolumeType.S3.equals(volume.getVolumeType())) {
            return NullSafe.getOrElse(
                    volume.getS3ClientConfig(),
                    S3ClientConfig::getBucketName,
                    S3ClientConfig.DEFAULT_BUCKET_NAME);
        }

        return volume.getPath();
    }

    private boolean isEnabled(final FsVolume volume) {
        final VolumeUseStatus volumeUseStatus = NullSafe.get(volume, FsVolume::getStatus);
        return VolumeUseStatus.ACTIVE == volumeUseStatus;
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        // Path.
        dataGrid.addAutoResizableColumn(
                DataGridUtil.textWithTooltipColumnBuilder(this::getPath, Function.identity())
                        .enabledWhen(this::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Path")
                        .withToolTip("The file system path to the volume.")
                        .build(), 200);

        // Volume type.
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((FsVolume fsVolume) -> fsVolume.getVolumeType().getDisplayValue())
                        .enabledWhen(this::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Type")
                        .withToolTip("The type of file system in use. Either Standard or S3.")
                        .build(),
                90);

        // Status.
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((FsVolume fsVolume) -> fsVolume.getStatus().getDisplayValue())
                        .enabledWhen(this::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Status")
                        .withToolTip("The status of the volume. One of Active, Inactive or Closed.")
                        .build(),
                90);

        // Total.
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((FsVolume fsVolume) ->
                                getSizeString(fsVolume.getCapacityInfo().getTotalCapacityBytes()))
                        .enabledWhen(this::isEnabled)
                        .rightAligned()
                        .build(),
                DataGridUtil.headingBuilder("Total")
                        .rightAligned()
                        .withToolTip("The total size of the volume.")
                        .build(),
                ColumnSizeConstants.BYTE_SIZE_COL);

        // Limit.
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(this::getLimitAsStr)
                        .enabledWhen(this::isEnabled)
                        .rightAligned()
                        .build(),
                DataGridUtil.headingBuilder("Limit")
                        .rightAligned()
                        .withToolTip("The optional limit set on the volume. The volume will be considered full " +
                                     "when the limit is reached.")
                        .build(),
                ColumnSizeConstants.BYTE_SIZE_COL);

        // Used.
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((FsVolume fsVolume) ->
                                getSizeString(fsVolume.getCapacityInfo().getCapacityUsedBytes()))
                        .enabledWhen(this::isEnabled)
                        .rightAligned()
                        .build(),
                DataGridUtil.headingBuilder("Used")
                        .rightAligned()
                        .withToolTip("The amount of the volume that is in use.")
                        .build(),
                ColumnSizeConstants.BYTE_SIZE_COL);

        // Free.
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((FsVolume fsVolume) ->
                                getSizeString(fsVolume.getCapacityInfo().getFreeCapacityBytes()))
                        .enabledWhen(this::isEnabled)
                        .rightAligned()
                        .build(),
                DataGridUtil.headingBuilder("Free")
                        .rightAligned()
                        .withToolTip("The amount of the volume that is free. If a limit is set then only free " +
                                     "space up to the limit is considered.")
                        .build(),
                ColumnSizeConstants.BYTE_SIZE_COL);

        // Use%.
        dataGrid.addResizableColumn(
                DataGridUtil.percentBarColumnBuilder(
                                this::getUsePercentage,
                                DataGridUtil.DEFAULT_PCT_WARNING_THRESHOLD,
                                DataGridUtil.DEFAULT_PCT_DANGER_THRESHOLD)
                        .enabledWhen(this::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Use%")
                        .withToolTip("The percentage of the volume that is in use. If a limit is set then the " +
                                     "percentage is relative to the limit.")
                        .build(),
                ColumnSizeConstants.SMALL_COL);

        // Is Full
        dataGrid.addResizableColumn(DataGridUtil.redGreenTextColumnBuilder(
                                (FsVolume vol) -> !vol.getCapacityInfo().isFull(),
                                "No",
                                "Yes")
                        .enabledWhen(this::isEnabled)
                        .centerAligned()
                        .build(),
                DataGridUtil.headingBuilder("Full")
                        .withToolTip("Whether this volume is full or not.")
                        .centerAligned()
                        .build(),
                50);

        // Usage Date.
        dataGrid.addResizableColumn(
                DataGridUtil.textWithTooltipColumnBuilder((FsVolume vol) ->
                                        dateTimeFormatter.formatWithDuration(vol.getVolumeState().getUpdateTimeMs()),
                                Function.identity())
                        .enabledWhen(this::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Usage Date")
                        .withToolTip("The date/time this volume was last written to.")
                        .build(),
                ColumnSizeConstants.DATE_AND_DURATION_COL);

        dataGrid.addEndColumn(new EndColumn<>());
    }

    private Number getUsePercentage(final FsVolume volume) {
        final OptionalDouble optUsedPercent = volume.getCapacityInfo()
                .getUsedCapacityPercent();
        return optUsedPercent.isPresent()
                ? optUsedPercent.getAsDouble()
                : null;
    }

    public String getLimitAsStr(final FsVolume volume) {
        if (volume.getByteLimit() == null) {
            return "";
        }
        // Special case for limit as it is user input data so need more precision
        return ModelStringUtil.formatIECByteSizeString(volume.getByteLimit(),
                true,
                ModelStringUtil.DEFAULT_SIGNIFICANT_FIGURES);
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
