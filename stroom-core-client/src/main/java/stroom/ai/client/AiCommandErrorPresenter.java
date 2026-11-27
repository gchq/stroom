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

package stroom.ai.client;

import stroom.ai.client.AiCommandErrorPresenter.AiCommandErrorView;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

/// Says why a command typed in the chat can't be run, as an alert does, with a link to the command's documentation
/// if it is one there is.
public class AiCommandErrorPresenter extends MyPresenterWidget<AiCommandErrorView>
        implements AiCommandErrorUiHandlers {

    private Runnable onOpenReference;

    /// @param eventBus The event bus.
    /// @param view     The dialog's view.
    @Inject
    public AiCommandErrorPresenter(final EventBus eventBus, final AiCommandErrorView view) {
        super(eventBus, view);
        view.setUiHandlers(this);
    }

    /// Shows the dialog.
    ///
    /// @param caption         The dialog's caption, e.g. "Unknown Command".
    /// @param message         Why the command can't be run.
    /// @param linkText        The link's text, e.g. "Show how to use /plan", or null for no link.
    /// @param onOpenReference Shows the documentation, when the link is clicked (after the dialog is closed).
    public void show(final String caption,
                     final String message,
                     final String linkText,
                     final Runnable onOpenReference) {
        this.onOpenReference = onOpenReference;
        getView().setMessage(message, linkText);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(PopupSize.resizable(500, 200))
                .caption(caption)
                .modal(true)
                .fire();
    }

    @Override
    public void onOpenReference() {
        HidePopupEvent.builder(this).fire();
        if (onOpenReference != null) {
            onOpenReference.run();
        }
    }


    // --------------------------------------------------------------------------------


    /// The dialog's view: an alert's warning icon and message, then the link.
    public interface AiCommandErrorView extends View, HasUiHandlers<AiCommandErrorUiHandlers> {

        /// @param message  Why the command can't be run.
        /// @param linkText The link's text, or null for no link.
        void setMessage(String message, String linkText);
    }
}
