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

package stroom.entity.client.presenter;

import stroom.document.client.event.MoveDocumentEvent;
import stroom.document.client.event.ShowMoveDocumentDialogEvent;
import stroom.entity.client.presenter.MoveDocumentPresenter.MoveDocumentView;
import stroom.explorer.client.component.EntityTree;
import stroom.explorer.client.component.EntityTreeController;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.PermissionInheritance;
import stroom.security.shared.DocumentPermission;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;

public class MoveDocumentPresenter
        extends MyPresenterWidget<MoveDocumentView>
        implements ShowMoveDocumentDialogEvent.Handler {

    private final EntityTreeController folders;
    private List<ExplorerNode> explorerNodeList;

    @Inject
    public MoveDocumentPresenter(final EventBus eventBus, final MoveDocumentView view,
                                 final Provider<EntityTreeController> entityTreeControllerProvider) {
        super(eventBus, view);
        this.folders = make(entityTreeControllerProvider).attach(view.getFolders());

        folders.setIncludedTypes(DocumentTypes.FOLDER_TYPES);
        folders.setRequiredPermissions(DocumentPermission.USE, DocumentPermission.VIEW);
    }

    @Override
    public void onMove(final ShowMoveDocumentDialogEvent event) {
        this.explorerNodeList = event.getExplorerNodeList();

        folders.setSelectedItem(null);

        ExplorerNode firstChild = event.getExplorerNodeList().get(0);
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

        show();
    }

    private void show() {
        String caption = "Move Multiple Items";
        if (explorerNodeList.size() == 1) {
            caption = "Move " + explorerNodeList.get(0).getDisplayValue();
        }
        getView().setPermissionInheritance(PermissionInheritance.DESTINATION);

        final PopupSize popupSize = PopupSize.resizable(400, 600, 380, 480);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .modal()
                .popupSize(popupSize)
                .caption(caption)
                .onShow(e -> folders.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final ExplorerNode folder = folders.getSelectedItem();
                        MoveDocumentEvent.fire(
                                MoveDocumentPresenter.this,
                                e,
                                explorerNodeList,
                                folder,
                                getView().getPermissionInheritance());
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }


    // --------------------------------------------------------------------------------


    // --------------------------------------------------------------------------------


    public interface MoveDocumentView extends View {

        /// @return The folder picker, for the presenter to attach its controller to.
        EntityTree getFolders();

        PermissionInheritance getPermissionInheritance();

        void setPermissionInheritance(PermissionInheritance permissionInheritance);
    }


    // --------------------------------------------------------------------------------

    /// Hears the app-wide events the screen handles, and makes the screen when it is first needed.

}
