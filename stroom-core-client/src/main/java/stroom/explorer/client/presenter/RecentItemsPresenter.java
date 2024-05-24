package stroom.explorer.client.presenter;

import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestFactory;
import stroom.explorer.client.event.ShowRecentItemsEvent;
import stroom.explorer.client.presenter.RecentItemsPresenter.RecentItemsProxy;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;

public class RecentItemsPresenter
        extends AbstractFindPresenter<RecentItemsProxy>
        implements ShowRecentItemsEvent.Handler {

    private final RecentItems recentItems;
    private boolean showing;

    @Inject
    public RecentItemsPresenter(final EventBus eventBus,
                                final FindView view,
                                final RecentItemsProxy proxy,
                                final PagerView pagerView,
                                final RestFactory restFactory,
                                final RecentItems recentItems) {
        super(eventBus, view, proxy, pagerView, restFactory);
        this.recentItems = recentItems;
    }

    @ProxyEvent
    @Override
    public void onShowRecentItems(final ShowRecentItemsEvent event) {
        try {
            if (!showing) {
                showing = true;
                focusText = true;
                refresh();
                final PopupSize popupSize = PopupSize.resizable(800, 600);
                ShowPopupEvent.builder(this)
                        .popupType(PopupType.CLOSE_DIALOG)
                        .popupSize(popupSize)
                        .caption("Recent Items")
                        .onShow(e -> getView().focus())
                        .onHideRequest(HidePopupRequestEvent::hide)
                        .onHide(e -> showing = false)
                        .fire();
            }
        } catch (final RuntimeException e) {
            GWT.log("Error in onShowRecentItems" + e.getMessage());
        }
    }

    @Override
    protected void updateFilter(final ExplorerTreeFilterBuilder explorerTreeFilterBuilder) {
        explorerTreeFilterBuilder.setRecentItems(recentItems.getRecentItems());
        explorerTreeFilterBuilder.setNameFilter(explorerTreeFilterBuilder.build().getNameFilter(), true);
    }

    @ProxyCodeSplit
    public interface RecentItemsProxy extends Proxy<RecentItemsPresenter> {

    }
}
