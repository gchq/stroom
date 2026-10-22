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

package stroom.widget.presenter.client;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.function.Supplier;

/// Asks for a screen to be shown in a slot of the screen that holds it, e.g. the document tabs in
/// the main screen's content slot. Each slot is its own event type, handled by its screen's proxy
/// ([#showIn(EventBus, GwtEvent.Type, Supplier)]), which shows that screen first.
public final class RevealContentEvent extends GwtEvent<RevealContentEvent.Handler> {

    private final Type<Handler> slot;
    private final MyPresenterWidget<?> content;

    private RevealContentEvent(final Type<Handler> slot, final MyPresenterWidget<?> content) {
        this.slot = slot;
        this.content = content;
    }

    /// Asks for a screen to be shown in a slot.
    ///
    /// @param source  What asks, e.g. the screen itself.
    /// @param slot    The slot, a constant of the screen that holds it.
    /// @param content The screen.
    public static void fire(final HasHandlers source,
                            final Type<Handler> slot,
                            final MyPresenterWidget<?> content) {
        source.fireEvent(new RevealContentEvent(slot, content));
    }

    /// Shows what is asked for in one of a screen's slots: shows the screen, then puts it in the slot,
    /// once the event being handled is done (as GWTP did).
    ///
    /// @param eventBus The event bus.
    /// @param slot     The slot.
    /// @param screen   Gets the screen that holds it, e.g. its GIN provider's `get`.
    /// @return The registration.
    public static HandlerRegistration showIn(final EventBus eventBus,
                                             final Type<Handler> slot,
                                             final Supplier<? extends MyPresenter<?>> screen) {
        return eventBus.addHandler(slot, event -> Scheduler.get().scheduleDeferred(() -> {
            final MyPresenter<?> presenter = screen.get();
            presenter.forceReveal();
            presenter.setInSlot(slot, event.getContent());
        }));
    }

    /// @return The screen to show.
    public MyPresenterWidget<?> getContent() {
        return content;
    }

    @Override
    public Type<Handler> getAssociatedType() {
        return slot;
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onRevealContent(this);
    }

    // --------------------------------------------------------------------------------


    /// Shows a screen in a slot.
    public interface Handler extends EventHandler {

        /// @param event What to show.
        void onRevealContent(RevealContentEvent event);
    }
}
