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

package stroom.explorer.client.component;

import stroom.data.client.presenter.RestDataProvider;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.editor.client.component.CodeEditorController;
import stroom.explorer.shared.ExplorerResource;
import stroom.explorer.shared.FetchHighlightsRequest;
import stroom.explorer.shared.FindInContentRequest;
import stroom.explorer.shared.FindInContentResult;
import stroom.explorer.shared.StringMatch;
import stroom.explorer.shared.StringMatch.MatchType;
import stroom.util.client.TextRangeUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.TextRange;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.view.DialogAction;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.user.client.Timer;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// Find In Content ([FindInContent]): finds the documents whose content holds a pattern, shows the
/// text of the one chosen with what was found highlighted, and opens it. Made once by the app and
/// shown again, it keeps the last pattern.
public class FindInContentController extends Controller {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);
    private static final int TIMER_DELAY_MS = 750;

    private final FindInContent view;
    private final RestFactory restFactory;
    private final CodeEditorController editor;
    private final RestDataProvider<FindInContentResult, ResultPage<FindInContentResult>> dataProvider;

    private FindInContentRequest currentQuery = new FindInContentRequest(
            PageRequest.createDefault(),
            null,
            StringMatch.any());
    private boolean initialised;
    private StringMatch lastFilter;
    private boolean showing;

    private final Timer filterRefreshTimer = new Timer() {
        @Override
        public void run() {
            refresh();
        }
    };

    /// Made by GIN, with its content.
    ///
    /// @param eventBus               The application's event bus.
    /// @param view                   The content.
    /// @param codeEditorProvider     Makes the controller of the text's editor.
    /// @param restFactory            Finds the documents and fetches their text.
    @Inject
    public FindInContentController(final EventBus eventBus,
                                   final FindInContent view,
                                   final Provider<CodeEditorController> codeEditorProvider,
                                   final RestFactory restFactory) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        initWidget(view);
        editor = make(codeEditorProvider).attach(view.getText());
        editor.setReadOnly(true);
        onDispose(filterRefreshTimer::cancel);

        view.setOnPatternChange(this::changePattern);
        view.setOnPatternKeyDown(this::onPatternKeyDown);
        view.setOnOpen(this::openDocument);

        dataProvider = new RestDataProvider<FindInContentResult, ResultPage<FindInContentResult>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<FindInContentResult>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                fetch(range, dataConsumer, errorHandler);
            }
        };

        view.setOnSelectionChange(this::showHighlights);
    }

    /// Shows the dialog, unless it is shown already, with the pattern it last had.
    public void show() {
        if (!showing) {
            showing = true;
            ControllerDialog.show(this)
                    .popupType(PopupType.CLOSE_DIALOG)
                    .popupSize(PopupSize.resizable(1200, 800))
                    .caption("Find In Content")
                    .onShow(e -> view.focus())
                    .onHide(e -> showing = false)
                    .fire();
        }
    }

    private void fetch(final Range range,
                       final Consumer<ResultPage<FindInContentResult>> dataConsumer,
                       final RestErrorHandler errorHandler) {
        final PageRequest pageRequest = new PageRequest(range.getStart(), range.getLength());
        currentQuery = new FindInContentRequest(pageRequest,
                currentQuery.getSortList(),
                currentQuery.getFilter());

        final boolean filterChange = !Objects.equals(lastFilter, currentQuery.getFilter());
        lastFilter = currentQuery.getFilter();

        if (NullSafe.isBlankString(currentQuery.getFilter().getPattern())) {
            view.setPatternValid();
            final ResultPage<FindInContentResult> resultPage = ResultPage.empty();
            if (resultPage.getPageStart() != view.getPageStart()) {
                view.setPageStart(resultPage.getPageStart());
            }
            dataConsumer.accept(resultPage);
            view.getSelectionModel().clear();
        } else {
            restFactory
                    .create(EXPLORER_RESOURCE)
                    .method(res -> res.findInContent(currentQuery))
                    .onSuccess(resultPage -> {
                        view.setPatternValid();
                        if (resultPage.getPageStart() != view.getPageStart()) {
                            view.setPageStart(resultPage.getPageStart());
                        }
                        dataConsumer.accept(resultPage);

                        if (filterChange) {
                            if (!resultPage.isEmpty()) {
                                view.getSelectionModel().setSelected(resultPage.getValues().get(0));
                            } else {
                                view.getSelectionModel().clear();
                            }
                        }
                    })
                    .onFailure(error -> {
                        if (MatchType.REGEX.equals(currentQuery.getFilter().getMatchType())) {
                            // Most likely the regex is invalid, so say why under it, rather than in
                            // a popup that takes the user away from typing it
                            view.setPatternInvalid(error.getMessage());
                            dataConsumer.accept(ResultPage.empty());
                            view.getSelectionModel().clear();
                        } else {
                            errorHandler.onError(error);
                        }
                    })
                    .owner(this)
                    .taskMonitorFactory(view.getTaskMonitor())
                    .exec();
        }
    }

    private void showHighlights(final FindInContentResult selection) {
        editor.setText("");
        editor.setHighlights(Collections.emptyList());
        if (selection != null && selection.getDocContentMatch() != null) {
            final FetchHighlightsRequest fetchHighlightsRequest = new FetchHighlightsRequest(
                    selection.getDocContentMatch().getDocRef(),
                    selection.getDocContentMatch().getExtension(),
                    currentQuery.getFilter());
            restFactory
                    .create(EXPLORER_RESOURCE)
                    .method(res -> res.fetchHighlights(fetchHighlightsRequest))
                    .onSuccess(response -> {
                        if (response != null && response.getText() != null) {
                            editor.setText(response.getText());
                            if (response.getHighlights() != null) {
                                final List<TextRange> highlights = TextRangeUtil
                                        .convertMatchesToRanges(response.getText(), response.getHighlights());
                                editor.setHighlights(highlights);
                            }
                        }
                    })
                    .onFailure(throwable -> editor.setText(throwable.getMessage()))
                    .owner(this)
                    .taskMonitorFactory(view.getTextTaskMonitor())
                    .exec();
        }
    }

    private void openDocument(final FindInContentResult match) {
        if (match != null) {
            OpenDocumentEvent.fire(this, match.getDocContentMatch().getDocRef(), true);
            ControllerDialog.pressAction(this, DialogAction.CLOSE);
        }
    }

    private void changePattern() {
        final String pattern = view.getPattern();
        final boolean matchCase = view.isMatchCase();
        final FindInContentRequest query = new FindInContentRequest(
                currentQuery.getPageRequest(),
                currentQuery.getSortList(),
                view.isRegex()
                        ? StringMatch.regex(pattern, matchCase)
                        : StringMatch.contains(pattern, matchCase));

        if (!Objects.equals(currentQuery, query)) {
            this.currentQuery = query;
            // Add in a slight delay to give the user a chance to type a few chars before we fire off
            // a rest call. This helps to reduce the logging too
            if (!filterRefreshTimer.isRunning()) {
                filterRefreshTimer.schedule(TIMER_DELAY_MS);
            }
        }
    }

    private void onPatternKeyDown(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            openDocument(view.getSelectionModel().getSelected());
        } else if (event.getNativeKeyCode() == KeyCodes.KEY_DOWN) {
            view.setKeyboardSelectedRow(0, true);
        }
    }

    private void refresh() {
        if (!initialised) {
            initialised = true;
            view.setDataProvider(dataProvider);
        } else {
            dataProvider.refresh();
        }
    }
}
