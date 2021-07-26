package stroom.widget.menu.client.presenter;

import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class Menu {

    private final Provider<MenuPresenter> menuPresenterProvider;

    private MenuPresenter menuPresenter;
    private List<Item> currentItems;

    @Inject
    public Menu(final EventBus eventBus,
                final Provider<MenuPresenter> menuPresenterProvider) {
        this.menuPresenterProvider = menuPresenterProvider;
        eventBus.addHandler(ShowMenuEvent.getType(), this::show);
    }

    private void show(final ShowMenuEvent event) {
        if (event.getItems() == currentItems) {
            hide();

        } else {
            hide();

            menuPresenter = menuPresenterProvider.get();
            menuPresenter.setData(event.getItems());
            menuPresenter.setFocusBehaviour(event.getFocusBehaviour());
            currentItems = event.getItems();

            final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
                @Override
                public void onShow() {
                    menuPresenter.selectFirstItem(event.getFocusBehaviour().switchFocus());
                }

                @Override
                public void onHideRequest(final boolean autoClose, final boolean ok) {
                    HidePopupEvent.fire(menuPresenter, menuPresenter, true, true);
                }

                @Override
                public void onHide(final boolean autoClose, final boolean ok) {
                    menuPresenter = null;
                    currentItems = null;
                }
            };

            ShowPopupEvent.fire(menuPresenter, menuPresenter, PopupType.POPUP,
                    event.getPopupPosition(), popupUiHandlers, event.getAutoHidePartner());
        }
    }

    private void hide() {
        if (menuPresenter != null) {
            menuPresenter.hideAll(false);
            menuPresenter = null;
            currentItems = null;
        }
    }
}
