package stroom.widget.menu.client.presenter;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class HideMenuEvent
        extends GwtEvent<HideMenuEvent.Handler> {

    private static Type<Handler> TYPE;

    private HideMenuEvent() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Type<Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public final Type<Handler> getAssociatedType() {
        return TYPE;
    }

    @Override
    protected void dispatch(Handler handler) {
        handler.onHide(this);
    }

    public interface Handler extends EventHandler {

        void onHide(HideMenuEvent event);
    }

    public static class Builder {

        public Builder() {
        }

        public void fire(HasHandlers hasHandlers) {
            hasHandlers.fireEvent(new HideMenuEvent());
        }
    }
}

