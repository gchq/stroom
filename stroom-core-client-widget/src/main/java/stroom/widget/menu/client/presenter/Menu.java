package stroom.widget.menu.client.presenter;

import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import com.google.gwt.dom.client.Element;
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
        eventBus.addHandler(ShowMenuEvent.getType(), (event) ->
                show(event.getItems(), event.getPopupPosition(), event.getCloseHandler(), event.getAutoHidePartner()));
    }

    private void show(final List<Item> items,
                      final PopupPosition popupPosition,
                      final Runnable closeHandler,
                      final Element... autoHidePartner) {
        if (items == currentItems) {
            hide();

        } else {
            hide();

            menuPresenter = menuPresenterProvider.get();
            menuPresenter.setData(items);
            currentItems = items;

            final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
                @Override
                public void onShow() {
                    menuPresenter.selectFirstItem();
                }

                @Override
                public void onHideRequest(final boolean autoClose, final boolean ok) {
                    HidePopupEvent.fire(menuPresenter, menuPresenter, true, true);
                }

                @Override
                public void onHide(final boolean autoClose, final boolean ok) {
                    menuPresenter = null;
                    currentItems = null;
                    if (closeHandler != null) {
                        closeHandler.run();
                    }
                }
            };

            ShowPopupEvent.fire(menuPresenter, menuPresenter, PopupType.POPUP,
                    popupPosition, popupUiHandlers, autoHidePartner);
        }
    }

    private void hide() {
        if (menuPresenter != null) {
            menuPresenter.hideAll();
            menuPresenter = null;
            currentItems = null;
        }
    }
}
