package stroom.security.client.event;

import stroom.security.client.event.OpenUsersAndGroupsScreenEvent.OpenUsersAndGroupsScreenHandler;
import stroom.util.shared.UserRef;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

import java.util.Objects;

public class OpenUsersAndGroupsScreenEvent extends GwtEvent<OpenUsersAndGroupsScreenHandler> {

    private static Type<OpenUsersAndGroupsScreenHandler> TYPE;
    private final UserRef userRef;

    private OpenUsersAndGroupsScreenEvent(final UserRef userRef) {
        this.userRef = Objects.requireNonNull(userRef);
    }

    /**
     * Open the user on the Users and Groups screen
     */
    public static void fire(final HasHandlers handlers, final UserRef userRef) {
        handlers.fireEvent(new OpenUsersAndGroupsScreenEvent(
                Objects.requireNonNull(userRef, "userRef required")));
    }

    public static Type<OpenUsersAndGroupsScreenHandler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<OpenUsersAndGroupsScreenHandler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final OpenUsersAndGroupsScreenHandler handler) {
        handler.onOpen(this);
    }

    public UserRef getUserRef() {
        return userRef;
    }

    @Override
    public String toString() {
        return "OpenUserOrGroupEvent{" +
               "userRef='" + userRef + '\'' +
               '}';
    }

    // --------------------------------------------------------------------------------


    public interface OpenUsersAndGroupsScreenHandler extends EventHandler {

        void onOpen(OpenUsersAndGroupsScreenEvent event);
    }
}
