package stroom.explorer.client.presenter;

import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.PagerView;
import stroom.data.table.client.MyCellTable;
import stroom.dispatch.client.RestFactory;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.explorer.client.event.ShowFindEvent;
import stroom.explorer.client.presenter.FindPresenter.FindProxy;
import stroom.explorer.client.presenter.FindPresenter.FindView;
import stroom.explorer.shared.ExplorerResource;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.explorer.shared.FindRequest;
import stroom.explorer.shared.FindResult;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.widget.popup.client.event.HidePopupEvent;
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

import java.util.Objects;
import java.util.function.Consumer;

public class FindPresenter
        extends MyPresenter<FindView, FindProxy>
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

    @Inject
    public FindPresenter(final EventBus eventBus,
                         final FindView view,
                         final FindProxy proxy,
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
                                final Consumer<Throwable> throwableConsumer) {
                final PageRequest pageRequest = new PageRequest(range.getStart(), range.getLength());
                final ExplorerTreeFilter filter = explorerTreeFilterBuilder.build();
                final boolean filterChange = !Objects.equals(lastFilter, filter);
                lastFilter = filter;

                currentQuery = new FindRequest(
                        pageRequest,
                        currentQuery.getSortList(),
                        filter);

                if (GwtNullSafe.isBlankString(explorerTreeFilterBuilder.build().getNameFilter())) {
                    final ResultPage<FindResult> resultPage = ResultPage.empty();
                    if (resultPage.getPageStart() != cellTable.getPageStart()) {
                        cellTable.setPageStart(resultPage.getPageStart());
                    }
                    dataConsumer.accept(resultPage);
                    selectionModel.clear();

                } else {
                    restFactory.builder()
                            .forResultPageOf(FindResult.class)
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
                            })
                            .onFailure(throwableConsumer)
                            .call(EXPLORER_RESOURCE)
                            .find(currentQuery);
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

    @ProxyEvent
    public void onShow(final ShowFindEvent event) {
        show();
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

    @Override
    protected void revealInParent() {
        show();
    }

    private void show() {
        final PopupSize popupSize = PopupSize.resizable(800, 600);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(popupSize)
                .caption("Find")
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

        void setResultView(View view);
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
}
