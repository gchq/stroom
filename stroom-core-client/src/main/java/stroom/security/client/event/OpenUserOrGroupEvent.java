package stroom.security.client.event;

import stroom.security.client.event.OpenUserOrGroupEvent.OpenUserOrGroupHandler;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

import java.util.Objects;

public class OpenUserOrGroupEvent extends GwtEvent<OpenUserOrGroupHandler> {

    private static Type<OpenUserOrGroupHandler> TYPE;
    private final String subjectId;

    private OpenUserOrGroupEvent(final String subjectId) {
        this.subjectId = Objects.requireNonNull(subjectId);
    }

    /**
     * Open the named node on the nodes screen
     */
    public static void fire(final HasHandlers handlers, final String subjectId) {
        handlers.fireEvent(new OpenUserOrGroupEvent(subjectId));
    }

    public static Type<OpenUserOrGroupHandler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<OpenUserOrGroupHandler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final OpenUserOrGroupHandler handler) {
        handler.onOpen(this);
    }

    public String getSubjectId() {
        return subjectId;
    }

    @Override
    public String toString() {
        return "OpenUserOrGroupEvent{" +
               "subjectId='" + subjectId + '\'' +
               '}';
    }

    // --------------------------------------------------------------------------------


    public interface OpenUserOrGroupHandler extends EventHandler {

        void onOpen(OpenUserOrGroupEvent event);
    }
}
