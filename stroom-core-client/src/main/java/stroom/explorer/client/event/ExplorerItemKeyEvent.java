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

package stroom.explorer.client.event;

import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

import java.util.function.Consumer;

/// A key for an explorer item's action (e.g. F2, [Action#EXPLORER_RENAME]) was pressed on the
/// explorer's tree, to act on the items chosen in it, as the item's menu would.
public class ExplorerItemKeyEvent extends GwtEvent<ExplorerItemKeyEvent.Handler> {

    private static Type<Handler> TYPE;
    private final Action action;
    private final Consumer<String> announcer;

    private ExplorerItemKeyEvent(final Action action, final Consumer<String> announcer) {
        this.action = action;
        this.announcer = announcer;
    }

    /// Fires the event.
    ///
    /// @param handlers  Where to fire it.
    /// @param action    The action whose key was pressed.
    /// @param announcer Tells screen reader users why the action couldn't be done.
    public static void fire(final HasHandlers handlers,
                            final Action action,
                            final Consumer<String> announcer) {
        handlers.fireEvent(new ExplorerItemKeyEvent(action, announcer));
    }

    /// @return The event's type.
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
        handler.onKey(this);
    }

    /// @return The action whose key was pressed.
    public Action getAction() {
        return action;
    }

    /// Tells screen reader users why the action couldn't be done, e.g. "Feeds can't be renamed".
    ///
    /// @param message The reason.
    public void announce(final String message) {
        announcer.accept(message);
    }


    // --------------------------------------------------------------------------------


    /// Handles [ExplorerItemKeyEvent]s.
    public interface Handler extends EventHandler {

        /// @param event The key's event.
        void onKey(ExplorerItemKeyEvent event);
    }
}
