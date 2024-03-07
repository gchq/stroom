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
import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.data.store.impl.fs.shared.FsVolumeGroupResource;
import stroom.dispatch.client.RestFactory;
import stroom.entity.shared.ExpressionCriteria;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.ResultPage;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.function.Consumer;

public class FsVolumeGroupListPresenter extends MyPresenterWidget<PagerView> {

    private static final FsVolumeGroupResource FS_VOLUME_GROUP_RESOURCE =
            GWT.create(FsVolumeGroupResource.class);

    private final MyDataGrid<FsVolumeGroup> dataGrid;
    private final MultiSelectionModelImpl<FsVolumeGroup> selectionModel;
    private final RestDataProvider<FsVolumeGroup, ResultPage<FsVolumeGroup>> dataProvider;

    @Inject
    public FsVolumeGroupListPresenter(final EventBus eventBus,
                                      final PagerView view,
                                      final RestFactory restFactory) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>();
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);
        getWidget().getElement().addClassName("default-min-sizes");

        initTableColumns();

        final ExpressionCriteria criteria = new ExpressionCriteria();
        dataProvider = new RestDataProvider<FsVolumeGroup, ResultPage<FsVolumeGroup>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<FsVolumeGroup>> dataConsumer,
                                final Consumer<Throwable> throwableConsumer) {
                CriteriaUtil.setRange(criteria, range);

                restFactory.builder()
                        .forResultPageOf(FsVolumeGroup.class)
                        .onSuccess(dataConsumer)
                        .onFailure(throwableConsumer)
                        .call(FS_VOLUME_GROUP_RESOURCE)
                        .find(criteria);
            }
        };
        dataProvider.addDataDisplay(dataGrid);
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        // Name.
        dataGrid.addResizableColumn(
                DataGridUtil.copyTextColumnBuilder(FsVolumeGroup::getName).build(),
                "Name",
                400);
        // UUID
        dataGrid.addResizableColumn(
                DataGridUtil.copyTextColumnBuilder(FsVolumeGroup::getUuid).build(),
                "UUID",
                ColumnSizeConstants.UUID_COL);
        // Is Default
        dataGrid.addColumn(
                DataGridUtil.readOnlyTickBoxColumnBuilder(FsVolumeGroup::isDefaultVolume).build(),
                "Default Group",
                ColumnSizeConstants.CHECKBOX_COL);
        DataGridUtil.addEndColumn(dataGrid);
    }

    public MultiSelectionModel<FsVolumeGroup> getSelectionModel() {
        return selectionModel;
    }

    public void refresh() {
        dataProvider.refresh();
    }
}
