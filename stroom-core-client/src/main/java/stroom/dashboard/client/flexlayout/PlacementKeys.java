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

package stroom.dashboard.client.flexlayout;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Event.NativePreviewEvent;

/// The keys that finish or cancel placing new dashboard components (a duplicate, or an added
/// component), which otherwise only a mouse click could do: Escape cancels and Enter places. They
/// are listened for only while a placement is under way, before anything else on the page sees
/// them.
final class PlacementKeys {

    /// What the glass over the page says while a placement is under way.
    static final String HELP = "Click where the new component should go, press Enter to put it beside "
                               + "the original, or Escape to cancel";

    private HandlerRegistration registration;

    /// Starts listening, replacing any listening already under way.
    ///
    /// @param onEscape Called when Escape is pressed.
    /// @param onEnter  Called when Enter is pressed.
    void start(final Runnable onEscape, final Runnable onEnter) {
        stop();
        registration = Event.addNativePreviewHandler(event -> onKey(event, onEscape, onEnter));
    }

    /// Stops listening, if listening.
    void stop() {
        if (registration != null) {
            registration.removeHandler();
            registration = null;
        }
    }

    /// @return True while listening.
    boolean isListening() {
        return registration != null;
    }

    private static void onKey(final NativePreviewEvent event, final Runnable onEscape, final Runnable onEnter) {
        if (event.getTypeInt() != Event.ONKEYDOWN) {
            return;
        }
        final int keyCode = event.getNativeEvent().getKeyCode();
        final Runnable action = keyCode == KeyCodes.KEY_ESCAPE
                ? onEscape
                : keyCode == KeyCodes.KEY_ENTER
                        ? onEnter
                        : null;
        if (action != null) {
            // The key is the placement's, not whatever has focus behind the glass
            event.cancel();
            event.getNativeEvent().preventDefault();
            event.getNativeEvent().stopPropagation();
            action.run();
        }
    }
}
