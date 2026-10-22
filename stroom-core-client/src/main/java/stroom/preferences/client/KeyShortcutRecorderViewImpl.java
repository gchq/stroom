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

package stroom.preferences.client;

import stroom.preferences.client.KeyShortcutRecorderPresenter.KeyShortcutRecorderView;
import stroom.util.shared.NullSafe;
import stroom.widget.form.client.FormGroup;
import stroom.widget.presenter.client.ViewWithUiHandlers;
import stroom.widget.util.client.KeyBinding;

import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

public final class KeyShortcutRecorderViewImpl
        extends ViewWithUiHandlers<KeyShortcutRecorderUiHandlers>
        implements KeyShortcutRecorderView {

    private final Widget widget;

    @UiField
    FormGroup keysFormGroup;
    @UiField
    TextBox keys;
    @UiField
    Label message;

    @Inject
    public KeyShortcutRecorderViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        // A read-only text box, so that a screen reader reads the keys as its value. The keys
        // are recorded from key presses, not typed.
        keys.setReadOnly(true);
        keys.getElement().setAttribute("placeholder", "Press keys");
        // Polite, as it changes with each key press while the keys keep the focus
        message.getElement().setAttribute("aria-live", "polite");
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        keys.setFocus(true);
    }

    @Override
    public void setActionName(final String actionName) {
        keysFormGroup.setLabel("Keys for '" + actionName + "'");
    }

    @Override
    public void setShortcut(final String shortcut) {
        keys.setValue(NullSafe.string(shortcut));
    }

    @Override
    public void setError(final String error) {
        keysFormGroup.setFeedback(error);
    }

    @Override
    public void setMessage(final String message) {
        this.message.setText(NullSafe.string(message));
    }

    @UiHandler("keys")
    void onKeysKeyDown(final KeyDownEvent event) {
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
            if (getUiHandlers() != null) {
                getUiHandlers().onKeyStroke(KeyBinding.getKeyStroke(e));
            }
        }
    }

    @UiHandler("keys")
    void onKeysFocus(final FocusEvent event) {
        KeyBinding.setRecording(true);
    }

    @UiHandler("keys")
    void onKeysBlur(final BlurEvent event) {
        KeyBinding.setRecording(false);
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, KeyShortcutRecorderViewImpl> {

    }
}
