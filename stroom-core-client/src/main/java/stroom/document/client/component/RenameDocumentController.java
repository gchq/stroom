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

package stroom.document.client.component;

import stroom.document.client.event.RenameDocumentEvent;
import stroom.explorer.shared.ExplorerNode;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.DialogAction;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// Renames a document ([NameEntry]) in a dialog made for one use.
public class RenameDocumentController extends Controller {

    private final NameEntry view;

    /// Made by GIN, with its name field.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The name field.
    @Inject
    public RenameDocumentController(final EventBus eventBus, final NameEntry view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        view.setOnEnter(() -> ControllerDialog.pressAction(this, DialogAction.OK));
    }

    /// Asks for a document's new name in a dialog, owned by `opener` until it is closed, and
    /// renames it on OK.
    ///
    /// @param opener   What shows it.
    /// @param document The document.
    /// @param onHidden Run once the dialog is closed, renamed or not.
    public void rename(final HasControllerNode opener,
                       final ExplorerNode document,
                       final Runnable onHidden) {
        view.setName(document.getDisplayValue());
        view.setNameValid();
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .modal()
                .popupSize(PopupSize.resizableX())
                .caption("Rename " + document.getDisplayValue())
                .onShow(e -> view.focus())
                .onHideRequest(e -> onHideRequest(e, document))
                .onHide(e -> onHidden.run())
                .fire();
    }

    private void onHideRequest(final HidePopupRequestEvent e, final ExplorerNode document) {
        if (e.isOk()) {
            final String name = view.getName() == null
                    ? ""
                    : view.getName().trim();
            if (name.isEmpty()) {
                // Said under the field, which is focused, so a screen reader reads it
                view.setNameInvalid("You must provide a new name for " + document.getDisplayValue());
                e.reset();
            } else {
                view.setNameValid();
                RenameDocumentEvent.fire(this, e, document, name);
            }
        } else {
            e.hide();
        }
    }
}
