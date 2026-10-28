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

package stroom.security.client.component;

import stroom.security.shared.FindUserContext;
import stroom.util.shared.UserRef;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.Listeners;
import stroom.widget.dropdowntree.client.DropDownBox;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.Objects;
import java.util.function.Consumer;

/// Picks a user or group with a [DropDownBox]: the box shows the one picked ("None" for none), and
/// asks for the picker popup ([UserRefPopupController]), which this owns and shows.
///
/// ```java
/// // A template declares the box; its screen's controller attaches one of these to it
/// ownerSelection = make(userRefSelectionProvider).attach(view.getOwnerBox());
/// ```
public class UserRefSelectionController extends Controller {

    /// Shown when no user or group is picked.
    public static final String NONE_DISPLAY_VALUE = "None";

    private final UserRefPopupController userRefPopup;
    private final Listeners<UserRef> selectionListeners = new Listeners<>();
    private DropDownBox box;
    private boolean enabled = true;
    private boolean readOnly;

    /// Made by GIN; [#attach(DropDownBox)] gives it its box.
    ///
    /// @param userRefPopup The picker popup, which this owns.
    @Inject
    public UserRefSelectionController(final UserRefPopupController userRefPopup) {
        this.userRefPopup = own(userRefPopup);
        userRefPopup.setSelectionChangeConsumer(this::showSelection);
    }

    /// Attaches this to the box whose requests to open the popup it answers.
    ///
    /// @param box The box, e.g. one a template declares.
    /// @return This, for its owner to keep.
    public UserRefSelectionController attach(final DropDownBox box) {
        this.box = Objects.requireNonNull(box, "box");
        box.setOnOpen(this::showPopup);
        box.setEnabled(enabled);
        box.setReadOnly(readOnly);
        showSelection(getSelected());
        return this;
    }

    /// @return The box this is attached to, or null before [#attach(DropDownBox)].
    public DropDownBox getBox() {
        return box;
    }

    /// Puts the focus on the box.
    public void focus() {
        if (box != null) {
            box.focus();
        }
    }

    /// @return The user or group picked, or null for none.
    public UserRef getSelected() {
        return userRefPopup.getSelected();
    }

    /// Shows a user or group as picked; listeners are not told.
    ///
    /// @param userRef The user or group, or null for none.
    public void setSelected(final UserRef userRef) {
        userRefPopup.setSelected(userRef);
        showSelection(userRef);
    }

    /// Sets which users and groups the popup offers.
    ///
    /// @param context What the user or group is picked for, e.g. an owner.
    public void setContext(final FindUserContext context) {
        userRefPopup.setContext(context);
    }

    /// Listens for the user picking another user or group, until the registration is removed.
    ///
    /// @param listener Told what is picked.
    /// @return The registration, for the listener's owner to own.
    public HandlerRegistration addSelectionListener(final Consumer<UserRef> listener) {
        return selectionListeners.add(listener);
    }

    /// Makes the selection read only: it can be read and stays in the tab order, but the
    /// popup doesn't open. It looks like the normal field with its value greyed.
    ///
    /// @param readOnly Whether the selection is read only.
    public void setReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        if (box != null) {
            box.setReadOnly(readOnly);
        }
    }

    /// Sets whether a user or group can be picked; the box is disabled when one can't.
    ///
    /// @param enabled Whether one can.
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
        if (box != null) {
            box.setEnabled(enabled);
        }
    }

    private void showPopup() {
        if (enabled && !readOnly) {
            final UserRef initialSelection = userRefPopup.getSelected();
            userRefPopup.show(userRef -> {
                final UserRef currentSelection = userRefPopup.getSelected();
                if (!Objects.equals(initialSelection, currentSelection)) {
                    selectionListeners.fire(userRef);
                }
            });
        }
    }

    private void showSelection(final UserRef selection) {
        if (box != null) {
            box.setText(selection == null
                    ? NONE_DISPLAY_VALUE
                    : selection.toDisplayString());
        }
    }
}
