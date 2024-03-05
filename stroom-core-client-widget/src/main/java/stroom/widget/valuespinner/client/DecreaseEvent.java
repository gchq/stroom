package stroom.widget.valuespinner.client;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class DecreaseEvent extends GwtEvent<DecreaseEvent.Handler> {

    private static Type<Handler> TYPE;

    private DecreaseEvent() {
    }

    public static void fire(final HasHandlers handlers) {
        handlers.fireEvent(new DecreaseEvent());
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
        handler.onDecrease(this);
    }

    public interface Handler extends EventHandler {

        void onDecrease(DecreaseEvent event);
    }
}
