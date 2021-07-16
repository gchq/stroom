package stroom.widget.tab.client.event;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.event.shared.HasHandlers;

public class KeyboardSelectionEvent<T> extends GwtEvent<KeyboardSelectionEvent.Handler<T>> {

    private static Type<Handler<?>> TYPE;

    public static <T> void fire(HasHandlers source, T selectedItem) {
        if (TYPE != null) {
            KeyboardSelectionEvent<T> event = new KeyboardSelectionEvent<T>(selectedItem);
            source.fireEvent(event);
        }
    }

    public static Type<Handler<?>> getType() {
        if (TYPE == null) {
            TYPE = new Type<Handler<?>>();
        }
        return TYPE;
    }

    private final T selectedItem;

    protected KeyboardSelectionEvent(T selectedItem) {
        this.selectedItem = selectedItem;
    }

    @SuppressWarnings("unchecked")
    @Override
    public final Type<Handler<T>> getAssociatedType() {
        return (Type) TYPE;
    }

    public T getSelectedItem() {
        return selectedItem;
    }

    @Override
    protected void dispatch(Handler<T> handler) {
        handler.onSelection(this);
    }

    public interface Handler<T> extends EventHandler {

        void onSelection(KeyboardSelectionEvent<T> event);
    }

    public interface HasKeyboardSelectionHandlers<T> extends HasHandlers {

        HandlerRegistration addKeyboardSelectionHandler(KeyboardSelectionEvent.Handler<T> handler);
    }
}

