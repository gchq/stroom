/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.entity.client.presenter.MoveDocumentPresenter.MoveDocumentProxy;
import stroom.entity.client.presenter.MoveDocumentPresenter.MoveDocumentView;
import stroom.explorer.client.presenter.EntityTreePresenter;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.PermissionInheritance;
import stroom.security.shared.DocumentPermission;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;

import java.util.List;

public class MoveDocumentPresenter
        extends MyPresenter<MoveDocumentView, MoveDocumentProxy>
        implements ShowMoveDocumentDialogEvent.Handler {

    private final EntityTreePresenter entityTreePresenter;
    private List<ExplorerNode> explorerNodeList;

    @Inject
    public MoveDocumentPresenter(final EventBus eventBus, final MoveDocumentView view, final MoveDocumentProxy proxy,
                                 final EntityTreePresenter entityTreePresenter) {
        super(eventBus, view, proxy);
        this.entityTreePresenter = entityTreePresenter;
        view.setFolderView(entityTreePresenter.getView());

        entityTreePresenter.setIncludedTypes(DocumentTypes.FOLDER_TYPES);
        entityTreePresenter.setRequiredPermissions(DocumentPermission.USE, DocumentPermission.VIEW);
    }

    @ProxyEvent
    @Override
    public void onMove(final ShowMoveDocumentDialogEvent event) {
        this.explorerNodeList = event.getExplorerNodeList();

        entityTreePresenter.setSelectedItem(null);

        ExplorerNode firstChild = event.getExplorerNodeList().get(0);
        // Make sure we reference the main node rather than the favourites' node.
        if (firstChild != null) {
            firstChild = firstChild
                    .copy()
                    .rootNodeUuid(ExplorerConstants.SYSTEM_DOC_REF.getUuid())
                    .build();
        }
        entityTreePresenter.setSelectedItem(firstChild);
        entityTreePresenter.getModel().reset();
        entityTreePresenter.getModel().setEnsureVisible(firstChild);
        entityTreePresenter.getModel().refresh();

        // We want to select the parent folder, so we have a default dest to move into.
        entityTreePresenter.setSelectParentIfNotFound(true);

        forceReveal();
    }

    @Override
    protected void revealInParent() {
        String caption = "Move Multiple Items";
        if (explorerNodeList.size() == 1) {
            caption = "Move " + explorerNodeList.get(0).getDisplayValue();
        }
        getView().setPermissionInheritance(PermissionInheritance.DESTINATION);

        final PopupSize popupSize = PopupSize.resizable(400, 600, 380, 480);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption(caption)
                .onShow(e -> entityTreePresenter.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final ExplorerNode folder = entityTreePresenter.getSelectedItem();
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


    @Override
    public void setTaskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {
        super.setTaskMonitorFactory(taskMonitorFactory);
        entityTreePresenter.setTaskMonitorFactory(taskMonitorFactory);
    }


    // --------------------------------------------------------------------------------


    public interface MoveDocumentView extends View {

        void setFolderView(View view);

        PermissionInheritance getPermissionInheritance();

        void setPermissionInheritance(PermissionInheritance permissionInheritance);
    }


    // --------------------------------------------------------------------------------


    @ProxyCodeSplit
    public interface MoveDocumentProxy extends Proxy<MoveDocumentPresenter> {

    }
}
