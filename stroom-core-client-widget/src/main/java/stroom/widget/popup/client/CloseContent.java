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

/// A dialog's content with Close below it, in either kind of dialog. Its markup is
/// `CloseContent.ui.xml`.
public class CloseContent extends Composite implements DialogButtons {

    private static final Binder BINDER = GWT.create(Binder.class);

    private Consumer<HideRequest> onHideRequest;

    @UiField
    Button close;
    @UiField
    SimplePanel content;

    /// Makes the bar, with its Close buttons, and an empty space for the dialog's
    /// content. Give it its content with [#setContent(Widget)] and its handler with
    /// [#setOnHideRequest(Consumer)].
    public CloseContent() {
        initWidget(BINDER.createAndBindUi(this));
        close.setOnClick(() -> onDialogAction(DialogAction.CLOSE));
    }

    /// @param onHideRequest Told when the user asks to close the dialog; it hides the dialog, or
    ///                      runs the request's cancel handler to keep it open.
    public void setOnHideRequest(final Consumer<HideRequest> onHideRequest) {
        this.onHideRequest = onHideRequest;
    }

    /// @param widget What the dialog shows above its button.
    public void setContent(final Widget widget) {
        content.setWidget(widget);
    }

    @Override
    public void focus() {
        close.setFocus(true);
    }

    @Override
    public void onDialogAction(final DialogAction action) {
        if (!DialogButtonAction.canRun(close)) {
            return;
        }
        final Runnable restoreFocus = DialogButtonAction.keepFocus(close);
        setEnabled(false);
        close.setLoading(true);
        if (onHideRequest != null) {
            onHideRequest.accept(new HideRequest(action, () -> {
                setEnabled(true);
                close.setLoading(false);
                restoreFocus.run();
            }));
        }
    }

    @Override
    public void setEnabled(final boolean enabled) {
        close.setEnabled(enabled);
    }

    @Override
    public void setEnabled(final DialogAction action, final boolean enabled) {
        if (DialogAction.CLOSE.equals(action)) {
            close.setEnabled(enabled);
        }
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, CloseContent> {

    }
}
