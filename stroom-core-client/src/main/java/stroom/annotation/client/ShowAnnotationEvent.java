package stroom.annotation.client;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import stroom.annotation.shared.Annotation;

public class ShowAnnotationEvent extends GwtEvent<ShowAnnotationEvent.Handler> {
    private static Type<ShowAnnotationEvent.Handler> TYPE;

    private final Annotation annotation;
    private final Long streamId;
    private final Long eventId;

    private ShowAnnotationEvent(final Annotation annotation, final Long streamId, final Long eventId) {
        this.annotation = annotation;
        this.streamId = streamId;
        this.eventId = eventId;
    }

    public static void fire(final HasHandlers source, final Annotation annotation, final Long streamId, final Long eventId) {
        source.fireEvent(new ShowAnnotationEvent(annotation, streamId, eventId));
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

    public Annotation getAnnotation() {
        return annotation;
    }

    public Long getStreamId() {
        return streamId;
    }

    public Long getEventId() {
        return eventId;
    }

    public interface Handler extends EventHandler {
        void onShow(ShowAnnotationEvent event);
    }
}