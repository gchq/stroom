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

import stroom.alert.client.event.AlertEvent;
import stroom.cell.info.client.ActionCell;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestError;
import stroom.dispatch.client.RestFactory;
import stroom.entity.shared.ExpressionCriteria;
import stroom.index.shared.IndexVolumeGroup;
import stroom.index.shared.IndexVolumeGroupResource;
import stroom.svg.shared.SvgImage;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.ResultPage;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuBuilder;
import stroom.widget.menu.client.presenter.MenuPresenter;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class IndexVolumeGroupListPresenter extends MyPresenterWidget<PagerView> {

    private static final IndexVolumeGroupResource INDEX_VOLUME_GROUP_RESOURCE =
            GWT.create(IndexVolumeGroupResource.class);

    private final RestFactory restFactory;
    private final MyDataGrid<IndexVolumeGroup> dataGrid;
    private final MultiSelectionModelImpl<IndexVolumeGroup> selectionModel;
    private final RestDataProvider<IndexVolumeGroup, ResultPage<IndexVolumeGroup>> dataProvider;
    private final MenuPresenter menuPresenter;

    private Consumer<List<IndexVolumeGroup>> deleteHandler = null;
    private Consumer<IndexVolumeGroup> editHandler;

    @Inject
    public IndexVolumeGroupListPresenter(final EventBus eventBus,
                                         final PagerView view,
                                         final RestFactory restFactory,
                                         final MenuPresenter menuPresenter) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.menuPresenter = menuPresenter;
        this.dataGrid = new MyDataGrid<>();
        this.selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);
        getWidget().getElement().addClassName("default-min-sizes");

        initTableColumns();

        final ExpressionCriteria criteria = new ExpressionCriteria();
        dataProvider = new RestDataProvider<IndexVolumeGroup, ResultPage<IndexVolumeGroup>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<IndexVolumeGroup>> dataConsumer,
                                final Consumer<RestError> errorConsumer) {
                CriteriaUtil.setRange(criteria, range);
                restFactory
                        .create(INDEX_VOLUME_GROUP_RESOURCE)
                        .method(res -> res.find(criteria))
                        .onSuccess(dataConsumer)
                        .onFailure(errorConsumer)
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
                DataGridUtil.copyTextColumnBuilder(IndexVolumeGroup::getName)
                        .build(),
                DataGridUtil.headingBuilder("Name")
                        .withToolTip("The name of the volume group.")
                        .build(),
                400);

        // UUID
        dataGrid.addResizableColumn(
                DataGridUtil.copyTextColumnBuilder(IndexVolumeGroup::getUuid)
                        .build(),
                DataGridUtil.headingBuilder("UUID")
                        .withToolTip("The unique identifier for the volume group.")
                        .build(),
                ColumnSizeConstants.UUID_COL);

        // Is Default
        final Column<IndexVolumeGroup, String> defaultColumn = DataGridUtil.textColumnBuilder(
                        IndexVolumeGroup::isDefaultVolume,
                        isDefault -> isDefault
                                ? "Yes"
                                : null)
                .centerAligned()
                .build();

        dataGrid.addColumn(
                defaultColumn,
                DataGridUtil.headingBuilder("Default Group")
                        .withToolTip("If set, this group will be used when no group has been specified.")
                        .build(),
                100);  // To allow for heading width

        // Action
        addActionButtonColumn();

        dataGrid.addEndColumn(new EndColumn<>());
    }

    private void addActionButtonColumn() {
        final ActionCell<IndexVolumeGroup> actionCell = new ActionCell<>(this::showActionMenu);
        final Column<IndexVolumeGroup, IndexVolumeGroup> actionColumn = DataGridUtil.columnBuilder(
                Function.identity(),
                () -> actionCell
        ).build();
        dataGrid.addColumn(actionColumn, "", ColumnSizeConstants.ACTION_COL);
    }

    private void showActionMenu(final IndexVolumeGroup indexVolumeGroup, final NativeEvent event) {

        final PopupPosition popupPosition = new PopupPosition(event.getClientX() + 10, event.getClientY());
        menuPresenter.setData(buildActionMenu(indexVolumeGroup));
        ShowPopupEvent.builder(menuPresenter)
                .popupType(PopupType.POPUP)
                .popupPosition(popupPosition)
                .fire();
    }

    private List<Item> buildActionMenu(final IndexVolumeGroup indexVolumeGroup) {

        final MenuBuilder builder = MenuBuilder.builder()
                .withIconMenuItem(itemBuilder -> itemBuilder
                        .icon(SvgImage.EDIT)
                        .text("Edit")
                        .command(() -> onEditVolGrp(indexVolumeGroup)));

        if (!indexVolumeGroup.isDefaultVolume()) {
            builder
                    .withIconMenuItem(itemBuilder -> itemBuilder
                            .icon(SvgImage.EDIT)
                            .text("Make Default")
                            .command(() -> onMakeDefault(indexVolumeGroup)))
                    .withIconMenuItem(itemBuilder -> itemBuilder
                            .icon(SvgImage.DELETE)
                            .text("Delete")
                            .command(() -> onDeleteVolGrp(indexVolumeGroup)));
        }
        return builder.build();
    }

    private void onDeleteVolGrp(final IndexVolumeGroup indexVolumeGroup) {
        if (deleteHandler != null && indexVolumeGroup != null) {
            deleteHandler.accept(GwtNullSafe.asList(indexVolumeGroup));
        }
    }

    private void onEditVolGrp(final IndexVolumeGroup indexVolumeGroup) {
        if (editHandler != null && indexVolumeGroup != null) {
            editHandler.accept(indexVolumeGroup);
        }

    }

    private void onMakeDefault(final IndexVolumeGroup indexVolumeGroup) {
        indexVolumeGroup.setDefaultVolume(true);
        restFactory
                .create(INDEX_VOLUME_GROUP_RESOURCE)
                .method(res -> res.update(indexVolumeGroup.getId(), indexVolumeGroup))
                .onSuccess(indexVolumeGroup2 -> {
                    refresh();
                })
                .onFailure(restError -> {
                    AlertEvent.fireError(this, restError.getMessage(), null);
                })
                .exec();
    }

    public MultiSelectionModel<IndexVolumeGroup> getSelectionModel() {
        return selectionModel;
    }

    public void refresh() {
        dataProvider.refresh();
    }

    public void setDeleteHandler(final Consumer<List<IndexVolumeGroup>> deleteHandler) {
        this.deleteHandler = deleteHandler;
    }

    public void setEditHandler(final Consumer<IndexVolumeGroup> editHandler) {
        this.editHandler = editHandler;
    }
}
