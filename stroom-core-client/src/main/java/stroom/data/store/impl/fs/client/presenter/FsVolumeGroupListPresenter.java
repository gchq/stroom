/*
 * Copyright 2020 Crown Copyright
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

import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.data.store.impl.fs.shared.FsVolumeGroupResource;
import stroom.data.store.impl.fs.shared.FsVolumeGroupRow;
import stroom.data.store.impl.fs.shared.FsVolumeType;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.entity.shared.ExpressionCriteria;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.function.Consumer;
import java.util.stream.Collectors;

public class FsVolumeGroupListPresenter extends MyPresenterWidget<PagerView> {

    private static final FsVolumeGroupResource FS_VOLUME_GROUP_RESOURCE =
            GWT.create(FsVolumeGroupResource.class);

    private final MyDataGrid<FsVolumeGroupRow> dataGrid;
    private final MultiSelectionModelImpl<FsVolumeGroupRow> selectionModel;
    private final RestDataProvider<FsVolumeGroupRow, ResultPage<FsVolumeGroupRow>> dataProvider;

    @Inject
    public FsVolumeGroupListPresenter(final EventBus eventBus,
                                      final PagerView view,
                                      final RestFactory restFactory) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>(this);
        dataGrid.setTableName("FS Volume Groups");
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);
        getWidget().getElement().addClassName("default-min-sizes");

        initTableColumns();

        final ExpressionCriteria criteria = new ExpressionCriteria();
        dataProvider = new RestDataProvider<FsVolumeGroupRow, ResultPage<FsVolumeGroupRow>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<FsVolumeGroupRow>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                CriteriaUtil.setRange(criteria, range);
                restFactory
                        .create(FS_VOLUME_GROUP_RESOURCE)
                        .method(resource ->
                                resource.findExtended(criteria))
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
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(
                                (FsVolumeGroupRow row1) -> row1.getGroup().getName())
                        .build(),
                DataGridUtil.headingBuilder("Group Name")
                        .withToolTip("The name of the Volume Group.")
                        .build(),
                400);

        // Types
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(this::getVolumeTypesStr)
                        .build(),
                DataGridUtil.headingBuilder("Volume Type(s)")
                        .withToolTip("The types of Volumes inside this Volume Group.")
                        .build(),
                250);

        // Count
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(
                                (FsVolumeGroupRow row) -> Integer.toString(row.getVolumeCount()))
                        .rightAligned()
                        .build(),
                DataGridUtil.headingBuilder("Volume Count")
                        .withToolTip("The number of Volumes inside this Volume Group.")
                        .build(),
                150);
    }

    private String getVolumeTypesStr(final FsVolumeGroupRow row) {
        return NullSafe.getOrElse(
                row,
                FsVolumeGroupRow::getVolumeTypes,
                types -> types.stream()
                        .map(FsVolumeType::getDisplayValue)
                        .collect(Collectors.joining(", ")),
                "");
    }

    public MultiSelectionModel<FsVolumeGroupRow> getSelectionModel() {
        return selectionModel;
    }

    public void refresh() {
        dataProvider.refresh();
    }
}
