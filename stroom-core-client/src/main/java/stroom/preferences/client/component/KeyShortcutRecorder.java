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

package stroom.preferences.client.component;

import stroom.util.shared.NullSafe;
import stroom.widget.component.client.AriaPanel;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.FormGroup;
import stroom.widget.form.client.TextBox;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyStroke;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// The content of the dialog in which the user presses the keys of a keyboard shortcut: the keys
/// recorded, why they can't be used, and what the user can do next.
public class KeyShortcutRecorder extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    FormGroup keysFormGroup;
    @UiField
    TextBox keys;
    @UiField
    AriaPanel message;

    private Consumer<KeyStroke> onKeyStroke = keyStroke -> {
    };

    /// Makes empty content.
    public KeyShortcutRecorder() {
        initWidget(BINDER.createAndBindUi(this));
        keys.addKeyDownHandler(event -> {
            final NativeEvent e = event.getNativeEvent();
            final int keyCode = e.getKeyCode();
            final boolean hasModifier = e.getShiftKey() || e.getCtrlKey() || e.getAltKey() || e.getMetaKey();
            final boolean isDialogKey = !hasModifier
                                        && (keyCode == KeyCodes.KEY_ENTER
                                            || keyCode == KeyCodes.KEY_MAC_ENTER
                                            || keyCode == KeyCodes.KEY_ESCAPE);
            // Tab moves the focus as usual, so the user is never trapped here, and Enter and
            // Escape are left to the dialog, to accept or cancel
            if (keyCode != KeyCodes.KEY_TAB && !isDialogKey) {
                e.preventDefault();
                e.stopPropagation();
                onKeyStroke.accept(KeyBinding.getKeyStroke(e));
            }
        });
        // Key presses are recorded, not acted on, while the keys have the focus
        keys.addFocusHandler(event -> KeyBinding.setRecording(true));
        keys.addBlurHandler(event -> KeyBinding.setRecording(false));
    }

    /// Puts the focus in the keys.
    public void focus() {
        keys.setFocus(true);
    }

    /// @param actionName The name of the action the shortcut is for.
    public void setActionName(final String actionName) {
        keysFormGroup.setLabel("Keys for '" + actionName + "'");
    }

    /// @param shortcut The text form of the shortcut recorded so far, or null if none.
    public void setShortcut(final String shortcut) {
        keys.setValue(NullSafe.string(shortcut));
    }

    /// @param error Why the shortcut can't be used, or null if it can.
    public void setError(final String error) {
        keysFormGroup.setFeedback(error);
    }

    /// @param message What the user can do next, and anything they should know about the
    ///                shortcut, or null for nothing.
    public void setMessage(final String message) {
        this.message.getElement().setInnerText(NullSafe.string(message));
    }

    /// @param onKeyStroke Told each key the user presses to record, with the modifiers held.
    public void setOnKeyStroke(final Consumer<KeyStroke> onKeyStroke) {
        this.onKeyStroke = onKeyStroke;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, KeyShortcutRecorder> {

    }
}
