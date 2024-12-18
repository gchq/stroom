package stroom.security.client.event;

import stroom.security.client.event.OpenUserEvent.OpenUserHandler;
import stroom.util.shared.UserRef;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

import java.util.Objects;

public class OpenUserEvent extends GwtEvent<OpenUserHandler> {

    private static Type<OpenUserHandler> TYPE;
    private final UserRef userRef;

    private OpenUserEvent(final UserRef userRef) {
        this.userRef = Objects.requireNonNull(userRef);
    }

    /**
     * Open the screen for a single user
     */
    public static void fire(final HasHandlers handlers, final UserRef userRef) {
        handlers.fireEvent(new OpenUserEvent(
                Objects.requireNonNull(userRef, "userRef required")));
    }

    public static Type<OpenUserHandler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<OpenUserHandler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final OpenUserHandler handler) {
        handler.onOpen(this);
    }

    public UserRef getUserRef() {
        return userRef;
    }

    @Override
    public String toString() {
        return "OpenUserEvent{" +
               "userRef='" + userRef + '\'' +
               '}';
    }

    // --------------------------------------------------------------------------------


    public interface OpenUserHandler extends EventHandler {

        void onOpen(OpenUserEvent event);
    }
}
