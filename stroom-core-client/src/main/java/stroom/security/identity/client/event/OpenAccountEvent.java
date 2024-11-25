package stroom.security.identity.client.event;

import stroom.security.identity.client.event.OpenAccountEvent.OpenAccountHandler;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

import java.util.Objects;

public class OpenAccountEvent extends GwtEvent<OpenAccountHandler> {

    private static Type<OpenAccountHandler> TYPE;
    private final String userId;

    private OpenAccountEvent(final String userId) {
        this.userId = Objects.requireNonNull(userId);
    }

    /**
     * Open the named node on the nodes screen
     */
    public static void fire(final HasHandlers handlers, final String userId) {
        handlers.fireEvent(new OpenAccountEvent(userId));
    }

    public static Type<OpenAccountHandler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<OpenAccountHandler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final OpenAccountHandler handler) {
        handler.onOpen(this);
    }

    public String getUserId() {
        return userId;
    }

    @Override
    public String toString() {
        return "OpenAccountEvent{" +
               "userId='" + userId + '\'' +
               '}';
    }

    // --------------------------------------------------------------------------------


    public interface OpenAccountHandler extends EventHandler {

        void onOpen(OpenAccountEvent event);
    }
}
