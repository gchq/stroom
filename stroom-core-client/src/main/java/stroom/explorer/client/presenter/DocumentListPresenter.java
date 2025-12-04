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

package stroom.explorer.client.presenter;

import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.PagerView;
import stroom.data.table.client.MyCellTable;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.entity.shared.ExpressionCriteria;
import stroom.explorer.shared.AdvancedDocumentFindRequest;
import stroom.explorer.shared.ExplorerResource;
import stroom.explorer.shared.FindResult;
import stroom.query.api.ExpressionOperator;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.Event;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public class DocumentListPresenter extends MyPresenterWidget<PagerView> {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);

    private final CellTable<FindResult> cellTable;
    private final RestDataProvider<FindResult, ResultPage<FindResult>> dataProvider;
    private final MultiSelectionModelImpl<FindResult> selectionModel;
    private final AdvancedDocumentFindRequest.Builder criteriaBuilder = new AdvancedDocumentFindRequest.Builder();
    private ExpressionCriteria currentQuery = criteriaBuilder.build();
    private ExpressionOperator lastFilter;
    private boolean initialised;
    private boolean focusText;
    private Consumer<ResultPage<FindResult>> currentResulthandler;
    private FindDocResultListHandler<FindResult> findResultListHandler = new FindDocResultListHandler<FindResult>() {
        @Override
        public void openDocument(final FindResult match) {

        }

        @Override
        public void focus() {

        }
    };

    @Inject
    public DocumentListPresenter(final EventBus eventBus,
                                 final PagerView view,
                                 final RestFactory restFactory) {
        super(eventBus, view);

        cellTable = new MyCellTable<FindResult>(100) {
            @Override
            protected void onBrowserEvent2(final Event event) {
                super.onBrowserEvent2(event);
                if (event.getTypeInt() == Event.ONKEYDOWN && event.getKeyCode() == KeyCodes.KEY_UP) {
                    if (cellTable.getKeyboardSelectedRow() == 0) {
                        findResultListHandler.focus();
                    }
                }
            }
        };
        cellTable.addStyleName("FindCellTable");

        selectionModel = new MultiSelectionModelImpl<>();
        final SelectionEventManager<FindResult> selectionEventManager = new SelectionEventManager<>(
                cellTable,
                selectionModel,
                doc -> findResultListHandler.openDocument(doc),
                null);
        cellTable.setSelectionModel(selectionModel, selectionEventManager);

        dataProvider = new RestDataProvider<FindResult, ResultPage<FindResult>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<FindResult>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                final PageRequest pageRequest = new PageRequest(range.getStart(), range.getLength());
                criteriaBuilder.pageRequest(pageRequest);

                final AdvancedDocumentFindRequest request = criteriaBuilder.build();
                final ExpressionOperator filter = request.getExpression();
                final boolean filterChange = !Objects.equals(lastFilter, filter);
                lastFilter = filter;

                restFactory
                        .create(EXPLORER_RESOURCE)
                        .method(res -> res.advancedFind(request))
                        .onSuccess(resultPage -> {
                            if (currentResulthandler != null) {
                                currentResulthandler.accept(resultPage);
                            }
                            if (resultPage.getPageStart() != cellTable.getPageStart()) {
                                cellTable.setPageStart(resultPage.getPageStart());
                            }
                            dataConsumer.accept(resultPage);

                            if (filterChange) {
                                if (!resultPage.isEmpty()) {
                                    selectionModel.setSelected(resultPage.getValues().get(0));
                                } else {
                                    selectionModel.clear();
                                }
                            }

                            resetFocus();
                        })
                        .onFailure(errorHandler)
                        .taskMonitorFactory(view)
                        .exec();
            }
        };

        final Column<FindResult, FindResult> column = new Column<FindResult, FindResult>(new FindResultCell()) {
            @Override
            public FindResult getValue(final FindResult object) {
                return object;
            }
        };
        cellTable.addColumn(column);
        view.setDataWidget(cellTable);
    }

    private void resetFocus() {
        if (focusText) {
            focusText = false;
            findResultListHandler.focus();
        }
    }

    public void refresh() {
        if (!initialised) {
            initialised = true;
            dataProvider.addDataDisplay(cellTable);
        } else {
            dataProvider.refresh();
        }
    }

    public void setFindResultListHandler(final FindDocResultListHandler findResultListHandler) {
        this.findResultListHandler = findResultListHandler;
    }

    public void setExpression(final ExpressionOperator expression) {
        criteriaBuilder.expression(expression);
    }

    public void setRequiredPermissions(final Set<DocumentPermission> requiredPermissions) {
        criteriaBuilder.requiredPermissions(requiredPermissions);
    }

    public FindResult getSelected() {
        return selectionModel.getSelected();
    }

    public void setKeyboardSelectedRow(final int row, final boolean stealFocus) {
        cellTable.setKeyboardSelectedRow(row, stealFocus);
    }

    public void setFocusText(final boolean focusText) {
        this.focusText = focusText;
    }

    public MultiSelectionModelImpl<FindResult> getSelectionModel() {
        return selectionModel;
    }

    public void setCurrentResulthandler(final Consumer<ResultPage<FindResult>> currentResulthandler) {
        this.currentResulthandler = currentResulthandler;
    }
}
