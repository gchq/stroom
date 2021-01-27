package com.google.gwt.event.dom.client;

public class InputEvent extends DomEvent<InputHandler> {

    /**
     * Event type for input events. Represents the meta-data associated with this
     * event.
     */
    private static final Type<InputHandler> TYPE = new Type<InputHandler>("input",
            new InputEvent());

    /**
     * Gets the event type associated with load events.
     *
     * @return the handler type
     */
    public static Type<InputHandler> getType() {
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
    public final Type<InputHandler> getAssociatedType() {
        return TYPE;
    }

    @Override
    protected void dispatch(InputHandler handler) {
        handler.onInput(this);
    }

}
