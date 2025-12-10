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
import stroom.explorer.shared.DocumentFindRequest;
import stroom.explorer.shared.ExplorerResource;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.explorer.shared.FindResult;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;
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
import java.util.function.Consumer;

public class FindDocResultListPresenter extends MyPresenterWidget<PagerView> {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);

    private final CellTable<FindResult> cellTable;
    private final RestDataProvider<FindResult, ResultPage<FindResult>> dataProvider;
    private final MultiSelectionModelImpl<FindResult> selectionModel;
    private final ExplorerTreeFilter.Builder explorerTreeFilterBuilder = ExplorerTreeFilter.builder();
    private DocumentFindRequest currentQuery = new DocumentFindRequest(
            PageRequest.createDefault(),
            null,
            null);
    private ExplorerTreeFilter lastFilter;
    private boolean initialised;
    private boolean focusText;
    private FindDocResultListHandler<FindResult> findResultListHandler = new FindDocResultListHandler<FindResult>() {
        @Override
        public void openDocument(final FindResult match) {

        }

        @Override
        public void focus() {

        }
    };

    @Inject
    public FindDocResultListPresenter(final EventBus eventBus,
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

        explorerTreeFilterBuilder.requiredPermissions(DocumentPermission.VIEW);

        dataProvider = new RestDataProvider<FindResult, ResultPage<FindResult>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<FindResult>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                final PageRequest pageRequest = new PageRequest(range.getStart(), range.getLength());
                final ExplorerTreeFilter filter = explorerTreeFilterBuilder.build();
                final boolean filterChange = !Objects.equals(lastFilter, filter);
                lastFilter = filter;

                currentQuery = new DocumentFindRequest(
                        pageRequest,
                        currentQuery.getSortList(),
                        filter);

                if ((filter.getRecentItems() == null && NullSafe.isBlankString(filter.getNameFilter())) ||
                    (filter.getRecentItems() != null && filter.getRecentItems().size() == 0)) {
                    final ResultPage<FindResult> resultPage = ResultPage.empty();
                    if (resultPage.getPageStart() != cellTable.getPageStart()) {
                        cellTable.setPageStart(resultPage.getPageStart());
                    }
                    dataConsumer.accept(resultPage);
                    selectionModel.clear();
                    resetFocus();

                } else {
                    restFactory
                            .create(EXPLORER_RESOURCE)
                            .method(res -> res.find(currentQuery))
                            .onSuccess(resultPage -> {
                                if (resultPage.getPageStart() != cellTable.getPageStart()) {
                                    cellTable.setPageStart(resultPage.getPageStart());
                                }
                                dataConsumer.accept(resultPage);

                                if (filterChange) {
                                    if (resultPage.size() > 0) {
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

    public ExplorerTreeFilter.Builder getExplorerTreeFilterBuilder() {
        return explorerTreeFilterBuilder;
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
}
