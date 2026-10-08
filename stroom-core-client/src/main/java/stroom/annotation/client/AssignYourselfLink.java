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

package stroom.annotation.client;

import stroom.widget.util.client.DisabledState;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.user.client.ui.Label;

/// Makes the 'Assign Yourself' link of an annotation's Assigned To setting a button that the
/// keyboard can reach and press, and that is disabled, saying why, when there is nothing to do,
/// rather than hidden.
final class AssignYourselfLink {

    private static final String TITLE = "Assign this annotation to yourself";
    private static final String ALREADY_ASSIGNED = "Already assigned to you";
    private static final String READ_ONLY = "You don't have permission to change this";

    private AssignYourselfLink() {
        // Static utility
    }

    /// Makes the label a button, pressed by Enter or Space as well as a click.
    ///
    /// @param link   The 'Assign Yourself' label.
    /// @param action Assigns the annotation; only run while the link is enabled.
    static void init(final Label link, final Runnable action) {
        final Element element = link.getElement();
        element.setAttribute("role", "button");
        element.setTabIndex(0);
        element.setTitle(TITLE);
        link.addDomHandler(event -> {
            final int keyCode = event.getNativeKeyCode();
            if (keyCode == KeyCodes.KEY_ENTER || keyCode == KeyCodes.KEY_SPACE) {
                event.preventDefault();
                event.stopPropagation();
                runIfEnabled(link, action);
            }
        }, KeyDownEvent.getType());
    }

    /// Runs the action unless the link is disabled.
    ///
    /// @param link   The 'Assign Yourself' label.
    /// @param action Assigns the annotation.
    static void runIfEnabled(final Label link, final Runnable action) {
        if (!DisabledState.isDisabled(link.getElement())) {
            action.run();
        }
    }

    /// Enables the link, or disables it with the reason as its tooltip.
    ///
    /// @param link            The 'Assign Yourself' label.
    /// @param alreadyAssigned Whether the annotation is already assigned to the current user.
    /// @param readOnly        Whether the annotation is read only.
    static void update(final Label link, final boolean alreadyAssigned, final boolean readOnly) {
        final boolean disabled = alreadyAssigned || readOnly;
        DisabledState.set(link.getElement(), disabled);
        if (readOnly) {
            link.setTitle(READ_ONLY);
        } else if (alreadyAssigned) {
            link.setTitle(ALREADY_ASSIGNED);
        } else {
            link.setTitle(TITLE);
        }
    }
}
