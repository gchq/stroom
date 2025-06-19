package stroom.data.client.presenter;

import stroom.data.client.presenter.ItemNavigatorPresenter.ItemNavigatorView;
import stroom.util.shared.HasItems;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class ItemNavigatorPresenter extends MyPresenterWidget<ItemNavigatorView> {

    private final Provider<ItemSelectionPresenter> itemSelectionPresenterProvider;
    private ItemSelectionPresenter itemSelectionPresenter = null;
    private HasItems display;

    @Inject
    public ItemNavigatorPresenter(final EventBus eventBus,
                                  final ItemNavigatorView view,
                                  final Provider<ItemSelectionPresenter> itemSelectionPresenterProvider) {
        super(eventBus, view);
        this.itemSelectionPresenterProvider = itemSelectionPresenterProvider;

        getView().setLabelClickHandler(this::handleLabelClick);
    }

    public void setDisplay(final HasItems display) {
        this.display = display;
        if (display != null && getView() != null) {
            getView().setDisplay(display);
            refreshNavigator();
        }
    }


    public void setRefreshing(final boolean refreshing) {
        getView().setRefreshing(refreshing);
    }

    public void refreshNavigator() {
        getView().refreshNavigator();
    }

    private ItemSelectionPresenter getItemSelectionPresenter() {
        if (itemSelectionPresenter == null) {
            itemSelectionPresenter = itemSelectionPresenterProvider.get();
        }
        return itemSelectionPresenter;
    }

    private void handleLabelClick(final ClickEvent clickEvent) {
        final ItemSelectionPresenter itemSelectionPresenter = getItemSelectionPresenter();
        itemSelectionPresenter.setDisplay(display);
        itemSelectionPresenter.show();
    }

    public interface ItemNavigatorView extends View {

        void setDisplay(final HasItems hasItems);

        void setRefreshing(final boolean refreshing);

        void refreshNavigator();

        void setLabelClickHandler(final ClickHandler clickHandler);
    }
}
