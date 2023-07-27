package stroom.main.client.event;

import stroom.docref.DocRef;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class ShowMainEvent extends GwtEvent<ShowMainEvent.Handler> {

    private static Type<Handler> TYPE;

    private final DocRef initialDocRef;

    private ShowMainEvent(final DocRef initialDocRef) {
        this.initialDocRef = initialDocRef;
    }

    public static void fire(final HasHandlers handlers, final DocRef initialDocRef) {
        handlers.fireEvent(new ShowMainEvent(initialDocRef));
    }

    public static Type<Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<Handler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onShowMain(this);
    }

    public DocRef getInitialDocRef() {
        return initialDocRef;
    }

    public interface Handler extends EventHandler {

        void onShowMain(ShowMainEvent event);
    }
}
