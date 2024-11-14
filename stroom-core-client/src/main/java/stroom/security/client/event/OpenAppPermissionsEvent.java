package stroom.security.client.event;

import stroom.security.client.event.OpenAppPermissionsEvent.OpenAppPermissionsHandler;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

import java.util.Objects;

public class OpenAppPermissionsEvent extends GwtEvent<OpenAppPermissionsHandler> {

    private static Type<OpenAppPermissionsHandler> TYPE;
    private final String subjectId;

    private OpenAppPermissionsEvent(final String subjectId) {
        this.subjectId = Objects.requireNonNull(subjectId);
    }

    /**
     * Open the named node on the nodes screen
     */
    public static void fire(final HasHandlers handlers, final String subjectId) {
        handlers.fireEvent(new OpenAppPermissionsEvent(subjectId));
    }

    public static Type<OpenAppPermissionsHandler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<OpenAppPermissionsHandler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final OpenAppPermissionsHandler handler) {
        handler.onOpen(this);
    }

    public String getSubjectId() {
        return subjectId;
    }

    @Override
    public String toString() {
        return "OpenAppPermissionsEvent{" +
               "subjectId='" + subjectId + '\'' +
               '}';
    }

    // --------------------------------------------------------------------------------


    public interface OpenAppPermissionsHandler extends EventHandler {

        void onOpen(OpenAppPermissionsEvent event);
    }
}
