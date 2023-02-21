package stroom.about.client.event;

import stroom.about.client.event.ShowAboutEvent.ShowAboutHandler;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class ShowAboutEvent extends GwtEvent<ShowAboutHandler> {

    private static Type<ShowAboutHandler> TYPE;

    private ShowAboutEvent() {
    }

    public static void fire(final HasHandlers handlers) {
        handlers.fireEvent(new ShowAboutEvent());
    }

    public static Type<ShowAboutHandler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<ShowAboutHandler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final ShowAboutHandler showAboutHandler) {
        showAboutHandler.onShow(this);
    }

    public interface ShowAboutHandler extends EventHandler {

        void onShow(ShowAboutEvent event);
    }
}
