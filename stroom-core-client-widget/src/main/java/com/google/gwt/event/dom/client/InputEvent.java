package com.google.gwt.event.dom.client;

import com.google.gwt.event.dom.client.InputEvent.Handler;
import com.google.gwt.event.shared.EventHandler;

public class InputEvent extends DomEvent<Handler> {

    /**
     * Event type for input events. Represents the meta-data associated with this
     * event.
     */
    private static final Type<Handler> TYPE = new Type<Handler>("input",
            new InputEvent());

    /**
     * Gets the event type associated with load events.
     *
     * @return the handler type
     */
    public static Type<Handler> getType() {
        return TYPE;
    }

    /**
     * Protected constructor, use
     * {@link DomEvent#fireNativeEvent(com.google.gwt.dom.client.NativeEvent, com.google.gwt.event.shared.HasHandlers)}
     * to fire load events.
     */
    protected InputEvent() {
    }

    @Override
    public final Type<Handler> getAssociatedType() {
        return TYPE;
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onInput(this);
    }

    public interface Handler extends EventHandler {

        /**
         * Called when InputEvent is fired.
         *
         * @param event the {@link InputEvent} that was fired
         */
        void onInput(InputEvent event);
    }
}
