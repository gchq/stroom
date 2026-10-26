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

package stroom.ai.client.component;

import stroom.ai.client.AiChatCell;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AskStroomAiResource;
import stroom.ai.shared.FindAiChatHistoryCriteria;
import stroom.data.client.presenter.RestDataProvider;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.preferences.client.DateTimeFormatter;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.TokenError;
import stroom.widget.component.client.Controller;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.Consumer;

/// The user's chats with Ask Stroom AI ([AiChatHistoryResultList]) found by a filter its owner
/// gives: a page at a time, the first chosen when the filter changes.
public class AiChatHistoryResultListController extends Controller {

    private static final AskStroomAiResource RESOURCE = GWT.create(AskStroomAiResource.class);

    private final AiChatHistoryResultList view;
    private final RestDataProvider<AiChat, ResultPage<AiChat>> dataProvider;
    private String filter;
    private String lastFilter;
    private boolean initialised;
    private boolean focusText;
    private Consumer<String> onFilterError = error -> {
    };
    private Runnable onFocusFilter = () -> {
    };

    /// Made by GIN, with its content.
    ///
    /// @param eventBus          The application's event bus.
    /// @param view              The content.
    /// @param restFactory       Finds the chats.
    /// @param dateTimeFormatter Formats when each chat was last changed.
    @Inject
    public AiChatHistoryResultListController(final EventBus eventBus,
                                             final AiChatHistoryResultList view,
                                             final RestFactory restFactory,
                                             final DateTimeFormatter dateTimeFormatter) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        view.addColumn(new AiChatCell(dateTimeFormatter));
        view.setOnUpFromFirst(() -> onFocusFilter.run());

        final MultiSelectionModel<AiChat> selectionModel = view.getSelectionModel();
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

                restFactory
                        .create(RESOURCE)
                        .method(res -> res.listChats(criteria))
                        .onSuccess(resultPage -> {
                            // A rejected filter comes back as an empty page like any other, so
                            // surface the reason on the filter box. See ResultPage.filterError.
                            onFilterError.accept(NullSafe.get(resultPage.getFilterError(), TokenError::getText));
                            if (resultPage.getPageStart() != view.getPageStart()) {
                                view.setPageStart(resultPage.getPageStart());
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
                        .owner(AiChatHistoryResultListController.this)
                        .taskMonitorFactory(view.getTaskMonitor())
                        .exec();
            }
        };
    }

    private void resetFocus() {
        if (focusText) {
            focusText = false;
            onFocusFilter.run();
        }
    }

    /// Finds the chats again; the first time, starts the list.
    public void refresh() {
        if (!initialised) {
            initialised = true;
            view.setDataProvider(dataProvider);
        } else {
            dataProvider.refresh();
        }
    }

    /// @param onFilterError Given why the filter was rejected after each find, or `null` if it
    ///                      wasn't.
    public void setOnFilterError(final Consumer<String> onFilterError) {
        this.onFilterError = onFilterError;
    }

    /// @param onOpen Given a chat to open (Enter, or a double click, on it).
    public void setOnOpen(final Consumer<AiChat> onOpen) {
        view.setOnOpen(onOpen);
    }

    /// @param onFocusFilter Run to put the focus back on the filter: on Up from the first chat, and
    ///                      after a find asked for with [#setFocusText(boolean)].
    public void setOnFocusFilter(final Runnable onFocusFilter) {
        this.onFocusFilter = onFocusFilter;
    }

    /// @param filter The filter the chats are found by.
    /// @return Whether it differs from the one the chats were last found by.
    public boolean setFilter(final String filter) {
        this.filter = filter;
        return !Objects.equals(filter, lastFilter);
    }

    /// @return The chat with the focus, or `null`.
    public AiChat getSelected() {
        return view.getSelectionModel().getSelected();
    }

    /// Moves the focus to a row.
    ///
    /// @param row        The row on the page.
    /// @param stealFocus Whether to take the focus from where it is.
    public void setKeyboardSelectedRow(final int row, final boolean stealFocus) {
        view.setKeyboardSelectedRow(row, stealFocus);
    }

    /// @param focusText Whether the next find puts the focus back on the filter.
    public void setFocusText(final boolean focusText) {
        this.focusText = focusText;
    }
}
