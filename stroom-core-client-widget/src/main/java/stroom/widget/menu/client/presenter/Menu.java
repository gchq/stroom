package stroom.widget.menu.client.presenter;

import stroom.util.shared.GwtNullSafe;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;

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
        eventBus.addHandler(HideMenuEvent.getType(), this::hide);
    }

    private void show(final ShowMenuEvent event) {
        if (event.getItems() == currentItems) {
            hide(false, false);

        } else {
            hide(false, false);

            menuPresenter = menuPresenterProvider.get();
            menuPresenter.setData(event.getItems());
            menuPresenter.setAllowCloseOnMoveLeft(event.isAllowCloseOnMoveLeft());
            currentItems = event.getItems();

            ShowPopupEvent.builder(menuPresenter)
                    .popupType(PopupType.POPUP)
                    .popupPosition(event.getPopupPosition())
                    .addAutoHidePartner(event.getAutoHidePartners())
                    .onShow(e -> GwtNullSafe.consume(menuPresenter, MenuPresenter::focus))
                    .onHide(e -> {
                        if (event.getHideHandler() != null) {
                            event.getHideHandler().onHide(e);
                        }
                        // Call hide to ensure any delayed sub menus are closed
                        hide(false, false);
                        menuPresenter = null;
                        currentItems = null;
                    })
                    .fire();
        }
    }

    private void hide(final HideMenuEvent e) {
        hide(false, false);
    }

    private void hide(final boolean autoClose, final boolean ok) {
        if (menuPresenter != null) {
            menuPresenter.hideAll(autoClose, ok);
            menuPresenter = null;
            currentItems = null;
        }
    }
}
