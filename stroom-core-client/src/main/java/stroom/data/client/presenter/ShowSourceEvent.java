package stroom.data.client.presenter;

import stroom.pipeline.shared.SourceLocation;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class ShowSourceEvent extends GwtEvent<ShowSourceEvent.Handler> {
    private static Type<ShowSourceEvent.Handler> TYPE;

    private final SourceLocation sourceLocation;
    private final DisplayMode displayMode;

    private ShowSourceEvent(final SourceLocation sourceLocation,
                            final DisplayMode displayMode) {
        this.sourceLocation = sourceLocation;
        this.displayMode = displayMode;
    }

    public static void fire(final HasHandlers source,
                            final SourceLocation sourceLocation,
                            final DisplayMode displayMode) {
        source.fireEvent(new ShowSourceEvent(sourceLocation, displayMode));
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

    public DisplayMode getMode() {
        return displayMode;
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    public interface Handler extends EventHandler {
        void onShow(ShowSourceEvent event);
    }

}