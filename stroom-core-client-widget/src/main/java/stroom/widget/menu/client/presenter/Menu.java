package stroom.widget.menu.client.presenter;

import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;

import java.util.List;
import javax.inject.Inject;
import javax.inject.Provider;

public class Menu {

    private final Provider<MenuPresenter> menuPresenterProvider;

    private MenuPresenter menuPresenter;

    @Inject
    public Menu(final Provider<MenuPresenter> menuPresenterProvider) {
        this.menuPresenterProvider = menuPresenterProvider;
    }

    public void show(final List<Item> items,
                     final int x,
                     final int y,
                     final Runnable closeHandler,
                     final Element... autoHidePartner) {
        GWT.log("SHOW MENU " + x + "," + y);

        hide();

        menuPresenter = menuPresenterProvider.get();
        menuPresenter.setData(items);

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
                if (closeHandler != null) {
                    closeHandler.run();
                }
            }
        };

        final PopupPosition popupPosition = new PopupPosition(x, y);
        ShowPopupEvent.fire(menuPresenter, menuPresenter, PopupType.POPUP,
                popupPosition, popupUiHandlers, autoHidePartner);
    }

    public void hide() {
        if (menuPresenter != null) {
            menuPresenter.hideAll();
            menuPresenter = null;
        }
    }

    public boolean isShowing() {
        return menuPresenter != null;
    }
}
