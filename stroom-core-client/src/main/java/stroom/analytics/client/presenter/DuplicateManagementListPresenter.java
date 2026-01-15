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

package stroom.analytics.client.presenter;

import stroom.alert.client.event.ConfirmEvent;
import stroom.analytics.shared.DeleteDuplicateCheckRequest;
import stroom.analytics.shared.DuplicateCheckResource;
import stroom.analytics.shared.DuplicateCheckRow;
import stroom.analytics.shared.FindDuplicateCheckCriteria;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class DuplicateManagementListPresenter
        extends MyPresenterWidget<PagerView> {

    private static final DuplicateCheckResource DUPLICATE_CHECK_RESOURCE =
            GWT.create(DuplicateCheckResource.class);

    private final MyDataGrid<DuplicateCheckRow> dataGrid;
    private final MultiSelectionModelImpl<DuplicateCheckRow> selectionModel;
    private final RestFactory restFactory;
    private final ButtonView deleteButton;
    private final FindDuplicateCheckCriteria criteria;
    private final RestDataProvider<DuplicateCheckRow, ResultPage<DuplicateCheckRow>> dataProvider;
    private boolean initialised;
    private final List<Column<DuplicateCheckRow, ?>> columns = new ArrayList<>();

    @Inject
    public DuplicateManagementListPresenter(final EventBus eventBus,
                                            final PagerView view,
                                            final RestFactory restFactory) {
        super(eventBus, view);
        this.restFactory = restFactory;

        dataGrid = new MyDataGrid<>(this);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);
        deleteButton = view.addButton(SvgPresets.DELETE);

        criteria = new FindDuplicateCheckCriteria();
        //noinspection Convert2Diamond // cos GWT
        dataProvider = new RestDataProvider<DuplicateCheckRow, ResultPage<DuplicateCheckRow>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<DuplicateCheckRow>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                CriteriaUtil.setRange(criteria, range);
                if (criteria.getAnalyticDocUuid() != null) {
                    CriteriaUtil.setRange(criteria, range);
                    restFactory
                            .create(DUPLICATE_CHECK_RESOURCE)
                            .method(res -> res.find(criteria))
                            .onSuccess(result -> {
                                updateColumns(result.getColumnNames());
                                dataConsumer.accept(result.getResultPage());
                            })
                            .onFailure(errorHandler)
                            .taskMonitorFactory(getView())
                            .exec();
                }
            }
        };
    }

    private void updateColumns(final List<String> columnNames) {
        if (columnNames.size() != columns.size()) {
            columns.forEach(dataGrid::removeColumn);
            columns.clear();

            for (int i = 0; i < columnNames.size(); i++) {
                final int pos = i;
                final String columnName = columnNames.get(pos);
                //noinspection Convert2Diamond // cos GWT
                final Column<DuplicateCheckRow, String> column = new Column<DuplicateCheckRow, String>(new TextCell()) {
                    @Override
                    public String getValue(final DuplicateCheckRow duplicateCheckRow) {
                        if (duplicateCheckRow != null &&
                            duplicateCheckRow.getValues() != null &&
                            duplicateCheckRow.getValues().size() > pos) {
                            return duplicateCheckRow.getValues().get(pos);
                        }
                        return null;
                    }
                };
                dataGrid.addResizableColumn(column, columnName, 200);
                columns.add(column);
            }

            dataGrid.addEndColumn(new EndColumn<>());
        }
    }

    private void enableButtons() {
        final boolean enabled = NullSafe.hasItems(selectionModel.getSelectedItems());
        deleteButton.setEnabled(enabled);
    }

    @Override
    protected void onBind() {
        registerHandler(selectionModel.addSelectionHandler(event -> {
            enableButtons();
        }));
        if (deleteButton != null) {
            registerHandler(deleteButton.addClickHandler(event -> {
                if (MouseUtil.isPrimary(event)) {
                    onDelete();
                }
            }));
        }

        super.onBind();
    }

    protected void read(final DocRef docRef) {
        criteria.setAnalyticDocUuid(docRef.getUuid());
        refresh();
    }

    public void refresh() {
        if (!initialised) {
            initialised = true;
            dataProvider.addDataDisplay(dataGrid);
        } else {
            dataProvider.refresh();
        }
    }

    private void onDelete() {
        final List<DuplicateCheckRow> selected = selectionModel.getSelectedItems();
        if (NullSafe.hasItems(selected)) {
            ConfirmEvent.fire(this, "Are you sure you want to delete the selected row" +
                                    (selected.size() > 1
                                            ? "s"
                                            : "") +
                                    "?",
                    result -> {
                        if (result) {
                            final DeleteDuplicateCheckRequest request =
                                    new DeleteDuplicateCheckRequest(criteria.getAnalyticDocUuid(), selected);
                            restFactory
                                    .create(DUPLICATE_CHECK_RESOURCE)
                                    .method(res -> res.delete(request))
                                    .onSuccess(r -> refresh())
                                    .taskMonitorFactory(getView())
                                    .exec();
                        }
                    });
        }
    }
}
