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

package stroom.dashboard.client.component;

import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.function.Supplier;

/// A listener that a popup needs only while it is shown, e.g. the dashboard's Current Selection,
/// refreshed as the selection changes. Showing the popup again while it is shown hides it (the
/// popup manager's toggle), so the listener is added once, when the popup is first shown, and
/// removed when it is hidden.
final class ListenerWhileShown {

    private HandlerRegistration registration;

    /// Starts listening, unless it already is.
    ///
    /// @param listen Adds the listener.
    /// @return Whether it started, i.e. the popup wasn't shown.
    boolean start(final Supplier<HandlerRegistration> listen) {
        if (registration != null) {
            return false;
        }
        registration = listen.get();
        return true;
    }

    /// Stops listening, if it is.
    void stop() {
        if (registration != null) {
            registration.removeHandler();
            registration = null;
        }
    }
}
