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

package stroom.widget.popup.client;

import stroom.widget.button.client.Button;
import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.function.Consumer;

/// A dialog's content with its buttons below it, in either kind of dialog: Close; OK and Cancel;
/// Accept and Reject (e.g. the splash screen's terms; Accept is OK and Reject is Cancel); or New at
/// the start and OK and Cancel at the end. [#setButtons(PopupType)] picks which. Its markup is
/// `DialogFrame.ui.xml`.
public class DialogFrame extends Component implements DialogButtons {

    private static final Binder BINDER = GWT.create(Binder.class);
    // Where the buttons are in their row (dialog.css): New at the start and the group at the end,
    // or only the group, at the end
    private static final String BUTTONS_START = "dialog__buttons--start";
    private static final String BUTTONS_END = "dialog__buttons--end";

    /// Whether the controller allows OK (or Accept), as set by [#setEnabled(DialogAction, boolean)].
    /// Kept apart from the button's own state, which [#setEnabled(boolean)] toggles around every
    /// hide request.
    private boolean okAllowed = true;
    /// As [#okAllowed], for New.
    private boolean createAllowed = true;
    private Consumer<HideRequest> onHideRequest;

    // The buttons shown, set by setButtons: New, or null; OK or Accept, or null; and the one that
    // closes the dialog without OK (Cancel, Reject or Close)
    private Button shownCreate;
    private Button shownOk;
    private Button shownDismiss;

    @UiField
    SimplePanel content;
    @UiField
    FlowPanel buttons;
    @UiField
    FlowPanel group;
    @UiField
    Button create;
    @UiField
    Button ok;
    @UiField
    Button accept;
    @UiField
    Button cancel;
    @UiField
    Button reject;
    @UiField
    Button close;

    /// Makes the frame, with OK and Cancel until [#setButtons(PopupType)] says otherwise, and an
    /// empty space for the dialog's content. Give it its content with [#setContent(Widget)] and its
    /// handler with [#setOnHideRequest(Consumer)].
    public DialogFrame() {
        initWidget(BINDER.createAndBindUi(this));
        create.setOnClick(() -> onDialogAction(DialogAction.CREATE));
        ok.setOnClick(() -> onDialogAction(DialogAction.OK));
        accept.setOnClick(() -> onDialogAction(DialogAction.OK));
        cancel.setOnClick(() -> onDialogAction(DialogAction.CANCEL));
        reject.setOnClick(() -> onDialogAction(DialogAction.CANCEL));
        close.setOnClick(() -> onDialogAction(DialogAction.CLOSE));
        setButtons(PopupType.OK_CANCEL_DIALOG);
    }

    /// Shows the buttons for a kind of dialog; the others are taken out of the page, so the dialog's
    /// markup holds only its own buttons, as each kind's frame did before they were one.
    ///
    /// @param popupType [PopupType#CLOSE_DIALOG] for Close, [PopupType#OK_CANCEL_DIALOG] for OK and
    ///                  Cancel, [PopupType#ACCEPT_REJECT_DIALOG] for Accept and Reject, or
    ///                  [PopupType#CREATE_OK_CANCEL_DIALOG] for New, OK and Cancel.
    /// @throws IllegalArgumentException If the kind of dialog has no buttons.
    public void setButtons(final PopupType popupType) {
        switch (popupType) {
            case CLOSE_DIALOG -> show(null, null, close);
            case OK_CANCEL_DIALOG -> show(null, ok, cancel);
            case ACCEPT_REJECT_DIALOG -> show(null, accept, reject);
            case CREATE_OK_CANCEL_DIALOG -> show(create, ok, cancel);
            default -> throw new IllegalArgumentException("A " + popupType + " has no buttons");
        }
    }

    /// @param onHideRequest Told when the user asks to close the dialog with one of its buttons; it
    ///                      hides the dialog, or runs the request's cancel handler to keep it open.
    public void setOnHideRequest(final Consumer<HideRequest> onHideRequest) {
        this.onHideRequest = onHideRequest;
    }

    /// @param widget What the dialog shows above its buttons.
    public void setContent(final Widget widget) {
        content.setWidget(widget);
        okAllowed = true;
        createAllowed = true;
        if (shownOk != null) {
            shownOk.setEnabled(true);
        }
        shownDismiss.setEnabled(true);
    }

    @Override
    public void focus() {
        shownDismiss.setFocus(true);
    }

    @Override
    public void onDialogAction(final DialogAction action) {
        final Button button = getButton(action);
        if (!DialogButtonAction.canRun(button)) {
            return;
        }
        final Runnable restoreFocus = DialogButtonAction.keepFocus(button);
        setEnabled(false);
        // New's spinner shows on Cancel, as it always has (ISSUES.md)
        final Button busy = button == shownCreate
                ? shownDismiss
                : button;
        busy.setLoading(true);
        if (onHideRequest != null) {
            onHideRequest.accept(new HideRequest(action, () -> {
                setEnabled(true);
                if (shownOk != null) {
                    shownOk.setLoading(false);
                }
                shownDismiss.setLoading(false);
                restoreFocus.run();
            }));
        }
    }

    @Override
    public void setEnabled(final boolean enabled) {
        if (shownCreate != null) {
            shownCreate.setEnabled(enabled && createAllowed);
        }
        if (shownOk != null) {
            shownOk.setEnabled(enabled && okAllowed);
        }
        shownDismiss.setEnabled(enabled);
    }

    @Override
    public void setEnabled(final DialogAction action, final boolean enabled) {
        if (DialogAction.CREATE.equals(action)) {
            if (shownCreate != null) {
                createAllowed = enabled;
                shownCreate.setEnabled(enabled);
            }
        } else if (DialogAction.OK.equals(action)) {
            if (shownOk != null) {
                okAllowed = enabled;
                shownOk.setEnabled(enabled);
            }
        } else if (getDismissAction().equals(action)) {
            shownDismiss.setEnabled(enabled);
        }
    }

    // The button that runs an action: New for CREATE and OK (or Accept) for OK, if shown, and the
    // one that closes the dialog for anything else (Escape's CLOSE, AUTO_CLOSE)
    private Button getButton(final DialogAction action) {
        if (action == DialogAction.CREATE && shownCreate != null) {
            return shownCreate;
        } else if (action == DialogAction.OK && shownOk != null) {
            return shownOk;
        }
        return shownDismiss;
    }

    // What the button that closes the dialog without OK does: CLOSE for Close, CANCEL for Cancel
    // and Reject
    private DialogAction getDismissAction() {
        return shownDismiss == close
                ? DialogAction.CLOSE
                : DialogAction.CANCEL;
    }

    private void show(final Button shownCreate, final Button shownOk, final Button shownDismiss) {
        this.shownCreate = shownCreate;
        this.shownOk = shownOk;
        this.shownDismiss = shownDismiss;
        // Only the shown buttons are in the page, in the template's order: New, then the group
        buttons.clear();
        if (shownCreate != null) {
            buttons.add(shownCreate);
        }
        buttons.add(group);
        group.clear();
        for (final Button button : List.of(ok, accept, cancel, reject, close)) {
            if (button == shownOk || button == shownDismiss) {
                group.add(button);
            }
        }
        buttons.setStyleName(BUTTONS_START, shownCreate != null);
        buttons.setStyleName(BUTTONS_END, shownCreate == null);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DialogFrame> {

    }
}
