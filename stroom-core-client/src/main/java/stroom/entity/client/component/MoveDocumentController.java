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

import stroom.document.client.event.MoveDocumentEvent;
import stroom.document.client.event.ShowMoveDocumentDialogEvent;
import stroom.explorer.client.component.EntityTreeController;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.security.shared.DocumentPermission;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;

/// Moves documents into a folder ([MoveDocument]), in a dialog made for one use. OK fires
/// [MoveDocumentEvent], whose handler moves them and closes the dialog.
public class MoveDocumentController extends Controller {

    private final MoveDocument view;
    private final EntityTreeController folders;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                     The application's event bus.
    /// @param view                         The content.
    /// @param entityTreeControllerProvider Makes the folder picker's controller.
    @Inject
    public MoveDocumentController(final EventBus eventBus,
                                  final MoveDocument view,
                                  final Provider<EntityTreeController> entityTreeControllerProvider) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        folders = make(entityTreeControllerProvider).attach(view.getFolders());
        folders.setIncludedTypes(DocumentTypes.FOLDER_TYPES);
        folders.setRequiredPermissions(DocumentPermission.USE, DocumentPermission.VIEW);
    }

    /// Shows the dialog for the documents the event names, owned by `opener` until it is closed,
    /// with the first document's folder chosen.
    ///
    /// @param opener What shows it.
    /// @param event  The documents to move.
    public void show(final HasControllerNode opener, final ShowMoveDocumentDialogEvent event) {
        final List<ExplorerNode> explorerNodeList = event.getExplorerNodeList();
        ExplorerNode firstChild = explorerNodeList.get(0);
        // Make sure we reference the main node rather than the favourites' node.
        if (firstChild != null) {
            firstChild = firstChild
                    .copy()
                    .rootNodeUuid(ExplorerConstants.SYSTEM_DOC_REF.getUuid())
                    .build();
        }
        folders.setSelectedItem(firstChild);
        folders.getTreeController().reset();
        folders.getTreeController().setEnsureVisible(firstChild);
        folders.getTreeController().refresh();
        // We want to select the parent folder, so we have a default dest to move into.
        folders.setSelectParentIfNotFound(true);

        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .modal()
                .popupSize(PopupSize.resizable(400, 600, 380, 480))
                .caption(explorerNodeList.size() == 1
                        ? "Move " + explorerNodeList.get(0).getDisplayValue()
                        : "Move Multiple Items")
                .onShow(e -> folders.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        MoveDocumentEvent.fire(
                                this,
                                e,
                                explorerNodeList,
                                folders.getSelectedItem(),
                                view.getPermissionInheritance());
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }
}
