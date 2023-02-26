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

package stroom.index.client.presenter;

import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.entity.shared.ExpressionCriteria;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolumeFields;
import stroom.index.shared.IndexVolumeResource;
import stroom.preferences.client.DateTimeFormatter;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.ResultPage;
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

        dataGrid = new MyDataGrid<>();
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);

        this.restFactory = restFactory;
        this.dateTimeFormatter = dateTimeFormatter;

        // Add a border to the list.
        getWidget().getElement().addClassName("stroom-border");

        initTableColumns();
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        // Node.
        final Column<IndexVolume, String> nodeColumn = new Column<IndexVolume, String>(new TextCell()) {
            @Override
            public String getValue(final IndexVolume volume) {
                return volume.getNodeName();
            }
        };
        dataGrid.addResizableColumn(nodeColumn, IndexVolumeFields.FIELD_NODE_NAME, 90);

        // Path.
        final Column<IndexVolume, String> volumeColumn = new Column<IndexVolume, String>(new TextCell()) {
            @Override
            public String getValue(final IndexVolume volume) {
                return volume.getPath();
            }
        };
        dataGrid.addResizableColumn(volumeColumn, IndexVolumeFields.FIELD_PATH, 300);

        // State.
        final Column<IndexVolume, String> streamStatusColumn = new Column<IndexVolume, String>(new TextCell()) {
            @Override
            public String getValue(final IndexVolume volume) {
                return volume.getState().getDisplayValue();
            }
        };
        dataGrid.addResizableColumn(streamStatusColumn, "State", 90);

        // Total.
        final Column<IndexVolume, String> totalColumn = new Column<IndexVolume, String>(new TextCell()) {
            @Override
            public String getValue(final IndexVolume volume) {
                return getSizeString(volume.getCapacityInfo().getTotalCapacityBytes());
            }
        };
        dataGrid.addResizableColumn(totalColumn, "Total", ColumnSizeConstants.SMALL_COL);

        // Limit.
        final Column<IndexVolume, String> limitColumn = new Column<IndexVolume, String>(new TextCell()) {
            @Override
            public String getValue(final IndexVolume volume) {
                if (volume.getBytesLimit() == null) {
                    return "";
                }
                return getSizeString(volume.getCapacityInfo().getCapacityLimitBytes());
            }
        };
        dataGrid.addResizableColumn(limitColumn, "Limit", ColumnSizeConstants.SMALL_COL);

        // Used.
        final Column<IndexVolume, String> usedColumn = new Column<IndexVolume, String>(new TextCell()) {
            @Override
            public String getValue(final IndexVolume volume) {
                return getSizeString(volume.getCapacityInfo().getCapacityUsedBytes());
            }
        };
        dataGrid.addResizableColumn(usedColumn, "Used", ColumnSizeConstants.SMALL_COL);

        // Free.
        final Column<IndexVolume, String> freeColumn = new Column<IndexVolume, String>(new TextCell()) {
            @Override
            public String getValue(final IndexVolume volume) {
                return getSizeString(volume.getCapacityInfo().getFreeCapacityBytes());
            }
        };
        dataGrid.addResizableColumn(freeColumn, "Free", ColumnSizeConstants.SMALL_COL);

        // Use%.
        final Column<IndexVolume, String> usePercentColumn = new Column<IndexVolume, String>(new TextCell()) {
            @Override
            public String getValue(final IndexVolume volume) {
                final OptionalDouble optUsedCapacityPercent = volume.getCapacityInfo().getUsedCapacityPercent();
                return optUsedCapacityPercent.isPresent()
                        ? ((long) optUsedCapacityPercent.getAsDouble()) + "%"
                        : "?";
            }
        };
        dataGrid.addResizableColumn(usePercentColumn, "Use%", ColumnSizeConstants.SMALL_COL);

        // Is Full
        final Column<IndexVolume, String> isFullColumn = new Column<IndexVolume, String>(new TextCell()) {
            @Override
            public String getValue(final IndexVolume volume) {
                return volume.getCapacityInfo().isFull()
                        ? "Yes"
                        : "No";
            }
        };
        dataGrid.addResizableColumn(isFullColumn, "Full", ColumnSizeConstants.SMALL_COL);

        // Usage Date.
        final Column<IndexVolume, String> usageDateColumn = new Column<IndexVolume, String>(new TextCell()) {
            @Override
            public String getValue(final IndexVolume volume) {
                return dateTimeFormatter.format(volume.getUpdateTimeMs());
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
                                final Consumer<Throwable> throwableConsumer) {
                CriteriaUtil.setRange(criteria, range);
                final Rest<ResultPage<IndexVolume>> rest = restFactory.create();
                rest
                        .onSuccess(result -> {
                            dataConsumer.accept(result);
                            if (consumer != null) {
                                consumer.accept(result);
                            }
                        })
                        .onFailure(throwableConsumer)
                        .call(INDEX_VOLUME_RESOURCE)
                        .find(criteria);
            }
        };
        dataProvider.addDataDisplay(dataGrid);
    }
}
