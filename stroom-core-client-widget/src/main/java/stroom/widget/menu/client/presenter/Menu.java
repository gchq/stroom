package stroom.widget.menu.client.presenter;

import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.DefaultPopupUiHandlers;
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
            hide(false, false);

        } else {
            hide(false, false);

            menuPresenter = menuPresenterProvider.get();
            menuPresenter.setData(event.getItems());
            currentItems = event.getItems();

            final PopupUiHandlers popupUiHandlers = new DefaultPopupUiHandlers(menuPresenter) {
                @Override
                public void onShow() {
                    menuPresenter.selectFirstItem(true);
                }

                @Override
                public void onHide(final boolean autoClose, final boolean ok) {
                    menuPresenter = null;
                    currentItems = null;

                    if (autoClose) {
                        restoreFocus();
                    }
                }
            };

            ShowPopupEvent.fire(menuPresenter, menuPresenter, PopupType.POPUP,
                    event.getPopupPosition(), popupUiHandlers, event.getAutoHidePartner());
        }
    }

    private void hide(final boolean autoClose, final boolean ok) {
        if (menuPresenter != null) {
            menuPresenter.hideAll(autoClose, ok);
            menuPresenter = null;
            currentItems = null;
        }
    }
}
