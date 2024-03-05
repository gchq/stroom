package stroom.widget.valuespinner.client;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class IncreaseEvent extends GwtEvent<IncreaseEvent.Handler> {

    private static Type<Handler> TYPE;

    private IncreaseEvent() {
    }

    public static void fire(final HasHandlers handlers) {
        handlers.fireEvent(new IncreaseEvent());
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
        handler.onIncrease(this);
    }

    public interface Handler extends EventHandler {

        void onIncrease(IncreaseEvent event);
    }
}
