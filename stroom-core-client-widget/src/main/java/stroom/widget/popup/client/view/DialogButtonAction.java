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

package stroom.widget.popup.client.view;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.FocusWidget;

/// The rules a dialog's buttons share when they run a dialog action.
final class DialogButtonAction {

    private DialogButtonAction() {
    }

    /// Whether the action of a button may run. Keyboard shortcuts (Ctrl+Enter, Escape) run a
    /// dialog's action without its button, so they must check that the button is enabled.
    ///
    /// @param button The button whose action would run.
    /// @return Whether the button is enabled.
    static boolean canRun(final FocusWidget button) {
        return button.isEnabled();
    }

    /// Notes whether a button has focus before the dialog disables it while its action runs, as
    /// disabling it moves focus to the page's body.
    ///
    /// @param button The button about to be disabled.
    /// @return Something to run once the button is enabled again: it puts focus back on the
    /// button if the button had it and focus hasn't moved anywhere else since.
    static Runnable keepFocus(final FocusWidget button) {
        final Document document = Document.get();
        final boolean hadFocus = button.getElement() == CurrentFocus.getActiveElement(document);
        return () -> {
            final Element active = CurrentFocus.getActiveElement(document);
            // Browsers differ in whether a disabled button keeps focus or loses it to the body
            final boolean focusStayed = active == null
                    || active == document.getBody()
                    || active == button.getElement();
            if (hadFocus && focusStayed && button.isAttached() && button.isEnabled()) {
                button.setFocus(true);
            }
        };
    }
}
