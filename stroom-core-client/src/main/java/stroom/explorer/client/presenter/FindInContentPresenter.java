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
import stroom.document.client.event.OpenDocumentEvent;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.explorer.client.event.ShowFindInContentEvent;
import stroom.explorer.client.presenter.FindInContentPresenter.FindInContentProxy;
import stroom.explorer.client.presenter.FindInContentPresenter.FindInContentView;
import stroom.explorer.shared.ExplorerResource;
import stroom.explorer.shared.FetchHighlightsRequest;
import stroom.explorer.shared.FindInContentRequest;
import stroom.explorer.shared.FindInContentResult;
import stroom.explorer.shared.StringMatch;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.TextRangeUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.TextRange;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class FindInContentPresenter
        extends MyPresenter<FindInContentView, FindInContentProxy>
        implements FindInContentUiHandlers {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);
    private static final int TIMER_DELAY_MS = 750;

    private final CellTable<FindInContentResult> cellTable;
    private final RestDataProvider<FindInContentResult, ResultPage<FindInContentResult>> dataProvider;
    private final MultiSelectionModelImpl<FindInContentResult> selectionModel;
    private final EditorPresenter editorPresenter;
    private final RestFactory restFactory;

    private FindInContentRequest currentQuery = new FindInContentRequest(
            PageRequest.createDefault(),
            null,
            StringMatch.any());
    private boolean initialised;
    protected boolean focusText;
    private StringMatch lastFilter;
    private boolean showing;

    private final Timer filterRefreshTimer = new Timer() {
        @Override
        public void run() {
            refresh();
        }
    };

    @Inject
    public FindInContentPresenter(final EventBus eventBus,
                                  final FindInContentView view,
                                  final FindInContentProxy proxy,
                                  final PagerView pagerView,
                                  final EditorPresenter editorPresenter,
                                  final RestFactory restFactory) {
        super(eventBus, view, proxy);
        this.editorPresenter = editorPresenter;
        this.restFactory = restFactory;

        cellTable = new MyCellTable<FindInContentResult>(100) {
            @Override
            protected void onBrowserEvent2(final Event event) {
                super.onBrowserEvent2(event);
                if (event.getTypeInt() == Event.ONKEYDOWN && event.getKeyCode() == KeyCodes.KEY_UP) {
                    if (cellTable.getKeyboardSelectedRow() == 0) {
                        getView().focus();
                    }
                }
            }
        };
        cellTable.addStyleName("FindCellTable");

        selectionModel = new MultiSelectionModelImpl<>();
        final SelectionEventManager<FindInContentResult> selectionEventManager = new SelectionEventManager<>(
                cellTable,
                selectionModel,
                this::openDocument,
                this::showHighlights);
        cellTable.setSelectionModel(selectionModel, selectionEventManager);

        view.setResultView(pagerView);
        editorPresenter.setReadOnly(true);
        view.setTextView(editorPresenter.getView());
        view.setUiHandlers(this);

        dataProvider = new RestDataProvider<FindInContentResult, ResultPage<FindInContentResult>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<FindInContentResult>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                final PageRequest pageRequest = new PageRequest(range.getStart(), range.getLength());
                currentQuery = new FindInContentRequest(pageRequest,
                        currentQuery.getSortList(),
                        currentQuery.getFilter());

                final boolean filterChange = !Objects.equals(lastFilter, currentQuery.getFilter());
                lastFilter = currentQuery.getFilter();

                if (NullSafe.isBlankString(currentQuery.getFilter().getPattern())) {
                    final ResultPage<FindInContentResult> resultPage = ResultPage.empty();
                    if (resultPage.getPageStart() != cellTable.getPageStart()) {
                        cellTable.setPageStart(resultPage.getPageStart());
                    }
                    dataConsumer.accept(resultPage);
                    selectionModel.clear();
                    resetFocus();

                } else {
                    restFactory
                            .create(EXPLORER_RESOURCE)
                            .method(res -> res.findInContent(currentQuery))
                            .onSuccess(resultPage -> {
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
                            .taskMonitorFactory(pagerView)
                            .exec();
                }
            }
        };

        final Column<FindInContentResult, FindInContentResult> column =
                new Column<FindInContentResult, FindInContentResult>(new FindInContentResultCell()) {
                    @Override
                    public FindInContentResult getValue(final FindInContentResult object) {
                        return object;
                    }
                };
        cellTable.addColumn(column);
        pagerView.setDataWidget(cellTable);
    }

    private void resetFocus() {
        if (focusText) {
            focusText = false;
            getView().focus();
        }
    }

    @Override
    protected void onBind() {
        registerHandler(selectionModel.addSelectionChangeHandler(event ->
                showHighlights(selectionModel.getSelected())));
    }

    private void showHighlights(final FindInContentResult selection) {
        editorPresenter.setText("");
        editorPresenter.setHighlights(Collections.emptyList());
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
                            editorPresenter.setText(response.getText());
                            if (response.getHighlights() != null) {
                                final List<TextRange> highlights = TextRangeUtil
                                        .convertMatchesToRanges(response.getText(), response.getHighlights());
                                editorPresenter.setHighlights(highlights);
                            }
                        }
                    })
                    .onFailure(throwable -> editorPresenter.setText(throwable.getMessage()))
                    .taskMonitorFactory(getView().getTaskListener())
                    .exec();
        }
    }

    @ProxyEvent
    public void onShow(final ShowFindInContentEvent event) {
        show();
    }

    private void openDocument(final FindInContentResult match) {
        if (match != null) {
            OpenDocumentEvent.fire(this, match.getDocContentMatch().getDocRef(), true);
            hide();
        }
    }

    @Override
    public void changePattern(final String pattern, final boolean matchCase, final boolean regex) {
        final FindInContentRequest query = new FindInContentRequest(
                currentQuery.getPageRequest(),
                currentQuery.getSortList(),
                regex
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

    @Override
    public void onPatternKeyDown(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            openDocument(selectionModel.getSelected());
        } else if (event.getNativeKeyCode() == KeyCodes.KEY_DOWN) {
            cellTable.setKeyboardSelectedRow(0, true);
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

    @Override
    protected void revealInParent() {
        show();
    }

    private void show() {
        if (!showing) {
            showing = true;
//            focusText = true;
            final PopupSize popupSize = PopupSize.resizable(1200, 800);
            ShowPopupEvent.builder(this)
                    .popupType(PopupType.CLOSE_DIALOG)
                    .popupSize(popupSize)
                    .caption("Find In Content")
                    .onShow(e -> getView().focus())
                    .onHide(e -> showing = false)
                    .fire();
        }
    }

    private void hide() {
        HidePopupRequestEvent.builder(this).fire();
    }

    @ProxyCodeSplit
    public interface FindInContentProxy extends Proxy<FindInContentPresenter> {

    }


    // --------------------------------------------------------------------------------


    public interface FindInContentView extends View, Focus, HasUiHandlers<FindInContentUiHandlers> {

        String getPattern();

        void setResultView(View view);

        void setTextView(View view);

        TaskMonitorFactory getTaskListener();
    }
}
