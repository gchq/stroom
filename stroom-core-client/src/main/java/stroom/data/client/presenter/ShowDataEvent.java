package stroom.data.client.presenter;

import stroom.pipeline.shared.SourceLocation;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class ShowDataEvent extends GwtEvent<ShowDataEvent.Handler> {
    private static Type<ShowDataEvent.Handler> TYPE;

    private final SourceLocation sourceLocation;
    private final DataViewType dataViewType;
    private final DisplayMode displayMode;

    private ShowDataEvent(final SourceLocation sourceLocation,
                          final DataViewType dataViewType,
                          final DisplayMode displayMode) {
        this.sourceLocation = sourceLocation;
        this.dataViewType = dataViewType;
        this.displayMode = displayMode;
    }


    public static void fire(final HasHandlers source,
                            final SourceLocation sourceLocation,
                            final DataViewType dataViewType,
                            final DisplayMode displayMode) {
        source.fireEvent(new ShowDataEvent(sourceLocation, dataViewType, displayMode));
    }

    public static void fire(final HasHandlers source,
                            final SourceLocation sourceLocation) {
        source.fireEvent(new ShowDataEvent(
                sourceLocation,
                DataViewType.PREVIEW,
                DisplayMode.DIALOG));
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

    public DataViewType getDataViewType() {
        return dataViewType;
    }

    public DisplayMode getDisplayMode() {
        return displayMode;
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    public interface Handler extends EventHandler {
        void onShow(ShowDataEvent event);
    }
}
