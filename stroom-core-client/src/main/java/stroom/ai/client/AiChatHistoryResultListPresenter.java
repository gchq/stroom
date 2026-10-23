/*
 * Copyright 2024 Crown Copyright
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
import stroom.dispatch.client.RestErrorHandler;
import stroom.explorer.client.presenter.FindDocResultListHandler;
import stroom.preferences.client.DateTimeFormatter;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.TokenError;
import stroom.widget.datagrid.client.DataGridWidget;
import stroom.widget.datagrid.client.FindResultLists;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.Consumer;

public class AiChatHistoryResultListPresenter extends MyPresenterWidget<PagerView> {

    private final DataGridWidget<AiChat> resultList;
    private final RestDataProvider<AiChat, ResultPage<AiChat>> dataProvider;
    private final MultiSelectionModelImpl<AiChat> selectionModel;
    private String filter;
    private String lastFilter;
    private Consumer<String> filterErrorConsumer;
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
                                            final AskStroomAiClient askStroomAiClient,
                                            final DateTimeFormatter dateTimeFormatter) {
        super(eventBus, view);

        selectionModel = new MultiSelectionModelImpl<>();

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
                            // A rejected filter comes back as an empty page like any other, so
                            // surface the reason on the filter box. See ResultPage.filterError.
                            if (filterErrorConsumer != null) {
                                filterErrorConsumer.accept(
                                        NullSafe.get(resultPage.getFilterError(), TokenError::getText));
                            }
                            if (resultPage.getPageStart() != resultList.getPageStart()) {
                                resultList.setPageStart(resultPage.getPageStart());
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

        // A striped list of a page of results, the one with the focus chosen; Enter, or a double
        // click, opens it, and Up on the first goes back to the filter
        resultList = FindResultLists.create(this,
                "Chats",
                new AiChatCell(dateTimeFormatter),
                selectionModel,
                doc -> findResultListHandler.openDocument(doc),
                () -> findResultListHandler.focus());
        view.setDataWidget(resultList);
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
            dataProvider.addDataDisplay(resultList);
        } else {
            dataProvider.refresh();
        }
    }

    public void setFindResultListHandler(final FindDocResultListHandler<AiChat> findResultListHandler) {
        this.findResultListHandler = findResultListHandler;
    }

    public void setFilterErrorConsumer(final Consumer<String> filterErrorConsumer) {
        this.filterErrorConsumer = filterErrorConsumer;
    }

    public boolean setFilter(final String filter) {
        this.filter = filter;
        return !Objects.equals(filter, lastFilter);
    }

    public AiChat getSelected() {
        return selectionModel.getSelected();
    }

    public void setKeyboardSelectedRow(final int row, final boolean stealFocus) {
        resultList.setKeyboardSelectedRow(row, stealFocus);
    }

    public void setFocusText(final boolean focusText) {
        this.focusText = focusText;
    }
}
