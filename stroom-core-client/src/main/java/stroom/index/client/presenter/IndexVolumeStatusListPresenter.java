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
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.entity.shared.ExpressionCriteria;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolumeResource;
import stroom.util.client.BorderUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.ResultPage;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.util.client.MultiSelectionModel;

import java.util.function.Consumer;

public class IndexVolumeStatusListPresenter extends MyPresenterWidget<DataGridView<IndexVolume>> {
    private static final IndexVolumeResource INDEX_VOLUME_RESOURCE = GWT.create(IndexVolumeResource.class);

    private final RestFactory restFactory;

    private Consumer<ResultPage<IndexVolume>> consumer;
    private RestDataProvider<IndexVolume, ResultPage<IndexVolume>> dataProvider;

    @Inject
    public IndexVolumeStatusListPresenter(final EventBus eventBus, final RestFactory restFactory) {
        super(eventBus, new DataGridViewImpl<>(true, true));
        this.restFactory = restFactory;

        // Add a border to the list.
        BorderUtil.addBorder(getWidget().getElement());

        initTableColumns();
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        // Path.
        final Column<IndexVolume, String> volumeColumn = new Column<IndexVolume, String>(new TextCell()) {
            @Override
            public String getValue(final IndexVolume volume) {
                return volume.getPath();
            }
        };
        getView().addResizableColumn(volumeColumn, "Path", 300);

        // Node.
        final Column<IndexVolume, String> nodeColumn = new Column<IndexVolume, String>(new TextCell()) {
            @Override
            public String getValue(final IndexVolume volume) {
                return volume.getNodeName();
            }
        };
        getView().addResizableColumn(nodeColumn, "Node", 90);

        // State.
        final Column<IndexVolume, String> streamStatusColumn = new Column<IndexVolume, String>(new TextCell()) {
            @Override
            public String getValue(final IndexVolume volume) {
                return volume.getState().getDisplayValue();
            }
        };
        getView().addResizableColumn(streamStatusColumn, "State", 90);

        // Total.
        final Column<IndexVolume, String> totalColumn = new Column<IndexVolume, String>(new TextCell()) {
            @Override
            public String getValue(final IndexVolume volume) {
                return getSizeString(volume.getBytesTotal());
            }
        };
        getView().addResizableColumn(totalColumn, "Total", ColumnSizeConstants.SMALL_COL);

        // Limit.
        final Column<IndexVolume, String> limitColumn = new Column<IndexVolume, String>(new TextCell()) {
            @Override
            public String getValue(final IndexVolume volume) {
                if (volume.getBytesLimit() == null) {
                    return "";
                }
                return getSizeString(volume.getBytesLimit());
            }
        };
        getView().addResizableColumn(limitColumn, "Limit", ColumnSizeConstants.SMALL_COL);

        // Used.
        final Column<IndexVolume, String> usedColumn = new Column<IndexVolume, String>(new TextCell()) {
            @Override
            public String getValue(final IndexVolume volume) {
                return getSizeString(volume.getBytesUsed());
            }
        };
        getView().addResizableColumn(usedColumn, "Used", ColumnSizeConstants.SMALL_COL);

        // Free.
        final Column<IndexVolume, String> freeColumn = new Column<IndexVolume, String>(new TextCell()) {
            @Override
            public String getValue(final IndexVolume volume) {
                return getSizeString(volume.getBytesFree());
            }
        };
        getView().addResizableColumn(freeColumn, "Free", ColumnSizeConstants.SMALL_COL);

        // Use%.
        final Column<IndexVolume, String> usePercentColumn = new Column<IndexVolume, String>(new TextCell()) {
            @Override
            public String getValue(final IndexVolume volume) {
                return getPercentString(volume.getPercentUsed());
            }
        };
        getView().addResizableColumn(usePercentColumn, "Use%", ColumnSizeConstants.SMALL_COL);

        // Usage Date.
        final Column<IndexVolume, String> usageDateColumn = new Column<IndexVolume, String>(new TextCell()) {
            @Override
            public String getValue(final IndexVolume volume) {
                return ClientDateUtil.toISOString(volume.getUpdateTimeMs());
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

    public MultiSelectionModel<IndexVolume> getSelectionModel() {
        return getView().getSelectionModel();
    }

    public void refresh() {
        this.consumer = null;
        dataProvider.refresh();
    }

    public void init(final ExpressionCriteria criteria, final Consumer<ResultPage<IndexVolume>> c) {
        this.consumer = c;
        final Rest<ResultPage<IndexVolume>> rest = restFactory.create();
        dataProvider = new RestDataProvider<IndexVolume, ResultPage<IndexVolume>>(getEventBus(), criteria.obtainPageRequest()) {
            @Override
            protected void exec(final Consumer<ResultPage<IndexVolume>> dataConsumer, final Consumer<Throwable> throwableConsumer) {
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
        dataProvider.addDataDisplay(getView().getDataDisplay());
    }
}
