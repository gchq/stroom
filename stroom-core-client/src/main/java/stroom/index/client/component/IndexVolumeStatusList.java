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

package stroom.index.client.component;

import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolume.VolumeUseState;
import stroom.index.shared.IndexVolumeFields;
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

import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.function.Function;

/// An index volume group's volumes, a row each (its node, path and status, its total, limit, used and
/// free space, how full it is and when it was last written to), faded unless active; several can be
/// selected.
public class IndexVolumeStatusList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<IndexVolume> dataGrid;

    private final MultiSelectionModelImpl<IndexVolume> selectionModel;

    /// Makes an empty list, with no columns until [#addColumns(Function)].
    public IndexVolumeStatusList() {
        initWidget(BINDER.createAndBindUi(this));
        // Rows keyed by id, so a row whose values change is still the same row
        dataGrid.setKeyProvider(IndexVolume::getId);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
    }

    /// Adds the columns, once.
    ///
    /// @param dateTimeFormat Formats a time in milliseconds since the epoch, with how long ago it was.
    public void addColumns(final Function<Long, String> dateTimeFormat) {
        // Node.
        dataGrid.addResizableColumn(DataGridUtil.textWithTooltipColumnBuilder(IndexVolume::getNodeName)
                        .enabledWhen(this::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder(IndexVolumeFields.FIELD_NODE_NAME)
                        .withToolTip("The node that this index volume is owned by.")
                        .build(),
                90);

        // Path.
        dataGrid.addAutoResizableColumn(
                DataGridUtil.textWithTooltipColumnBuilder(IndexVolume::getPath, Function.identity())
                        .enabledWhen(this::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder(IndexVolumeFields.FIELD_PATH)
                        .withToolTip("The file system path to the volume.")
                        .build(), 200);

        // State.
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((IndexVolume indexVolume) -> indexVolume.getState().getDisplayValue())
                        .enabledWhen(this::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Status")
                        .withToolTip("The status of the volume. One of Active, Inactive or Closed.")
                        .build(),
                90);

        // Total.
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((IndexVolume indexVolume) ->
                                getSizeString(indexVolume.getCapacityInfo().getTotalCapacityBytes()))
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
                DataGridUtil.textColumnBuilder((IndexVolume indexVolume) ->
                                getSizeString(indexVolume.getCapacityInfo().getCapacityUsedBytes()))
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
                DataGridUtil.textColumnBuilder((IndexVolume indexVolume) ->
                                getSizeString(indexVolume.getCapacityInfo().getFreeCapacityBytes()))
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
                                (IndexVolume vol) -> !vol.getCapacityInfo().isFull(),
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
                DataGridUtil.textWithTooltipColumnBuilder((IndexVolume vol) ->
                                        dateTimeFormat.apply(vol.getUpdateTimeMs()),
                                Function.identity())
                        .enabledWhen(this::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Usage Date")
                        .withToolTip("The date/time this volume was last written to.")
                        .build(),
                ColumnSizeConstants.DATE_AND_DURATION_COL);
    }

    /// @param dataProvider Gives the list its rows, a page at a time.
    public void setDataProvider(final AbstractDataProvider<IndexVolume> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return The selection.
    public MultiSelectionModel<IndexVolume> getSelectionModel() {
        return selectionModel;
    }

    /// @return The toolbar beside the pager, for the screen's own buttons.
    public Toolbar getToolbar() {
        return pager.getToolbar();
    }

    private Number getUsePercentage(final IndexVolume volume) {
        final OptionalDouble optUsedPercent = volume.getCapacityInfo()
                .getUsedCapacityPercent();
        return optUsedPercent.isPresent()
                ? optUsedPercent.getAsDouble()
                : null;
    }

    private boolean isEnabled(final IndexVolume volume) {
        final VolumeUseState volumeUseState = NullSafe.get(volume, IndexVolume::getState);
        return VolumeUseState.ACTIVE == volumeUseState;
    }

    private String getLimitAsStr(final IndexVolume volume) {
        if (volume.getBytesLimit() == null) {
            return "";
        }
        // Special case for limit as it is user input data so need more precision
        return ModelStringUtil.formatIECByteSizeString(volume.getBytesLimit(),
                true,
                ModelStringUtil.DEFAULT_SIGNIFICANT_FIGURES);
    }

    private String getSizeString(final OptionalLong optSizeBytes) {
        return optSizeBytes != null && optSizeBytes.isPresent()
                ? ModelStringUtil.formatIECByteSizeString(optSizeBytes.getAsLong())
                : "?";
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, IndexVolumeStatusList> {

    }
}
