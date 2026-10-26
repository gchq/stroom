/*
 * Copyright 2026 Crown Copyright
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

package stroom.data.store.impl.fs.client.component;

import stroom.aws.s3.shared.S3ClientConfig;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolume.VolumeUseStatus;
import stroom.data.store.impl.fs.shared.FsVolumeType;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.function.Function;

/// A data volume group's volumes, a row each (its path, type and status, its total, limit, used and
/// free space, how full it is and when it was last written to), faded unless active; several can be
/// selected.
public class FsVolumeStatusList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<FsVolume> dataGrid;

    private final MultiSelectionModelImpl<FsVolume> selectionModel;

    /// Makes an empty list, with no columns until [#addColumns(Function)].
    public FsVolumeStatusList() {
        initWidget(BINDER.createAndBindUi(this));
        // Rows keyed by id, so a row whose values change is still the same row
        dataGrid.setKeyProvider(FsVolume::getId);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
    }

    /// Adds the columns, once.
    ///
    /// @param dateTimeFormat Formats a time in milliseconds since the epoch, with how long ago it was.
    public void addColumns(final Function<Long, String> dateTimeFormat) {
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
                        .withToolTip("The type of volume, either a Standard file system or another " +
                                     "file/object store like S3.")
                        .build(),
                150);

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
                        .withToolTip("The total size of the volume. Not supported for S3 based volumes.")
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
                                     "when the limit is reached. Not supported for S3 based volumes.")
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
                        .withToolTip("The amount of the volume that is in use. Not supported for S3 based volumes.")
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
                                     "space up to the limit is considered. Not supported for S3 based volumes.")
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
                                     "percentage is relative to the limit. Not supported for S3 based volumes.")
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
                        .withToolTip("Whether this volume is full or not. Not supported for S3 based volumes.")
                        .centerAligned()
                        .build(),
                50);

        // Usage Date.
        dataGrid.addResizableColumn(
                DataGridUtil.textWithTooltipColumnBuilder((FsVolume vol) ->
                                        dateTimeFormat.apply(vol.getVolumeState().getUpdateTimeMs()),
                                Function.identity())
                        .enabledWhen(this::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Usage Date")
                        .withToolTip(
                                "The date/time this volume was last written to. Not supported for S3 based volumes.")
                        .build(),
                ColumnSizeConstants.DATE_AND_DURATION_COL);
    }

    /// @param dataProvider Gives the list its rows, a page at a time.
    public void setDataProvider(final AbstractDataProvider<FsVolume> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return The selection.
    public MultiSelectionModel<FsVolume> getSelectionModel() {
        return selectionModel;
    }

    /// @return The toolbar beside the pager, for the screen's own buttons.
    public Toolbar getToolbar() {
        return pager.getToolbar();
    }

    private String getPath(final FsVolume volume) {
        if (volume == null) {
            return null;
        }
        if (FsVolumeType.isS3VolumeType(volume.getVolumeType()) && volume.getS3ClientConfig() != null) {
            final S3ClientConfig s3ClientConfig = volume.getS3ClientConfig();
            final List<String> parts = new ArrayList<>();
            NullSafe.consumeNonBlankString(
                    s3ClientConfig.getRegion(),
                    region -> "region: " + region,
                    parts::add);
            NullSafe.consumeNonBlankString(
                    s3ClientConfig.getBucketName(),
                    region -> "bucket: " + region,
                    parts::add);
            NullSafe.consumeNonBlankString(
                    s3ClientConfig.getKeyPattern(),
                    region -> "key: " + region,
                    parts::add);
            return String.join(", ", parts);
        } else {
            return volume.getPath();
        }
    }

    private boolean isEnabled(final FsVolume volume) {
        final VolumeUseStatus volumeUseStatus = NullSafe.get(volume, FsVolume::getStatus);
        return VolumeUseStatus.ACTIVE == volumeUseStatus;
    }

    private Number getUsePercentage(final FsVolume volume) {
        final OptionalDouble optUsedPercent = volume.getCapacityInfo()
                .getUsedCapacityPercent();
        return optUsedPercent.isPresent()
                ? optUsedPercent.getAsDouble()
                : null;
    }

    private String getLimitAsStr(final FsVolume volume) {
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


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, FsVolumeStatusList> {

    }
}
