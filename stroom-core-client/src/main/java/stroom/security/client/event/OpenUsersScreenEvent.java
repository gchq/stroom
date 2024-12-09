package stroom.security.client.event;

import stroom.security.client.event.OpenUsersScreenEvent.OpenUsersScreenHandler;
import stroom.util.shared.UserRef;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

import java.util.Objects;

public class OpenUsersScreenEvent extends GwtEvent<OpenUsersScreenHandler> {

    private static Type<OpenUsersScreenHandler> TYPE;
    private final UserRef userRef;

    private OpenUsersScreenEvent(final UserRef userRef) {
        this.userRef = Objects.requireNonNull(userRef);
    }

    /**
     * Open the user on the Users and Groups screen
     */
    public static void fire(final HasHandlers handlers, final UserRef userRef) {
        handlers.fireEvent(new OpenUsersScreenEvent(
                Objects.requireNonNull(userRef, "userRef required")));
    }

    public static Type<OpenUsersScreenHandler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<OpenUsersScreenHandler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final OpenUsersScreenHandler handler) {
        handler.onOpen(this);
    }

    public UserRef getUserRef() {
        return userRef;
    }

    @Override
    public String toString() {
        return "OpenUsersScreenEvent{" +
               "userRef='" + userRef + '\'' +
               '}';
    }

    // --------------------------------------------------------------------------------


    public interface OpenUsersScreenHandler extends EventHandler {

        void onOpen(OpenUsersScreenEvent event);
    }
}
