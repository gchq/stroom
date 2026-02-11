package stroom.main.client.event;

import stroom.ai.client.AskStroomAiPresenter.DockBehaviour;
import stroom.widget.util.client.Size;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.gwtplatform.mvp.client.Presenter;

public class DockEvent extends GwtEvent<DockEvent.Handler> {

    private static Type<Handler> TYPE;

    private final Presenter<?, ?> presenter;
    private final DockBehaviour dockBehaviour;
    private final Size size;

    private DockEvent(final Presenter<?, ?> presenter,
                      final DockBehaviour dockBehaviour,
                      final Size size) {
        this.presenter = presenter;
        this.dockBehaviour = dockBehaviour;
        this.size = size;
    }

    public static void fire(final HasHandlers handlers,
                            final Presenter<?, ?> presenter,
                            final DockBehaviour dockBehaviour,
                            final Size size) {
        handlers.fireEvent(new DockEvent(presenter, dockBehaviour, size));
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
        handler.onDock(this);
    }

    public Presenter<?, ?> getPresenter() {
        return presenter;
    }

    public DockBehaviour getDockBehaviour() {
        return dockBehaviour;
    }

    public Size getSize() {
        return size;
    }

    public interface Handler extends EventHandler {

        void onDock(DockEvent event);
    }
}
