package stroom.annotation.client;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import stroom.annotation.shared.Annotation;

public class ShowAnnotationEvent extends GwtEvent<ShowAnnotationEvent.Handler> {
    private static Type<ShowAnnotationEvent.Handler> TYPE;

    private final Annotation annotation;

    private ShowAnnotationEvent(final Annotation annotation) {
        this.annotation = annotation;
    }

    public static void fire(final HasHandlers source, final Annotation annotation) {
        source.fireEvent(new ShowAnnotationEvent(annotation));
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

    public interface Handler extends EventHandler {
        void onShow(ShowAnnotationEvent event);
    }
}