package stroom.explorer.client.presenter;

import stroom.cell.list.client.CustomCellList;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocContentHighlights;
import stroom.docref.StringMatch;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.explorer.client.event.ShowFindEvent;
import stroom.explorer.client.presenter.FindPresenter.FindProxy;
import stroom.explorer.client.presenter.FindPresenter.FindView;
import stroom.explorer.shared.ExplorerDocContentMatch;
import stroom.explorer.shared.ExplorerResource;
import stroom.explorer.shared.FetchHighlightsRequest;
import stroom.explorer.shared.FindExplorerNodeQuery;
import stroom.util.client.TextRangeUtil;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.TextRange;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.MySingleSelectionModel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.CellList;
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

public class FindPresenter extends MyPresenter<FindView, FindProxy> implements FindUiHandlers {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);
    private static final int TIMER_DELAY_MS = 500;

    private final CellList<ExplorerDocContentMatch> cellList;
    private final RestDataProvider<ExplorerDocContentMatch, ResultPage<ExplorerDocContentMatch>> dataProvider;
    private final MySingleSelectionModel<ExplorerDocContentMatch> selectionModel;
    private final EditorPresenter editorPresenter;
    private final RestFactory restFactory;

    private FindExplorerNodeQuery currentQuery = new FindExplorerNodeQuery(
            new PageRequest(0, 100),
            null,
            StringMatch.any());
    private boolean initialised;
    private StringMatch lastFilter;

    private final Timer filterRefreshTimer = new Timer() {
        @Override
        public void run() {
            refresh();
        }
    };

    @Inject
    public FindPresenter(final EventBus eventBus,
                         final FindView view,
                         final FindProxy proxy,
                         final PagerView pagerView,
                         final EditorPresenter editorPresenter,
                         final RestFactory restFactory) {
        super(eventBus, view, proxy);
        this.editorPresenter = editorPresenter;
        this.restFactory = restFactory;

        selectionModel = new MySingleSelectionModel<>();
        cellList = new CustomCellList<>(new ExplorerDocContentMatchCell());
        pagerView.setDataWidget(cellList);
        cellList.setSelectionModel(selectionModel);

        view.setResultView(pagerView);
        view.setTextView(editorPresenter.getView());
        view.setUiHandlers(this);

        dataProvider = new RestDataProvider<ExplorerDocContentMatch, ResultPage<ExplorerDocContentMatch>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<ExplorerDocContentMatch>> dataConsumer,
                                final Consumer<Throwable> throwableConsumer) {
                final PageRequest pageRequest = new PageRequest(range.getStart(), range.getLength());
                currentQuery = new FindExplorerNodeQuery(pageRequest,
                        currentQuery.getSortList(),
                        currentQuery.getFilter());

                final boolean filterChange = !Objects.equals(lastFilter, currentQuery.getFilter());
                lastFilter = currentQuery.getFilter();

                if (GwtNullSafe.isBlankString(currentQuery.getFilter().getPattern())) {
                    final ResultPage<ExplorerDocContentMatch> resultPage =
                            ResultPage.createUnboundedList(Collections.emptyList());
                    if (resultPage.getPageStart() != cellList.getPageStart()) {
                        cellList.setPageStart(resultPage.getPageStart());
                    }
                    dataConsumer.accept(resultPage);
                    selectionModel.clear();

                } else {
                    restFactory.builder()
                            .forResultPageOf(ExplorerDocContentMatch.class)
                            .onSuccess(resultPage -> {
                                if (resultPage.getPageStart() != cellList.getPageStart()) {
                                    cellList.setPageStart(resultPage.getPageStart());
                                }
                                dataConsumer.accept(resultPage);

                                if (filterChange) {
                                    if (resultPage.size() > 0) {
                                        selectionModel.setSelected(resultPage.getValues().get(0), true);
                                    } else {
                                        selectionModel.clear();
                                    }
                                }
                            })
                            .onFailure(throwableConsumer)
                            .call(EXPLORER_RESOURCE)
                            .findContent(currentQuery);
                }
            }
        };
    }

    @Override
    protected void onBind() {
        registerHandler(selectionModel.addSelectionChangeHandler(event -> {
            editorPresenter.setText("");
            editorPresenter.setHighlights(Collections.emptyList());
            final ExplorerDocContentMatch selection = selectionModel.getSelectedObject();
            if (selection != null && selection.getDocContentMatch() != null) {
                final FetchHighlightsRequest fetchHighlightsRequest = new FetchHighlightsRequest(
                        selection.getDocContentMatch().getDocRef(),
                        selection.getDocContentMatch().getExtension(),
                        currentQuery.getFilter());
                restFactory.builder()
                        .forType(DocContentHighlights.class)
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
                        .call(EXPLORER_RESOURCE)
                        .fetchHighlights(fetchHighlightsRequest);
            }
        }));
        registerHandler(selectionModel.addDoubleSelectHandler(event -> {
            final ExplorerDocContentMatch match = selectionModel.getSelectedObject();
            if (match != null) {
                OpenDocumentEvent.fire(this, match.getDocContentMatch().getDocRef(), true);
                hide();
            }
        }));
    }

    @ProxyEvent
    public void onShow(final ShowFindEvent event) {
        show();
    }

    @Override
    public void changePattern(final String pattern, final boolean matchCase, final boolean regex) {
        String trimmed;
        if (pattern == null) {
            trimmed = "";
        } else {
            trimmed = pattern.trim();
        }

        final FindExplorerNodeQuery query = new FindExplorerNodeQuery(
                currentQuery.getPageRequest(),
                currentQuery.getSortList(),
                regex
                        ? StringMatch.regex(trimmed, matchCase)
                        : StringMatch.contains(trimmed, matchCase));

        if (!Objects.equals(currentQuery, query)) {
            this.currentQuery = query;
            // Add in a slight delay to give the user a chance to type a few chars before we fire off
            // a rest call. This helps to reduce the logging too
            if (!filterRefreshTimer.isRunning()) {
                filterRefreshTimer.schedule(TIMER_DELAY_MS);
            }
        }
    }

    public void refresh() {
        if (!initialised) {
            initialised = true;
            dataProvider.addDataDisplay(cellList);
        } else {
            dataProvider.refresh();
        }
    }

    @Override
    protected void revealInParent() {
        show();
    }

    private void show() {
        final PopupSize popupSize = PopupSize.resizable(800, 600);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(popupSize)
                .caption("Find Content")
                .onShow(e -> getView().focus())
                .onHideRequest(HidePopupRequestEvent::hide)
                .fire();
    }

    private void hide() {
        HidePopupEvent.builder(this).fire();
    }

    @ProxyCodeSplit
    public interface FindProxy extends Proxy<FindPresenter> {

    }

    public interface FindView extends View, Focus, HasUiHandlers<FindUiHandlers> {

        String getPattern();

        void setResultView(View view);

        void setTextView(View view);
    }
}
