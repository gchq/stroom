/*
 * Copyright 2026 Crown Copyright
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


package stroom.gwt.workbench.client.app.screen;

import com.google.web.bindery.event.shared.Event;
import com.google.web.bindery.event.shared.SimpleEventBus;

/// The event bus of a screen story's harness: a [SimpleEventBus] that can be switched off when
/// the story renders again, so that whatever the old rendering's presenters still do (e.g. a timer
/// that shows a popup, or a reply that fires an alert) has no effect on the page.
public class StoryEventBus extends SimpleEventBus {

    private boolean disposed;

    /// Creates an event bus that fires events to handlers in the order they were added.
    public StoryEventBus() {
        super();
    }

    /// Fires the event, unless the bus has been disposed.
    ///
    /// @param event The event.
    @Override
    public void fireEvent(final Event<?> event) {
        if (!disposed) {
            super.fireEvent(event);
        }
    }

    /// Fires the event from a source, unless the bus has been disposed.
    ///
    /// @param event  The event.
    /// @param source The source.
    @Override
    public void fireEventFromSource(final Event<?> event, final Object source) {
        if (!disposed) {
            super.fireEventFromSource(event, source);
        }
    }

    /// Stops the bus firing events. Handlers can still be added, but are never called.
    public void dispose() {
        disposed = true;
    }

    /// @return True if [#dispose()] has been called.
    public boolean isDisposed() {
        return disposed;
    }
}
