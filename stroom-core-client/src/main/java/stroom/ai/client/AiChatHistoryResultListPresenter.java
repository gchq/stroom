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

package stroom.ai.client;

import stroom.ai.shared.AiChat;
import stroom.ai.shared.FindAiChatHistoryCriteria;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.PagerView;
import stroom.data.table.client.MyCellTable;
import stroom.dispatch.client.RestErrorHandler;
import stroom.explorer.client.presenter.FindDocResultListHandler;
import stroom.explorer.client.presenter.SelectionEventManager;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.widget.util.client.MultiSelectionModelImpl;

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

public class AiChatHistoryResultListPresenter extends MyPresenterWidget<PagerView> {

    private final CellTable<AiChat> cellTable;
    private final RestDataProvider<AiChat, ResultPage<AiChat>> dataProvider;
    private final MultiSelectionModelImpl<AiChat> selectionModel;
    private String filter;
    private String lastFilter;
    private boolean initialised;
    private boolean focusText;
    private FindDocResultListHandler<AiChat> findResultListHandler = new FindDocResultListHandler<AiChat>() {
        @Override
        public void openDocument(final AiChat match) {

        }

        @Override
        public void focus() {

        }
    };

    @Inject
    public AiChatHistoryResultListPresenter(final EventBus eventBus,
                                            final PagerView view,
                                            final AskStroomAiClient askStroomAiClient) {
        super(eventBus, view);

        cellTable = new MyCellTable<AiChat>(100) {
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
        final SelectionEventManager<AiChat> selectionEventManager = new SelectionEventManager<>(
                cellTable,
                selectionModel,
                doc -> findResultListHandler.openDocument(doc),
                null);
        cellTable.setSelectionModel(selectionModel, selectionEventManager);

        dataProvider = new RestDataProvider<AiChat, ResultPage<AiChat>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<AiChat>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                final PageRequest pageRequest = new PageRequest(range.getStart(), range.getLength());
                final boolean filterChange = !Objects.equals(lastFilter, filter);
                lastFilter = filter;
                final FindAiChatHistoryCriteria criteria = new FindAiChatHistoryCriteria(
                        pageRequest,
                        null,
                        filter);

                askStroomAiClient
                        .listChats(criteria, resultPage -> {
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
                        }, view);
            }
        };

        final Column<AiChat, AiChat> column = new Column<AiChat, AiChat>(new AiChatCell()) {
            @Override
            public AiChat getValue(final AiChat object) {
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

    public void setFindResultListHandler(final FindDocResultListHandler<AiChat> findResultListHandler) {
        this.findResultListHandler = findResultListHandler;
    }

    public boolean setFilter(final String filter) {
        this.filter = filter;
        return !Objects.equals(filter, lastFilter);
    }

    public AiChat getSelected() {
        return selectionModel.getSelected();
    }

    public void setKeyboardSelectedRow(final int row, final boolean stealFocus) {
        cellTable.setKeyboardSelectedRow(row, stealFocus);
    }

    public void setFocusText(final boolean focusText) {
        this.focusText = focusText;
    }
}
