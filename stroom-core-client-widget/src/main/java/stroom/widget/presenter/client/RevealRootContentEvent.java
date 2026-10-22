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

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

/// Asks for a screen to be the whole page, e.g. the sign in page or the application's frame. The
/// application's [RootPresenter] shows it; nothing does in a workbench story, which has its own page.
public final class RevealRootContentEvent extends GwtEvent<RevealRootContentEvent.Handler> {

    private static final Type<Handler> TYPE = new Type<>();

    private final MyPresenterWidget<?> content;

    private RevealRootContentEvent(final MyPresenterWidget<?> content) {
        this.content = content;
    }

    /// Asks for a screen to be the whole page.
    ///
    /// @param source  What asks, e.g. the screen itself.
    /// @param content The screen.
    public static void fire(final HasHandlers source, final MyPresenterWidget<?> content) {
        source.fireEvent(new RevealRootContentEvent(content));
    }

    /// @return The event's type.
    public static Type<Handler> getType() {
        return TYPE;
    }

    /// @return The screen to show.
    public MyPresenterWidget<?> getContent() {
        return content;
    }

    @Override
    public Type<Handler> getAssociatedType() {
        return TYPE;
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onRevealRootContent(this);
    }

    // --------------------------------------------------------------------------------


    /// Shows a screen as the whole page.
    public interface Handler extends EventHandler {

        /// @param event What to show.
        void onRevealRootContent(RevealRootContentEvent event);
    }
}
