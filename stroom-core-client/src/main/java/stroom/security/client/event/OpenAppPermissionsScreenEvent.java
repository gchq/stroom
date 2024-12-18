package stroom.security.client.event;

import stroom.security.client.event.OpenAppPermissionsScreenEvent.OpenAppPermissionsScreenHandler;
import stroom.util.shared.UserRef;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

import java.util.Objects;

public class OpenAppPermissionsScreenEvent extends GwtEvent<OpenAppPermissionsScreenHandler> {

    private static Type<OpenAppPermissionsScreenHandler> TYPE;
    private final UserRef userRef;

    private OpenAppPermissionsScreenEvent(final UserRef userRef) {
        this.userRef = Objects.requireNonNull(userRef);
    }

    /**
     * Open the user on the App Permissions screen
     */
    public static void fire(final HasHandlers handlers, final UserRef userRef) {
        handlers.fireEvent(new OpenAppPermissionsScreenEvent(userRef));
    }

    public static Type<OpenAppPermissionsScreenHandler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<OpenAppPermissionsScreenHandler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final OpenAppPermissionsScreenHandler handler) {
        handler.onOpen(this);
    }

    public UserRef getUserRef() {
        return userRef;
    }

    @Override
    public String toString() {
        return "OpenAppPermissionsEvent{" +
               "userRef='" + userRef + '\'' +
               '}';
    }

    // --------------------------------------------------------------------------------


    public interface OpenAppPermissionsScreenHandler extends EventHandler {

        void onOpen(OpenAppPermissionsScreenEvent event);
    }
}
