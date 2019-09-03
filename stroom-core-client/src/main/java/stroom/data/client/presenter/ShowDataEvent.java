package stroom.data.client.presenter;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import stroom.pipeline.shared.SourceLocation;

public class ShowDataEvent extends GwtEvent<ShowDataEvent.Handler> {
    private static Type<ShowDataEvent.Handler> TYPE;

    private final SourceLocation sourceLocation;

    private ShowDataEvent(final SourceLocation sourceLocation) {
        this.sourceLocation = sourceLocation;
    }

    public static void fire(final HasHandlers source, final SourceLocation sourceLocation) {
        source.fireEvent(new ShowDataEvent(sourceLocation));
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

    public SourceLocation getSourceLocation() {
        return sourceLocation;
    }

    public interface Handler extends EventHandler {
        void onShow(ShowDataEvent event);
    }
}