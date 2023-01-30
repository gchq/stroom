package stroom.core.client.event;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class WindowCloseEvent extends GwtEvent<WindowCloseEvent.Handler> {

    private static Type<Handler> TYPE;

    private WindowCloseEvent() {
    }

    public static void fire(final HasHandlers handlers) {
        handlers.fireEvent(new WindowCloseEvent());
    }

    public static Type<Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<Handler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onWindowClose(this);
    }

    public interface Handler extends EventHandler {

        void onWindowClose(WindowCloseEvent event);
    }
}