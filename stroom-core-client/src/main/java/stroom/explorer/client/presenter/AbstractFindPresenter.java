package stroom.explorer.client.presenter;

import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.PagerView;
import stroom.data.table.client.MyCellTable;
import stroom.dispatch.client.RestError;
import stroom.dispatch.client.RestFactory;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.explorer.client.presenter.AbstractFindPresenter.FindView;
import stroom.explorer.shared.ExplorerResource;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.explorer.shared.FindRequest;
import stroom.explorer.shared.FindResult;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.widget.popup.client.event.HidePopupEvent;
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
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.proxy.Proxy;

import java.util.Objects;
import java.util.function.Consumer;

public abstract class AbstractFindPresenter<T_PROXY extends Proxy<?>>
        extends MyPresenter<FindView, T_PROXY>
        implements FindUiHandlers {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);

    private final CellTable<FindResult> cellTable;
    private final RestDataProvider<FindResult, ResultPage<FindResult>> dataProvider;
    private final MultiSelectionModelImpl<FindResult> selectionModel;
    private final NameFilterTimer timer = new NameFilterTimer();
    private final ExplorerTreeFilterBuilder explorerTreeFilterBuilder = new ExplorerTreeFilterBuilder();
    private ExplorerTreeFilter lastFilter;

    private FindRequest currentQuery = new FindRequest(
            new PageRequest(0, 100),
            null,
            null);
    private boolean initialised;
    protected boolean focusText;

    public AbstractFindPresenter(final EventBus eventBus,
                                 final FindView view,
                                 final T_PROXY proxy,
                                 final PagerView pagerView,
                                 final RestFactory restFactory) {
        super(eventBus, view, proxy);

        cellTable = new MyCellTable<FindResult>(100) {
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

        selectionModel = new MultiSelectionModelImpl<>(cellTable);
        SelectionEventManager<FindResult> selectionEventManager = new SelectionEventManager<>(
                cellTable,
                selectionModel,
                this::openDocument,
                null);
        cellTable.setSelectionModel(selectionModel, selectionEventManager);

        view.setResultView(pagerView);
        view.setUiHandlers(this);
        explorerTreeFilterBuilder.setRequiredPermissions(DocumentPermissionNames.READ);

        dataProvider = new RestDataProvider<FindResult, ResultPage<FindResult>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<FindResult>> dataConsumer,
                                final Consumer<RestError> errorConsumer) {
                final PageRequest pageRequest = new PageRequest(range.getStart(), range.getLength());
                updateFilter(explorerTreeFilterBuilder);
                final ExplorerTreeFilter filter = explorerTreeFilterBuilder.build();
                final boolean filterChange = !Objects.equals(lastFilter, filter);
                lastFilter = filter;

                currentQuery = new FindRequest(
                        pageRequest,
                        currentQuery.getSortList(),
                        filter);

                if ((filter.getRecentItems() == null && GwtNullSafe.isBlankString(filter.getNameFilter())) ||
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
                            .onFailure(errorConsumer)
                            .taskListener(pagerView)
                            .execWithListener();
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
        pagerView.setDataWidget(cellTable);
    }

    private void resetFocus() {
        if (focusText) {
            focusText = false;
            getView().focus();
        }
    }

    protected void updateFilter(final ExplorerTreeFilterBuilder explorerTreeFilterBuilder) {
    }

    private void openDocument(final FindResult match) {
        if (match != null) {
            OpenDocumentEvent.fire(this, match.getDocRef(), true);
            hide();
        }
    }

    @Override
    public void changeQuickFilter(final String name) {
        timer.setName(name);
        timer.cancel();
        timer.schedule(400);
    }

    @Override
    public void onFilterKeyDown(final KeyDownEvent event) {
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

    private void hide() {
        HidePopupEvent.builder(this).fire();
    }

    @Override
    protected void revealInParent() {
    }

    private class NameFilterTimer extends Timer {

        private String name;

        @Override
        public void run() {
            if (explorerTreeFilterBuilder.setNameFilter(name)) {
                refresh();
            }
        }

        public void setName(final String name) {
            this.name = name;
        }
    }

    public interface FindView extends View, Focus, HasUiHandlers<FindUiHandlers> {

        void setResultView(View view);
    }
}
