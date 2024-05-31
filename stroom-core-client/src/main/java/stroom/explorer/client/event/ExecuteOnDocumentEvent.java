package stroom.explorer.client.event;

import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class ExecuteOnDocumentEvent extends GwtEvent<ExecuteOnDocumentEvent.Handler> {

    private static Type<ExecuteOnDocumentEvent.Handler> TYPE;

    private final Action action;

    private ExecuteOnDocumentEvent(final Action action) {
        this.action = action;
    }

    public static void fire(final HasHandlers handlers,
                            final Action action) {
        handlers.fireEvent(new ExecuteOnDocumentEvent(action));
    }

    public static Type<ExecuteOnDocumentEvent.Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<ExecuteOnDocumentEvent.Handler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final ExecuteOnDocumentEvent.Handler handler) {
        handler.onCreate(this);
    }

    public Action getAction() {
        return action;
    }


    // --------------------------------------------------------------------------------


    public interface Handler extends EventHandler {

        void onCreate(ExecuteOnDocumentEvent event);
    }
}
