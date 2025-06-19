package stroom.query.client.presenter;

import stroom.query.client.presenter.RefreshRequestEvent.Handler;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class RefreshRequestEvent extends GwtEvent<Handler> {

    private static Type<Handler> TYPE;

    private RefreshRequestEvent() {
    }

    public static void fire(final HasHandlers handlers) {
        handlers.fireEvent(new RefreshRequestEvent());
    }

    public static Type<Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public final Type<Handler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onRefresh(this);
    }

    public interface Handler extends EventHandler {

        void onRefresh(RefreshRequestEvent event);
    }
}
