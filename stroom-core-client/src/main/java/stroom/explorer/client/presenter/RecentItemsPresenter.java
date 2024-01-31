package stroom.explorer.client.presenter;

import stroom.config.global.client.presenter.ListDataProvider;
import stroom.core.client.RecentItems;
import stroom.data.grid.client.PagerView;
import stroom.data.table.client.MyCellTable;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.explorer.client.event.ShowRecentItemsEvent;
import stroom.explorer.client.presenter.RecentItemsPresenter.RecentItemsProxy;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.explorer.shared.FindResult;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.shared.GwtNullSafe;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Timer;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RecentItemsPresenter
        extends MyPresenter<FindView, RecentItemsProxy>
        implements FindUiHandlers, ShowRecentItemsEvent.Handler {

    private final CellTable<FindResult> cellTable;
    private final ListDataProvider<FindResult> dataProvider;
    private final MultiSelectionModelImpl<FindResult> selectionModel;
    private final NameFilterTimer timer = new NameFilterTimer();
    private final ExplorerTreeFilterBuilder explorerTreeFilterBuilder = new ExplorerTreeFilterBuilder();
    private final RecentItems recentItems;
    private ExplorerTreeFilter lastFilter;

    private boolean initialised;

    @Inject
    public RecentItemsPresenter(final EventBus eventBus,
                                final FindView view,
                                final RecentItemsProxy proxy,
                                final PagerView pagerView,
                                final RecentItems recentItems) {
        super(eventBus, view, proxy);
        this.recentItems = recentItems;

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
        dataProvider = new ListDataProvider<FindResult>();

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
    @Override
    public void onShowRecentItems(final ShowRecentItemsEvent event) {
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
        final ExplorerTreeFilter filter = explorerTreeFilterBuilder.build();
        final boolean filterChange = !Objects.equals(lastFilter, filter);
        lastFilter = filter;

        final String nameFilter = explorerTreeFilterBuilder.build().getNameFilter();
        final List<FindResult> results = new ArrayList<>(recentItems.getRecentItems());
        List<FindResult> filtered = results;
        if (!GwtNullSafe.isBlankString(nameFilter)) {
            filtered = new ArrayList<>(results.size());
            for (FindResult findResult : results) {
                if (findResult.getDocRef() != null &&
                        findResult.getDocRef().getName() != null &&
                        findResult.getDocRef().getName().toLowerCase().contains(nameFilter.toLowerCase())) {
                    filtered.add(findResult);
                }
            }
        }

        dataProvider.setCompleteList(filtered);

        if (filterChange) {
            if (filtered.size() > 0) {
                selectionModel.setSelected(filtered.get(0));
            } else {
                selectionModel.clear();
            }
        }

        if (!initialised) {
            initialised = true;
            dataProvider.addDataDisplay(cellTable);
        } else {
            dataProvider.refresh(true);
        }
    }

    @Override
    protected void revealInParent() {
        show();
    }

    private void show() {
        lastFilter = null;
        refresh();
        final PopupSize popupSize = PopupSize.resizable(800, 600);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(popupSize)
                .caption("Recent Items")
                .onShow(e -> getView().focus())
                .onHideRequest(HidePopupRequestEvent::hide)
                .fire();
    }

    private void hide() {
        HidePopupEvent.builder(this).fire();
    }

    @ProxyCodeSplit
    public interface RecentItemsProxy extends Proxy<RecentItemsPresenter> {

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
