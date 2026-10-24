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

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.user.client.ui.Widget;

/// The 'Assign Yourself' link of an annotation's Assigned To setting: a button that the keyboard
/// can reach and press (Enter or Space), and that is disabled, saying why, when there is nothing to
/// do, rather than hidden. Its markup is `AssignYourselfLink.ui.xml`.
public class AssignYourselfLink extends Widget {

    private static final Binder BINDER = GWT.create(Binder.class);

    private static final String TITLE = "Assign this annotation to yourself";
    private static final String ALREADY_ASSIGNED = "Already assigned to you";
    private static final String READ_ONLY = "You don't have permission to change this";

    private Runnable onAssign;

    public AssignYourselfLink() {
        setElement(BINDER.createAndBindUi(this));
        addDomHandler(event -> {
            // The setting around it opens its chooser on a click: this click is the link's own
            event.stopPropagation();
            assign();
        }, ClickEvent.getType());
        addDomHandler(event -> {
            final int keyCode = event.getNativeKeyCode();
            if (keyCode == KeyCodes.KEY_ENTER || keyCode == KeyCodes.KEY_SPACE) {
                event.preventDefault();
                event.stopPropagation();
                assign();
            }
        }, KeyDownEvent.getType());
    }

    /// @param onAssign Assigns the annotation; only run while the link is enabled.
    public void setOnAssign(final Runnable onAssign) {
        this.onAssign = onAssign;
    }

    /// Enables the link, or disables it with the reason as its tooltip.
    ///
    /// @param alreadyAssigned Whether the annotation is already assigned to the current user.
    /// @param readOnly        Whether the annotation is read only.
    public void setState(final boolean alreadyAssigned, final boolean readOnly) {
        DisabledState.set(getElement(), alreadyAssigned || readOnly);
        if (readOnly) {
            setTitle(READ_ONLY);
        } else if (alreadyAssigned) {
            setTitle(ALREADY_ASSIGNED);
        } else {
            setTitle(TITLE);
        }
    }

    private void assign() {
        if (onAssign != null && !DisabledState.isDisabled(getElement())) {
            onAssign.run();
        }
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<DivElement, AssignYourselfLink> {

    }
}
