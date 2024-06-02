package stroom.explorer.client.event;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class CreateNewDocumentEvent extends GwtEvent<CreateNewDocumentEvent.Handler> {

    private static Type<CreateNewDocumentEvent.Handler> TYPE;

    private final String documentType;

    private CreateNewDocumentEvent(final String documentType) {
        this.documentType = documentType;
    }

    public static void fire(final HasHandlers handlers,
                            final String documentType) {
        handlers.fireEvent(new CreateNewDocumentEvent(documentType));
    }

    public static Type<CreateNewDocumentEvent.Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<CreateNewDocumentEvent.Handler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final CreateNewDocumentEvent.Handler handler) {
        handler.onCreate(this);
    }

    public String getDocumentType() {
        return documentType;
    }

    // --------------------------------------------------------------------------------


    public interface Handler extends EventHandler {

        void onCreate(CreateNewDocumentEvent event);
    }
}
