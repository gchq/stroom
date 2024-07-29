package stroom.dispatch.client;

import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.gwt.event.shared.HasHandlers;

public interface RestErrorHandler {

    void onError(RestError error);

    static DefaultErrorHandler forPopup(final HasHandlers hasHandlers,
                                        final HidePopupRequestEvent event) {
        return new DefaultErrorHandler(hasHandlers, event::reset);
    }
}
