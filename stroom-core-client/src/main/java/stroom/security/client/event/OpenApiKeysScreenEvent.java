package stroom.security.client.event;

import stroom.security.client.event.OpenApiKeysScreenEvent.OpenApiKeysScreenHandler;
import stroom.util.shared.UserRef;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

import java.util.Objects;

public class OpenApiKeysScreenEvent extends GwtEvent<OpenApiKeysScreenHandler> {

    private static Type<OpenApiKeysScreenHandler> TYPE;
    private final UserRef userRef;

    private OpenApiKeysScreenEvent(final UserRef userRef) {
        this.userRef = Objects.requireNonNull(userRef);
    }

    /**
     * Open the user on the API Keys screen
     */
    public static void fire(final HasHandlers handlers, final UserRef userRef) {
        handlers.fireEvent(new OpenApiKeysScreenEvent(Objects.requireNonNull(userRef, "userRef required")));
    }

    public static Type<OpenApiKeysScreenHandler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<OpenApiKeysScreenHandler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final OpenApiKeysScreenHandler handler) {
        handler.onOpen(this);
    }

    public UserRef getUserRef() {
        return userRef;
    }

    @Override
    public String toString() {
        return "OpenApiKeysEvent{" +
               "userRef='" + userRef + '\'' +
               '}';
    }

    // --------------------------------------------------------------------------------


    public interface OpenApiKeysScreenHandler extends EventHandler {

        void onOpen(OpenApiKeysScreenEvent event);
    }
}
