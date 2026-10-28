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

package stroom.entity.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.document.client.event.CreateDocumentEvent;
import stroom.document.client.event.ShowCreateDocumentDialogEvent;
import stroom.explorer.client.component.EntityTreeController;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerNode;
import stroom.security.shared.DocumentPermission;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.DialogAction;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// Creates a document in a folder ([CreateDocument]), in a dialog made for one use. OK fires
/// [CreateDocumentEvent], whose handler makes it and closes the dialog.
public class CreateDocumentController extends Controller {

    private final CreateDocument view;
    private final EntityTreeController folders;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                     The application's event bus.
    /// @param view                         The content.
    /// @param entityTreeControllerProvider Makes the folder picker's controller.
    @Inject
    public CreateDocumentController(final EventBus eventBus,
                                    final CreateDocument view,
                                    final Provider<EntityTreeController> entityTreeControllerProvider) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        folders = make(entityTreeControllerProvider).attach(view.getFolders());
        folders.setIncludedTypes(DocumentTypes.FOLDER_TYPES);
        folders.setRequiredPermissions(DocumentPermission.USE, DocumentPermission.VIEW);
        // Enter in the name is OK
        view.setOnSubmit(() -> ControllerDialog.pressAction(this, DialogAction.OK));
    }

    /// Shows the dialog the event asks for, owned by `opener` until it is closed, with the
    /// selected item's folder chosen.
    ///
    /// @param opener What shows it.
    /// @param event  The document type, caption, selected item and initial name.
    public void show(final HasControllerNode opener, final ShowCreateDocumentDialogEvent event) {
        folders.setSelectedItem(event.getSelected());
        folders.getTreeController().reset();
        folders.getTreeController().setEnsureVisible(event.getSelected());
        // We want to select the parent folder, so we have a default dest to move into.
        folders.setSelectParentIfNotFound(true);
        folders.getTreeController().refresh();

        view.setName(event.getInitialDocName() != null
                ? event.getInitialDocName()
                : "");
        view.setNameValid();

        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .modal()
                .popupSize(PopupSize.resizable(400, 600, 380, 480))
                .caption(event.getDialogCaption())
                .onShow(e -> view.focus())
                .onHideRequest(e -> onHideRequest(e, event))
                .fire();
    }

    private void onHideRequest(final HidePopupRequestEvent e, final ShowCreateDocumentDialogEvent event) {
        if (e.isOk()) {
            final ExplorerNode destinationFolder = folders.getSelectedItem();
            if (!event.isAllowNullFolder() && destinationFolder == null) {
                AlertEvent.fireWarn(this, "No parent folder has been selected", e::reset);
            } else {
                String docName = view.getName();
                if (docName != null) {
                    docName = docName.trim();
                }

                if (docName == null || docName.isEmpty()) {
                    // Said under the field, which is focused, so a screen reader reads it
                    view.setNameInvalid("You must provide a name for the new " + event.getDocType().toLowerCase());
                    e.reset();
                } else {
                    view.setNameValid();
                    CreateDocumentEvent.fire(
                            this,
                            e,
                            event.getDocType(),
                            docName,
                            destinationFolder,
                            view.getPermissionInheritance(),
                            event.getNewDocConsumer());
                }
            }
        } else {
            e.hide();
        }
    }
}
