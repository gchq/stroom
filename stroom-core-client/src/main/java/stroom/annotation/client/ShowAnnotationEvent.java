package stroom.annotation.client;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.EventId;

import java.util.List;

public class ShowAnnotationEvent extends GwtEvent<ShowAnnotationEvent.Handler> {
    private static Type<ShowAnnotationEvent.Handler> TYPE;

    private final Annotation annotation;
    private final List<EventId> linkedEvents;

    private ShowAnnotationEvent(final Annotation annotation, final List<EventId> linkedEvents) {
        this.annotation = annotation;
        this.linkedEvents = linkedEvents;
    }

    public static void fire(final HasHandlers source, final Annotation annotation, final List<EventId> linkedEvents) {
        source.fireEvent(new ShowAnnotationEvent(annotation, linkedEvents));
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

    public List<EventId> getLinkedEvents() {
        return linkedEvents;
    }

    public interface Handler extends EventHandler {
        void onShow(ShowAnnotationEvent event);
    }
}