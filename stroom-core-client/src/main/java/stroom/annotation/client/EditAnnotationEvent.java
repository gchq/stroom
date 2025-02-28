package stroom.annotation.client;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class EditAnnotationEvent extends GwtEvent<EditAnnotationEvent.Handler> {

    private static Type<EditAnnotationEvent.Handler> TYPE;

    private final long annotationId;

    private EditAnnotationEvent(final long annotationId) {
        this.annotationId = annotationId;
    }

    public static void fire(final HasHandlers source, final long annotationId) {
        source.fireEvent(new EditAnnotationEvent(annotationId));
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
        handler.onEdit(this);
    }

    public long getAnnotationId() {
        return annotationId;
    }

    public interface Handler extends EventHandler {

        void onEdit(EditAnnotationEvent event);
    }
}
