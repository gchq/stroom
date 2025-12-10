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

package stroom.index.client.presenter;

import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.entity.shared.ExpressionCriteria;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolume.VolumeUseState;
import stroom.index.shared.IndexVolumeFields;
import stroom.index.shared.IndexVolumeResource;
import stroom.preferences.client.DateTimeFormatter;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
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

public class IndexVolumeStatusListPresenter extends MyPresenterWidget<PagerView> {

    private static final IndexVolumeResource INDEX_VOLUME_RESOURCE = GWT.create(IndexVolumeResource.class);

    private final MyDataGrid<IndexVolume> dataGrid;
    private final MultiSelectionModelImpl<IndexVolume> selectionModel;
    private final RestFactory restFactory;
    private final DateTimeFormatter dateTimeFormatter;

    private Consumer<ResultPage<IndexVolume>> consumer;
    private RestDataProvider<IndexVolume, ResultPage<IndexVolume>> dataProvider;

    @Inject
    public IndexVolumeStatusListPresenter(final EventBus eventBus,
                                          final PagerView view,
                                          final RestFactory restFactory,
                                          final DateTimeFormatter dateTimeFormatter) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>(this);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);

        this.restFactory = restFactory;
        this.dateTimeFormatter = dateTimeFormatter;

        initTableColumns();
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
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
                                        dateTimeFormatter.formatWithDuration(vol.getUpdateTimeMs()),
                                Function.identity())
                        .enabledWhen(this::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Usage Date")
                        .withToolTip("The date/time this volume was last written to.")
                        .build(),
                ColumnSizeConstants.DATE_AND_DURATION_COL);

        dataGrid.addEndColumn(new EndColumn<>());
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

    public String getLimitAsStr(final IndexVolume volume) {
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

    public MultiSelectionModel<IndexVolume> getSelectionModel() {
        return selectionModel;
    }

    public void refresh() {
        this.consumer = null;
        dataProvider.refresh();
    }

    public void init(final ExpressionCriteria criteria, final Consumer<ResultPage<IndexVolume>> c) {
        this.consumer = c;
        dataProvider = new RestDataProvider<IndexVolume, ResultPage<IndexVolume>>(getEventBus()) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<IndexVolume>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                CriteriaUtil.setRange(criteria, range);
                restFactory
                        .create(INDEX_VOLUME_RESOURCE)
                        .method(res -> res.find(criteria))
                        .onSuccess(result -> {
                            dataConsumer.accept(result);
                            if (consumer != null) {
                                consumer.accept(result);
                            }
                        })
                        .onFailure(errorHandler)
                        .taskMonitorFactory(getView())
                        .exec();
            }
        };
        dataProvider.addDataDisplay(dataGrid);
    }

    public TaskMonitorFactory getTaskListener() {
        return getView();
    }
}
