/*
 * Copyright 2016 Crown Copyright
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

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// A dialog's content with OK and Cancel below it, in either kind of dialog. Its markup is
/// `OkCancelContent.ui.xml`.
public class OkCancelContent extends Composite implements DialogButtons {

    private static final Binder BINDER = GWT.create(Binder.class);

    /// Whether the presenter allows OK, as set by [#setEnabled(DialogAction, boolean)]. Kept apart
    /// from the button's own state, which [#setEnabled(boolean)] toggles around every hide request.
    private boolean okAllowed = true;
    private Consumer<HideRequest> onHideRequest;

    @UiField
    Button ok;
    @UiField
    Button cancel;
    @UiField
    SimplePanel content;

    /// Makes the bar, with its OK and Cancel buttons, and an empty space for the dialog's
    /// content. Give it its content with [#setContent(Widget)] and its handler with
    /// [#setOnHideRequest(Consumer)].
    public OkCancelContent() {
        initWidget(BINDER.createAndBindUi(this));
        ok.setOnClick(() -> onDialogAction(DialogAction.OK));
        cancel.setOnClick(() -> onDialogAction(DialogAction.CANCEL));
    }

    /// @param onHideRequest Told when the user asks to close the dialog with OK or Cancel; it hides
    ///                      the dialog, or runs the request's cancel handler to keep it open.
    public void setOnHideRequest(final Consumer<HideRequest> onHideRequest) {
        this.onHideRequest = onHideRequest;
    }

    /// @param widget What the dialog shows above its buttons.
    public void setContent(final Widget widget) {
        content.setWidget(widget);
        okAllowed = true;
        ok.setEnabled(true);
        cancel.setEnabled(true);
    }

    @Override
    public void focus() {
        cancel.setFocus(true);
    }

    @Override
    public void onDialogAction(final DialogAction action) {
        final Button button = action == DialogAction.OK
                ? ok
                : cancel;
        if (!DialogButtonAction.canRun(button)) {
            return;
        }
        final Runnable restoreFocus = DialogButtonAction.keepFocus(button);
        setEnabled(false);
        button.setLoading(true);
        if (onHideRequest != null) {
            onHideRequest.accept(new HideRequest(action, () -> {
                setEnabled(true);
                ok.setLoading(false);
                cancel.setLoading(false);
                restoreFocus.run();
            }));
        }
    }

    @Override
    public void setEnabled(final boolean enabled) {
        ok.setEnabled(enabled && okAllowed);
        cancel.setEnabled(enabled);
    }

    @Override
    public void setEnabled(final DialogAction action, final boolean enabled) {
        if (DialogAction.OK.equals(action)) {
            okAllowed = enabled;
            ok.setEnabled(enabled);
        } else if (DialogAction.CANCEL.equals(action)) {
            cancel.setEnabled(enabled);
        }
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, OkCancelContent> {

    }
}
