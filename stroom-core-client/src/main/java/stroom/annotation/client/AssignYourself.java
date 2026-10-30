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

import stroom.widget.button.client.LinkButton;

/// The state of an annotation's 'Assign Yourself' button (a [LinkButton], styled
/// `annotation-setting__assign-yourself`): disabled, saying why in its tooltip, when there is
/// nothing to do, rather than hidden.
public final class AssignYourself {

    private static final String TITLE = "Assign this annotation to yourself";
    private static final String ALREADY_ASSIGNED = "Already assigned to you";
    private static final String READ_ONLY = "You don't have permission to change this";

    private AssignYourself() {
        // Static utility
    }

    /// Enables the button, or disables it with the reason as its tooltip.
    ///
    /// @param button          The button.
    /// @param alreadyAssigned Whether the annotation is already assigned to the current user.
    /// @param readOnly        Whether the annotation is read only.
    public static void setState(final LinkButton button, final boolean alreadyAssigned, final boolean readOnly) {
        button.setAriaDisabled(alreadyAssigned || readOnly);
        if (readOnly) {
            button.setTitle(READ_ONLY);
        } else if (alreadyAssigned) {
            button.setTitle(ALREADY_ASSIGNED);
        } else {
            button.setTitle(TITLE);
        }
    }
}
