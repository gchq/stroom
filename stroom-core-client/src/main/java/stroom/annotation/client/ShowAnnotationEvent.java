package stroom.annotation.client;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class ShowAnnotationEvent extends GwtEvent<ShowAnnotationEvent.Handler> {
    private static Type<ShowAnnotationEvent.Handler> TYPE;

    private final long metaId;
    private final long eventId;

    private ShowAnnotationEvent(final long metaId, final long eventId) {
        this.metaId = metaId;
        this.eventId = eventId;
    }

    public static void fire(final HasHandlers source, final long metaId, final long eventId) {
        source.fireEvent(new ShowAnnotationEvent(metaId, eventId));
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
        handler.onShow(this);
    }

    long getMetaId() {
        return metaId;
    }

    long getEventId() {
        return eventId;
    }

    public interface Handler extends EventHandler {
        void onShow(ShowAnnotationEvent event);
    }
}