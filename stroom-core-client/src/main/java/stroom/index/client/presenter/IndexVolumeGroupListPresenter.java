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

import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.entity.shared.ExpressionCriteria;
import stroom.index.shared.IndexVolumeGroup;
import stroom.index.shared.IndexVolumeGroupResource;
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

import java.util.function.Consumer;

public class IndexVolumeGroupListPresenter extends MyPresenterWidget<PagerView> {

    private static final IndexVolumeGroupResource INDEX_VOLUME_GROUP_RESOURCE =
            GWT.create(IndexVolumeGroupResource.class);

    private final MyDataGrid<IndexVolumeGroup> dataGrid;
    private final MultiSelectionModelImpl<IndexVolumeGroup> selectionModel;
    private final RestDataProvider<IndexVolumeGroup, ResultPage<IndexVolumeGroup>> dataProvider;

    @Inject
    public IndexVolumeGroupListPresenter(final EventBus eventBus,
                                         final PagerView view,
                                         final RestFactory restFactory) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>(this);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);
        getWidget().getElement().addClassName("default-min-sizes");

        initTableColumns();

        final ExpressionCriteria criteria = new ExpressionCriteria();
        dataProvider = new RestDataProvider<IndexVolumeGroup, ResultPage<IndexVolumeGroup>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<IndexVolumeGroup>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                CriteriaUtil.setRange(criteria, range);
                restFactory
                        .create(INDEX_VOLUME_GROUP_RESOURCE)
                        .method(res -> res.find(criteria))
                        .onSuccess(dataConsumer)
                        .onFailure(errorHandler)
                        .taskMonitorFactory(view)
                        .exec();
            }
        };
        dataProvider.addDataDisplay(dataGrid);
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        // Name.
        final Column<IndexVolumeGroup, String> volumeColumn = new Column<IndexVolumeGroup, String>(new TextCell()) {
            @Override
            public String getValue(final IndexVolumeGroup volume) {
                return volume.getName();
            }
        };
        dataGrid.addResizableColumn(volumeColumn, "Name", 400);

        dataGrid.addEndColumn(new EndColumn<>());
    }

    public MultiSelectionModel<IndexVolumeGroup> getSelectionModel() {
        return selectionModel;
    }

    public void refresh() {
        dataProvider.refresh();
    }
}
