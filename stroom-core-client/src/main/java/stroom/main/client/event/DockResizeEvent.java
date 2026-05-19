/*
 * Copyright 2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.main.client.event;

import stroom.widget.util.client.Size;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

/**
 * Event fired when the user drags the dock splitter to resize a docked panel.
 * Allows presenters to persist the new dock size to user preferences.
 */
public class DockResizeEvent extends GwtEvent<DockResizeEvent.Handler> {

    private static Type<Handler> TYPE;

    private final Size newSize;

    private DockResizeEvent(final Size newSize) {
        this.newSize = newSize;
    }

    public static void fire(final HasHandlers handlers,
                            final Size newSize) {
        handlers.fireEvent(new DockResizeEvent(newSize));
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
        handler.onDockResize(this);
    }

    public Size getNewSize() {
        return newSize;
    }

    public interface Handler extends EventHandler {

        void onDockResize(DockResizeEvent event);
    }
}
