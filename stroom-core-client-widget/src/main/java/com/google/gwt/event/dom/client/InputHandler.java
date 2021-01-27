package com.google.gwt.event.dom.client;

import com.google.gwt.event.shared.EventHandler;

public interface InputHandler extends EventHandler {

    /**
     * Called when InputEvent is fired.
     *
     * @param event the {@link InputEvent} that was fired
     */
    void onInput(InputEvent event);
}