package stroom.widget.popup.client.presenter;

import stroom.widget.popup.client.event.HidePopupEvent;

import com.gwtplatform.mvp.client.PresenterWidget;

public class DefaultPopupUiHandlers implements PopupUiHandlers {

    private final PresenterWidget<?> presenterWidget;

    public DefaultPopupUiHandlers(final PresenterWidget<?> presenterWidget) {
        this.presenterWidget = presenterWidget;
    }

    @Override
    public void onShow() {
    }

    @Override
    public void onHideRequest(final boolean autoClose, final boolean ok) {
        hide(autoClose, ok);
    }

    @Override
    public void onHide(final boolean autoClose, final boolean ok) {
    }

    public void hide() {
        hide(false, false);
    }

    public void hide(final boolean autoClose, final boolean ok) {
        HidePopupEvent.fire(presenterWidget, presenterWidget, autoClose, ok);
    }
}
