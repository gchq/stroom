package stroom.annotation.client;

import stroom.docref.DocRef;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class AnnotationChangeEvent extends GwtEvent<AnnotationChangeEvent.Handler> {

    private static Type<AnnotationChangeEvent.Handler> TYPE;

    private final DocRef annotationRef;

    private AnnotationChangeEvent(final DocRef annotationRef) {
        this.annotationRef = annotationRef;
    }

    public static void fire(final HasHandlers source, final DocRef annotationRef) {
        source.fireEvent(new AnnotationChangeEvent(annotationRef));
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
        handler.onChange(this);
    }

    public DocRef getAnnotationRef() {
        return annotationRef;
    }

    public interface Handler extends EventHandler {

        void onChange(AnnotationChangeEvent event);
    }
}
