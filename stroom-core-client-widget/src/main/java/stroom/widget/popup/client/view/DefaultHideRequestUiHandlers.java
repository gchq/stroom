package stroom.widget.popup.client.view;

import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.gwtplatform.mvp.client.PresenterWidget;

public class DefaultHideRequestUiHandlers implements HideRequestUiHandlers {

    private final PresenterWidget<?> presenterWidget;

    public DefaultHideRequestUiHandlers(final PresenterWidget<?> presenterWidget) {
        this.presenterWidget = presenterWidget;
    }

    @Override
    public void hideRequest(final HideRequest request) {
        HidePopupRequestEvent.builder(presenterWidget).autoClose(request.isAutoClose()).ok(request.isOk()).fire();
    }
}
